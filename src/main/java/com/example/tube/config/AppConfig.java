package com.example.tube.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class AppConfig {

    private final Properties props = new Properties();

    public AppConfig(String path) {
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            props.load(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config: " + path, e);
        }
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public String getString(String key, String def) {
        return System.getProperty(key,
                System.getenv().getOrDefault(key.toUpperCase().replace('.', '_'),
                        props.getProperty(key, def)));
    }

    public int getInt(String key, int def) {
        return Integer.parseInt(getString(key, String.valueOf(def)));
    }
}
