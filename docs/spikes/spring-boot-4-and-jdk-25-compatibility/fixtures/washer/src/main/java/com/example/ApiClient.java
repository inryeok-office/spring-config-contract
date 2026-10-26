package com.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApiClient {
    @Value("${app.api-key}")
    private String apiKey;

    @Value("${app.region:us}")
    private String region;
}
