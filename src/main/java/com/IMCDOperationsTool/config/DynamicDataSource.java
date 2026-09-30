package com.IMCDOperationsTool.config;

import java.sql.Connection;
import java.sql.SQLException;

import org.codehaus.jettison.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.AbstractDataSource;

import com.IMCDOperationsTool.security.EncPropertyResolver;
import com.IMCDOperationsTool.security.ExternalMasterKeyProvider;
import com.IMCDOperationsTool.utils.DatabaseCredentials;
import com.IMCDOperationsTool.utils.SecretsManagerUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Data source that loads database credentials once at startup from AWS Secrets
 * Manager or from local properties, then serves connections from a HikariCP pool.
 */
public class DynamicDataSource extends AbstractDataSource {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicDataSource.class);
    private final Environment environment;
    private final HikariDataSource dataSource;

    public DynamicDataSource(String secretName, String region, Environment environment) throws JSONException {
        this.environment = environment;
        DatabaseCredentials credentials = resolveCredentials(secretName, region);
        this.dataSource = buildHikariDataSource(credentials);
    }

    private boolean usesAwsSecrets() {
        return Boolean.parseBoolean(environment.getProperty("use.aws.secrets", "true"));
    }

    private DatabaseCredentials resolveCredentials(String secretName, String region) throws JSONException {
        if (usesAwsSecrets()) {
            return SecretsManagerUtil.getDatabaseCredentials(secretName, region);
        }

        String rawUsername = environment.getProperty("spring.datasource.username");
        String rawPassword = environment.getProperty("spring.datasource.password");

        boolean requiresServerKey = EncPropertyResolver.isEncrypted(rawUsername)
                || EncPropertyResolver.isEncrypted(rawPassword);

        String resolvedUsername = rawUsername;
        String resolvedPassword = rawPassword;

        if (requiresServerKey) {
            String masterKey = new ExternalMasterKeyProvider(environment).loadMasterKey();
            resolvedUsername = EncPropertyResolver.resolve(rawUsername, () -> masterKey);
            resolvedPassword = EncPropertyResolver.resolve(rawPassword, () -> masterKey);
        }

        DatabaseCredentials credentials = new DatabaseCredentials();
        credentials.setUsername(resolvedUsername);
        credentials.setPassword(resolvedPassword);
        return credentials;
    }

    private HikariDataSource buildHikariDataSource(DatabaseCredentials credentials) {
        HikariConfig config = new HikariConfig();

        if (usesAwsSecrets()) {
            config.setJdbcUrl(buildJdbcUrl(credentials.getHost(), credentials.getPort()));
        } else {
            String url = environment.getProperty("spring.datasource.url");
            config.setJdbcUrl(appendJdbcDriverOptions(url));
        }

        config.setUsername(credentials.getUsername());
        config.setPassword(credentials.getPassword());
        config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        config.setConnectionInitSql(
                "SET ARITHABORT ON; SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; "
                        + "SET CONCAT_NULL_YIELDS_NULL ON; SET QUOTED_IDENTIFIER ON; SET NUMERIC_ROUNDABORT OFF;");
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setMaxLifetime(1_800_000);
        config.setConnectionTimeout(10_000);
        config.setLeakDetectionThreshold(60_000);
        return new HikariDataSource(config);
    }

    private static String buildJdbcUrl(String host, int port) {
        return appendJdbcDriverOptions(
                "jdbc:sqlserver://" + host + ":" + port
                        + ";databaseName=OPERATIONSTOOL;encrypt=true;trustServerCertificate=true;");
    }

    private static String appendJdbcDriverOptions(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String options = "responseBuffering=full;socketTimeout=120000;loginTimeout=10";
        if (url.contains("responseBuffering=")) {
            return url;
        }
        return url.endsWith(";") ? url + options : url + ";" + options;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOG.info("HikariCP pool closed.");
        }
    }
}
