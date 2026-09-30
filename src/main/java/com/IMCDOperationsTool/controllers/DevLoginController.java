package com.IMCDOperationsTool.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.IMCDOperationsTool.utils.JwtUtil;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

/**
 * REST controller for login operations in development environment.
 * <p>
 * Provides endpoints for simplified authentication and connectivity
 * verification,
 * intended exclusively for the development environment. Uses JWT for session
 * management
 * and queries the database to validate users.
 * </p>
 */
@RestController
@Profile("loc")
public class DevLoginController {

    private final JdbcTemplate jdbcTemplate;
    private final JwtUtil jwtUtil;

    /**
     * Constructor that injects the necessary dependencies for the controller.
     *
     * @param jdbcTemplate JDBC template to execute database queries
     * @param jwtUtil      utility to generate and validate JWT tokens
     */
    public DevLoginController(JdbcTemplate jdbcTemplate, JwtUtil jwtUtil) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Endpoint for authentication in development environment.
     * <p>
     * Validates the fixed password and searches for the user in the database. If
     * valid,
     * generates a JWT token and sets it in an HTTP-only cookie.
     * </p>
     *
     * @param user     username to authenticate
     * @param password password provided (must be "1Mcd@M0saic")
     * @param response HttpServletResponse object to set the cookie
     * @return {@link ResponseEntity} with user data if authentication is
     *         successful,
     *         or an error message with the corresponding status code
     */
    @PostMapping("/login_dev")
    public ResponseEntity<?> loginDev(
            @RequestParam String user,
            @RequestParam String password,
            HttpServletResponse response) {

        try {

            // Obtener password desde configuración
            String sqlConfig = """
                        SELECT TOP 1 CFG_KEY_LOCAL
                        FROM MOSAICTOOL.dbo.ConfigurationMosaicTool
                    """;
            String devPassword = jdbcTemplate.queryForObject(sqlConfig, String.class);

            // Validación de password
            if (password == null || devPassword == null || !devPassword.equals(password.trim())) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body("Incorrect password");
            }

            String sqlMail = """
                    SELECT TOP 1
                        UCA_Id,
                        UCA_Mail,
                        UCA_TipoUserId,
                        UCA_UserName,
                        UCA_IdRegion
                    FROM COMMON_APPS.dbo.UsersCommonApps
                    WHERE UCA_LocalADUser = ?
                    AND UCA_UserStatus = 'Active'
                    \s""";

            List<Map<String, Object>> users = jdbcTemplate.queryForList(sqlMail, user);

            if (users.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body("User not found");
            }

            String mail = (String) users.getFirst().get("UCA_Mail");

            String jwt = jwtUtil.generateToken(mail);

            Cookie cookie = new Cookie("jwt", jwt);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(60 * 60 * 8);

            response.addCookie(cookie);

            return ResponseEntity.ok(users);

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal error");

        }
    }

    /**
     * Connectivity-check endpoint for the development environment.
     * <p>
     * Returns a simple response confirming that the service is running.
     * </p>
     *
     * @return string {@code "OK"} indicating that the service is available
     */
    @GetMapping("/ping_dev")
    public String pingDev() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "OK_DB";
        } catch (Exception e) {
            return "ERROR_DB";
        }
    }
}
