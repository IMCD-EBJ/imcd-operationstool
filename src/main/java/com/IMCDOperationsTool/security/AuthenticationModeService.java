package com.IMCDOperationsTool.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Resolves the authentication mode enabled for the current Spring profile.
 */
@Service
public class AuthenticationModeService {

    private final Environment environment;

    public AuthenticationModeService(Environment environment) {
        this.environment = environment;
    }

    /**
     * Indicates whether manual HTML login is allowed for the current runtime.
     *
     * @return {@code true} only when the local profile is active.
     */
    public boolean isLocalLoginEnabled() {
        return Arrays.asList(environment.getActiveProfiles()).contains("loc");
    }

    /**
     * Exposes active Spring profiles for diagnostics.
     *
     * @return active profile names.
     */
    public List<String> getActiveProfiles() {
        return Arrays.asList(environment.getActiveProfiles());
    }
}
