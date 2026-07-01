package com.eneik.production.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "lease")
public class LeaseConfig {

    private int defaultTtlSeconds = 300;
    private Map<String, Integer> overrides = new HashMap<>();

    public int getDefaultTtlSeconds() {
        return defaultTtlSeconds;
    }

    public void setDefaultTtlSeconds(int defaultTtlSeconds) {
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    public Map<String, Integer> getOverrides() {
        return overrides;
    }

    public void setOverrides(Map<String, Integer> overrides) {
        this.overrides = overrides;
    }

    public int getTtlForTag(String tag) {
        return overrides.getOrDefault(tag, defaultTtlSeconds);
    }
}
