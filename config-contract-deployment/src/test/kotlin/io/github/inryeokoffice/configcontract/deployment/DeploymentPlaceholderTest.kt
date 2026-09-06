package io.github.inryeokoffice.configcontract.deployment

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class DeploymentPlaceholderTest {
    @Test
    fun `deployment module scaffold is loadable`() {
        assertNotNull(DeploymentPlaceholder)
    }
}
