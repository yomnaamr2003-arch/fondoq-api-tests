package com.fondoq.api.support;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Central configuration. Resolution order for every setting:
 * 1. JVM system property        (mvn test -DbaseUrl=http://localhost:3001)
 * 2. environment variable       (BASE_URL, API_USERNAME, API_PASSWORD)
 * 3. src/test/resources/config.properties
 * Nothing is hard-coded in the tests themselves.
 */
public final class TestConfig {

    private static final Properties DEFAULTS = load();

    private TestConfig() {
    }

    public static String baseUrl() {
        return resolve("baseUrl", "BASE_URL");
    }

    public static String username() {
        return resolve("username", "API_USERNAME");
    }

    public static String password() {
        return resolve("password", "API_PASSWORD");
    }

    private static String resolve(String property, String envVar) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(envVar);
        }
        if (value == null || value.isBlank()) {
            value = DEFAULTS.getProperty(property);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing configuration value: " + property);
        }
        return value.trim();
    }

    private static Properties load() {
        Properties props = new Properties();
        try (InputStream in = TestConfig.class.getResourceAsStream("/config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read config.properties", e);
        }
        return props;
    }
}
