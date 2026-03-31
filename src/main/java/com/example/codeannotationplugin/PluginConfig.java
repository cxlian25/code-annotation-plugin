package com.example.codeannotationplugin;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class PluginConfig {

    private static final String CONFIG_RESOURCE = "/code-annotation.properties";
    private static final String BACKEND_BASE_URL_KEY = "backend.base-url";
    private static final String DEFAULT_BACKEND_BASE_URL = "http://127.0.0.1:8080";

    private static final Properties PROPERTIES = loadProperties();

    private PluginConfig() {
    }

    @NotNull
    public static String getBackendBaseUrl() {
        String configured = PROPERTIES.getProperty(BACKEND_BASE_URL_KEY, DEFAULT_BACKEND_BASE_URL);
        String trimmed = configured == null ? DEFAULT_BACKEND_BASE_URL : configured.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_BACKEND_BASE_URL;
        }
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    @NotNull
    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream stream = PluginConfig.class.getResourceAsStream(CONFIG_RESOURCE)) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException ignored) {
            // Fall back to default values when config cannot be loaded.
        }
        return properties;
    }
}