package com.IMCDOperationsTool.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Supplier;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves property values that may be wrapped as ENC(...). Values without ENC are returned unchanged.
 */
public final class EncPropertyResolver {

    private static final Logger LOG = LoggerFactory.getLogger(EncPropertyResolver.class);
    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";
    private static final String CIPHER_ALGORITHM = "AES";

    private EncPropertyResolver() {
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENC_PREFIX) && value.endsWith(ENC_SUFFIX);
    }

    public static String resolve(String rawValue, Supplier<String> serverKeySupplier) {
        if (!isEncrypted(rawValue)) {
            return rawValue;
        }

        String serverKey = serverKeySupplier.get();
        if (serverKey == null || serverKey.isBlank()) {
            throw new IllegalStateException("Master key is empty. Cannot resolve ENC() value.");
        }

        try {
            String payload = unwrap(rawValue);
            byte[] decrypted = decryptAES(payload, serverKey);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decrypt property value: " + e.getMessage(), e);
        }
    }

    private static String unwrap(String encryptedValue) {
        return encryptedValue.substring(ENC_PREFIX.length(), encryptedValue.length() - ENC_SUFFIX.length());
    }

    private static byte[] decryptAES(String encryptedBase64, String masterKey) throws Exception {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedBase64);
        byte[] key = java.security.MessageDigest.getInstance("SHA-256").digest(masterKey.getBytes(StandardCharsets.UTF_8));
        SecretKeySpec keySpec = new SecretKeySpec(key, CIPHER_ALGORITHM);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        try {
            return cipher.doFinal(encryptedBytes);
        } catch (Exception e) {
            LOG.error("AES decryption failed. This usually means the master key is incorrect.");
            throw e;
        }
    }
}
