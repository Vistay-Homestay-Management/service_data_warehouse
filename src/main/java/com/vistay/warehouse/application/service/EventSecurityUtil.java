package com.vistay.warehouse.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.ObjectMapper;

public final class EventSecurityUtil {

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "token", "accesstoken", "refreshtoken", "secret",
            "cccd", "nationalid", "idcard", "id_card", "rawcccd",
            "phone", "phonenumber", "phone_number",
            "email", "emailaddress",
            "bankaccount", "bank_account", "accountnumber", "account_number",
            "cardnumber", "card_number", "cvv"
    );

    private EventSecurityUtil() {
    }

    public static String computeSha256(String input) {
        if (input == null) {
            return "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }

    public static String sanitizePayload(String rawPayload, ObjectMapper objectMapper) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return "{}";
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(rawPayload, Map.class);
            if (map == null) {
                return "{}";
            }
            redactMap(map);
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private static void redactMap(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey().toLowerCase().replace("-", "").replace("_", "");
            if (SENSITIVE_FIELDS.contains(key)) {
                entry.setValue("[REDACTED]");
            } else if (entry.getValue() instanceof Map<?, ?> nested) {
                redactMap((Map<String, Object>) nested);
            } else if (entry.getValue() instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> itemMap) {
                        redactMap((Map<String, Object>) itemMap);
                    }
                }
            }
        }
    }
}
