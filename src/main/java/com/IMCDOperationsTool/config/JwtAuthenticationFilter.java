package com.IMCDOperationsTool.config;

import com.IMCDOperationsTool.security.AuthenticationModeService;
import com.IMCDOperationsTool.services.ActivityLogService;
import com.IMCDOperationsTool.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter copied from Price Tool.
 * Local login paths are skipped only when the {@code loc} profile is active.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final Log logger = LogFactory.getLog(this.getClass());
    private final JwtUtil jwtUtil;
    private final AuthenticationModeService authenticationModeService;
    private final ActivityLogService activityLogService;

    @Value("${commonsapp.url}")
    private String commonAppsUrl;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, AuthenticationModeService authenticationModeService,
            ActivityLogService activityLogService) {
        this.jwtUtil = jwtUtil;
        this.authenticationModeService = authenticationModeService;
        this.activityLogService = activityLogService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = getRequestPath(request);

        return path.equals("/auth/config")
                || (authenticationModeService.isLocalLoginEnabled() && path.equals("/login_dev"))
                || (authenticationModeService.isLocalLoginEnabled() && path.equals("/ping_dev"))
                || (authenticationModeService.isLocalLoginEnabled() && path.equals("/login.html"))
                || path.startsWith("/js/")
                || path.startsWith("/styles/")
                || path.startsWith("/imgs/")
                || path.startsWith("/components/")
                || path.contains("favicon")
                || path.contains("manifest");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String context = request.getContextPath();
        String path = getRequestPath(request);

        if (isLocalOnlyPath(path) && !authenticationModeService.isLocalLoginEnabled()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String containerUser = resolveContainerUser(request);
        String jwt = jwtUtil.getJwtFromCookie(request);

        if (jwt == null) {
            if (StringUtils.hasText(containerUser)) {
                continueAuthenticated(containerUser, "container:" + containerUser, request, response, filterChain);
                return;
            }
            if ("/login.html".equals(path)) {
                filterChain.doFilter(request, response);
                return;
            }
            handleUnauthenticated(response, context);
            return;
        }

        String username;
        String sessionKey;
        try {
            Claims claims = jwtUtil.getJwtClaims(jwt);
            username = claims.getSubject();
            sessionKey = jwt;
            if (!StringUtils.hasText(username)) {
                handleUnauthenticated(response, context);
                return;
            }
        } catch (Exception e) {
            if (StringUtils.hasText(containerUser)) {
                logger.warn("JWT not available/invalid; falling back to container user " + containerUser);
                username = containerUser;
                sessionKey = "container:" + containerUser;
            } else {
                logger.error("Error validating JWT", e);
                handleUnauthenticated(response, context);
                return;
            }
        }

        continueAuthenticated(username, sessionKey, request, response, filterChain);
    }

    private void continueAuthenticated(String username, String sessionKey, HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        setAuthenticatedUser(username);
        activityLogService.logSafenetLogin(sessionKey);
        filterChain.doFilter(request, response);
    }

    private String getRequestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return uri.substring(context.length());
    }

    private boolean isLocalOnlyPath(String path) {
        return path.equals("/login_dev")
                || path.equals("/ping_dev")
                || path.equals("/login.html");
    }

    private void handleUnauthenticated(HttpServletResponse response, String context) throws IOException {
        if (authenticationModeService.isLocalLoginEnabled()) {
            response.sendRedirect(context + "/login.html");
            return;
        }

        response.sendRedirect(commonAppsUrl);
    }

    private String resolveContainerUser(HttpServletRequest request) {
        if (request.getUserPrincipal() != null && StringUtils.hasText(request.getUserPrincipal().getName())) {
            return request.getUserPrincipal().getName();
        }

        if (StringUtils.hasText(request.getRemoteUser())) {
            return request.getRemoteUser();
        }

        return null;
    }

    private void setAuthenticatedUser(String username) {
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        UserDetails userDetails = new User(username, "", authorities);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null,
                authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
