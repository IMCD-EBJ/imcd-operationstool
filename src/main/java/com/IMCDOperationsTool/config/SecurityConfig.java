package com.IMCDOperationsTool.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.IMCDOperationsTool.security.AuthenticationModeService;

/**
 * Spring Security configuration copied from Price Tool.
 * Local login is available only on the {@code loc} profile. Production sends
 * anonymous browsers to CommonApps, where SafeNet signs them in.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationModeService authenticationModeService;
    private final String commonAppsUrl;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationModeService authenticationModeService,
            @Value("${commonsapp.url}") String commonAppsUrl) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationModeService = authenticationModeService;
        this.commonAppsUrl = commonAppsUrl;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint jsonEntryPoint = (request, response, authException) -> {
            String accept = request.getHeader("Accept");
            String ct = request.getContentType();
            String xrw = request.getHeader("X-Requested-With");
            boolean isApi = "XMLHttpRequest".equalsIgnoreCase(xrw)
                    || (accept != null && accept.contains("application/json"))
                    || (ct != null && ct.contains("application/json"));
            if (isApi) {
                response.setStatus(401);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"Unauthorized\"}");
            } else if (authenticationModeService.isLocalLoginEnabled()) {
                response.sendRedirect(response.encodeRedirectURL(
                        request.getContextPath() + "/login.html"));
            } else {
                response.sendRedirect(commonAppsUrl);
            }
        };

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/config",
                                "/login.html",
                                "/login_dev",
                                "/ping_dev",
                                "/js/**",
                                "/styles/**",
                                "/imgs/**",
                                "/components/**",
                                "/favicon*",
                                "/manifest*",
                                "/api/training/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jsonEntryPoint));

        return http.build();
    }
}
