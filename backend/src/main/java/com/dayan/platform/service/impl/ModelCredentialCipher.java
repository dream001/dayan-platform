package com.dayan.platform.service.impl;

import com.dayan.platform.config.ApplicationProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
class ModelCredentialCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    ModelCredentialCipher(ApplicationProperties properties) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    ("dayan:model-credentials:" + properties.security().jwtSecret())
                            .getBytes(StandardCharsets.UTF_8)
            );
            this.key = new SecretKeySpec(digest, "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize model credential encryption", exception);
        }
    }

    String encrypt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array()
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt model credential", exception);
        }
    }

    String decrypt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(value);
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted credential");
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to decrypt model credential", exception);
        }
    }
}
