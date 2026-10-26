package com.example

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class FeatureToggle {
    @Value("\${goms.feature.owner}")
    lateinit var owner: String

    @Value("\${goms.feature.beta:false}")
    var beta: String = "false"
}
