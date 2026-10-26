package com.example

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties(prefix = "goms.mail")
data class MailProperties(
    val sender: String,
    val region: String,
    val retries: Int = 3,
    val replyTo: String?,
    @DefaultValue("smtp.example.com") val host: String,
)
