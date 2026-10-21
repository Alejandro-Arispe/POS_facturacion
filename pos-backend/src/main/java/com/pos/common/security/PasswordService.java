package com.pos.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public String hash(String raw) {
        return bcrypt.encode(raw);
    }

    public boolean matches(String raw, String hashed) {
        if (raw == null || hashed == null) {
            return false;
        }
        try {
            if (hashed.startsWith("$bcrypt-sha256$")) {
                return matchesPasslibBcryptSha256(raw, hashed);
            }
            return bcrypt.matches(raw, hashed);
        } catch (Exception ex) {
            return false;
        }
    }

    public String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo hashear el codigo", ex);
        }
    }

    public String randomAlphanumeric(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private boolean matchesPasslibBcryptSha256(String raw, String hashed) {
        // $bcrypt-sha256$v=2,t=2b,r=12$<salt>$<digest>
        String[] parts = hashed.split("\\$");
        if (parts.length < 5) {
            return false;
        }
        String params = parts[2];
        int rounds = 12;
        for (String item : params.split(",")) {
            if (item.startsWith("r=")) {
                rounds = Integer.parseInt(item.substring(2));
            }
        }
        String saltB64 = parts[3];
        String digest = parts[4];
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digestBytes = sha256.digest(raw.getBytes(StandardCharsets.UTF_8));
            String passwordDigest = Base64.getEncoder().encodeToString(digestBytes);
            String bcryptSalt = "$2b$" + String.format("%02d", rounds) + "$" + saltB64;
            String candidate = BCrypt.hashpw(passwordDigest, bcryptSalt);
            String candidateDigest = candidate.substring(candidate.lastIndexOf('$') + 1);
            return MessageDigest.isEqual(
                    digest.getBytes(StandardCharsets.US_ASCII),
                    candidateDigest.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ex) {
            return false;
        }
    }
}
