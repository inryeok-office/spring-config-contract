package com.example

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.payment")
data class PaymentProperties(
    // Required: non-null, no Kotlin default, no @DefaultValue.
    val apiKey: String,
    // Required, provided by compose.yml.
    val merchantId: String,
    // Optional: Kotlin default argument.
    val retries: Int = 3,
    // Optional: nullable.
    val callbackUrl: String?,
)
