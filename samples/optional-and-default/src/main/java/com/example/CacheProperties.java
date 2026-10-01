package com.example;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.cache")
public class CacheProperties {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
