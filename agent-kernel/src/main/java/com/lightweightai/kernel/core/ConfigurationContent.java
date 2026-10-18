package com.lightweightai.kernel.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.security.MessageDigest;
import java.util.*;

/** Immutable JSON configuration values and deterministic content hashing. */
public final class ConfigurationContent {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private ConfigurationContent() {}

    public static Object freeze(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) return value;
        if (value instanceof Number number) {
            if ((number instanceof Double || number instanceof Float) && !Double.isFinite(number.doubleValue()))
                throw new IllegalArgumentException("Configuration numbers must be finite");
            if (!(number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long
                    || number instanceof Float || number instanceof Double || number instanceof java.math.BigDecimal
                    || number instanceof java.math.BigInteger)) throw new IllegalArgumentException("Unsupported number type");
            return number;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new TreeMap<>();
            map.forEach((key, item) -> {
                if (!(key instanceof String)) throw new IllegalArgumentException("Configuration keys must be strings");
                copy.put((String) key, freeze(item));
            });
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(item -> copy.add(freeze(item)));
            return Collections.unmodifiableList(copy);
        }
        throw new IllegalArgumentException("Configuration value is not JSON: " + value.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, ?> value) {
        return (Map<String, Object>) freeze(value);
    }

    public static String digest(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JSON.writeValueAsBytes(freeze(value))));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot hash configuration", e);
        }
    }
}
