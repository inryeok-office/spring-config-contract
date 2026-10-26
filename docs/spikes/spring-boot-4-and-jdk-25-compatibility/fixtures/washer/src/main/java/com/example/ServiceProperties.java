package com.example;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.service")
public record ServiceProperties(String endpoint, @DefaultValue("30s") Duration timeout) {
}
