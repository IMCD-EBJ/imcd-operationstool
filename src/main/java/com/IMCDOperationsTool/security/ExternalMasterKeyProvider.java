package com.IMCDOperationsTool.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

/**
 * Resolves the master key used to decrypt ENC(...) values.
 * Order: property security.enc.master-key, then the configured environment variable, then the system property.
 */
public class ExternalMasterKeyProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalMasterKeyProvider.class);

    private final Environment environment;

    public ExternalMasterKeyProvider(Environment environment) {
        this.environment = environment;
    }

    public String loadMasterKey() {
        String inlineKey = trimToNull(environment.getProperty("security.enc.master-key"));
        if (inlineKey != null) {
            return inlineKey;
        }

        String envVarName = environment.getProperty("security.enc.master-key-env-var", "MOSAIC_MASTER_KEY");
        String envKey = trimToNull(System.getenv(envVarName));
        if (envKey != null) {
            return envKey;
        }

        String systemPropertyName = environment.getProperty("security.enc.master-key-system-property", "mosaic.master.key");
        String systemKey = trimToNull(System.getProperty(systemPropertyName));
        if (systemKey != null) {
            return systemKey;
        }

        String errorMsg = "Master key for ENC() values not found. Configure one of: property 'security.enc.master-key', "
                + "environment variable '" + envVarName + "', or system property '" + systemPropertyName + "'.";
        LOG.error(errorMsg);
        throw new IllegalStateException(errorMsg);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
