package com.example

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GreetingService(
    @Value("\${greeting.locale:en}") locale: String,
) {
    @Value("\${greeting.message}")
    lateinit var message: String

    val localeTag: String = locale
}
