package com.example

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties("app.mail")
data class MailProperties(
    val sender: String,
    val replyTo: String?,
    val retries: Int = 3,
    @DefaultValue("smtp.example.com") val host: String,
)
