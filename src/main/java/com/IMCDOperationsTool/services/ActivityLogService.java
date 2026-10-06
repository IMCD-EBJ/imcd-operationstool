package com.IMCDOperationsTool.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.IMCDOperationsTool.security.AuthenticationModeService;

/**
 * Writes one {@code dbo.Activity_Log} row per completed action.
 * A failed insert is recorded in the application log and does not fail the action.
 */
@Service
public class ActivityLogService {

    public static final String LOGIN = "LOGIN";
    public static final String IMPORT_FILE = "IMPORT_FILE";
    public static final String EXPORT_DATA = "EXPORT_DATA";
    public static final String APPLY_FILTERS = "APPLY_FILTERS";

    private static final Logger LOG = LoggerFactory.getLogger(ActivityLogService.class);
    private static final long SESSION_WINDOW_MS = Duration.ofHours(12).toMillis();

    private final JdbcTemplate jdbcTemplate;
    private final AuthenticationModeService authenticationModeService;
    private final Map<String, Long> safenetSessions = new ConcurrentHashMap<>();

    public ActivityLogService(JdbcTemplate jdbcTemplate, AuthenticationModeService authenticationModeService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authenticationModeService = authenticationModeService;
    }

    /**
     * Records a SafeNet sign-in once per token. The local password form is ignored.
     *
     * @param sessionKey JWT, or another stable key for a container principal
     */
    public void logSafenetLogin(String sessionKey) {
        if (authenticationModeService.isLocalLoginEnabled() || !StringUtils.hasText(sessionKey)) {
            return;
        }
        long now = System.currentTimeMillis();
        String key = sha256(sessionKey);
        Long previous = safenetSessions.putIfAbsent(key, now);
        if (previous != null && now - previous < SESSION_WINDOW_MS) {
            return;
        }
        if (previous != null) {
            safenetSessions.put(key, now);
        }
        if (safenetSessions.size() > 2000) {
            safenetSessions.entrySet().removeIf(entry -> now - entry.getValue() > SESSION_WINDOW_MS);
        }
        log(LOGIN);
    }

    /**
     * @param taskName one of the activity names accepted by {@code dbo.Activity_Log}
     */
    public void log(String taskName) {
        log(taskName, null, null);
    }

    /**
     * @param localAdUser known AD user, when the request already carries it
     * @param userName    display name, when the request already carries it
     */
    public void log(String taskName, String localAdUser, String userName) {
        try {
            ResolvedUser user = resolve(localAdUser, userName);
            jdbcTemplate.update(
                    "{call dbo.Activity_Log_Insert(?,?,?)}",
                    user.localAdUser(),
                    user.userName(),
                    taskName);
        } catch (RuntimeException e) {
            LOG.warn("Activity {} was not stored: {}", taskName, e.getMessage());
        }
    }

    private ResolvedUser resolve(String localAdUser, String userName) {
        String principal = currentPrincipal();
        String lookupKey = StringUtils.hasText(localAdUser) ? localAdUser.trim() : principal;
        ResolvedUser fromDatabase = lookup(lookupKey);
        String adUser = firstText(localAdUser, fromDatabase == null ? null : fromDatabase.localAdUser(), principal);
        String name = firstText(userName, fromDatabase == null ? null : fromDatabase.userName());
        if (!StringUtils.hasText(adUser)) {
            adUser = "unknown";
        }
        return new ResolvedUser(clip(adUser, 200), clip(name, 200));
    }

    private ResolvedUser lookup(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT TOP (1) UOTLocalADUuser, UOTUserName
                    FROM dbo.UsersOperationsTool
                    WHERE UOTLocalADUuser = ?
                       OR UOTMail = ?
                       OR UOTId = ?
                       OR UOTUserName = ?
                    ORDER BY CASE
                        WHEN UOTLocalADUuser = ? THEN 0
                        WHEN UOTMail = ? THEN 1
                        ELSE 2
                    END
                    """, key, key, key, key, key, key);
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> row = rows.getFirst();
            return new ResolvedUser(text(row.get("UOTLocalADUuser")), text(row.get("UOTUserName")));
        } catch (RuntimeException e) {
            LOG.warn("Activity log user lookup failed: {}", e.getMessage());
            return null;
        }
    }

    private static String currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !StringUtils.hasText(authentication.getName())) {
            return null;
        }
        return authentication.getName().trim();
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String clip(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private record ResolvedUser(String localAdUser, String userName) {
    }
}
