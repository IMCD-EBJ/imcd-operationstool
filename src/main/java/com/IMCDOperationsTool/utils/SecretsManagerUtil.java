package com.IMCDOperationsTool.utils;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Retrieves database credentials from AWS Secrets Manager.
 * Expected JSON keys: username, password, host, port.
 */
public class SecretsManagerUtil {

    private static final ConcurrentHashMap<String, AWSSecretsManager> CLIENTS = new ConcurrentHashMap<>();

    private SecretsManagerUtil() {
    }

    public static DatabaseCredentials getDatabaseCredentials(String secretName, String region) throws JSONException {
        GetSecretValueRequest request = new GetSecretValueRequest().withSecretId(secretName);
        GetSecretValueResult result = CLIENTS.computeIfAbsent(region, r -> AWSSecretsManagerClientBuilder.standard()
                .withRegion(r)
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build()).getSecretValue(request);

        JSONObject json = new JSONObject(result.getSecretString());
        DatabaseCredentials credentials = new DatabaseCredentials();
        credentials.setUsername(json.getString("username"));
        credentials.setPassword(json.getString("password"));
        credentials.setHost(json.getString("host"));
        credentials.setPort(json.getInt("port"));
        return credentials;
    }
}
