package com.example;

import org.springframework.beans.factory.annotation.Value;

public class AppSettings {
    @Value("${app.region:us}")
    private String region;

    @Value("${app.timeout:}")
    private String timeout;
}
