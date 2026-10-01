package com.example;

import org.springframework.beans.factory.annotation.Value;

public class AppSettings {
    @Value("${app.api-key}")
    private String apiKey;

    @Value("${app.db.url}")
    private String databaseUrl;
}
