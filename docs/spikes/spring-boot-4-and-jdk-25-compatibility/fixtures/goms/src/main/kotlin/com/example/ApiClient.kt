package com.example

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ApiClient(
    @Value("\${goms.api.key}") private val apiKey: String,
    @Value("\${goms.api.timeout:5s}") private val timeout: String,
) {
    fun describe(): String = "timeout=$timeout keyLength=${apiKey.length}"
}
