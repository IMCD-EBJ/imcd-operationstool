package com.IMCDOperationsTool.config;

import org.codehaus.jettison.json.JSONException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DataSourceConfig {

    private final Environment environment;

    @Value("${aws.datasource.secret}")
    private String awsDatasourceSecret;

    public DataSourceConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean(destroyMethod = "close")
    public DynamicDataSource dataSource() throws JSONException {
        return new DynamicDataSource(awsDatasourceSecret, "eu-central-1", environment);
    }
}
