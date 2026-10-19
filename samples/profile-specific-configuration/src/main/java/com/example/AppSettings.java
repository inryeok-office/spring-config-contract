package com.example;

import org.springframework.beans.factory.annotation.Value;

public class AppSettings {
    @Value("${app.greeting}")
    private String greeting;
}
