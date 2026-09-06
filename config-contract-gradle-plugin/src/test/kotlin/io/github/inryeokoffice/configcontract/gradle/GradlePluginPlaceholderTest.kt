package io.github.inryeokoffice.configcontract.gradle

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class GradlePluginPlaceholderTest {
    @Test
    fun `gradle integration module scaffold is loadable`() {
        assertNotNull(GradlePluginPlaceholder)
    }
}
