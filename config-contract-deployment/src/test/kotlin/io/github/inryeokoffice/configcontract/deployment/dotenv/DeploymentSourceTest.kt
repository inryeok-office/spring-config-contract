package io.github.inryeokoffice.configcontract.deployment.dotenv

import io.github.inryeokoffice.configcontract.deployment.DeploymentSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class DeploymentSourceTest {
    @Test
    fun `rejects unsafe source names`() {
        listOf(
            "/home/alice/.env.example",
            "C:/Users/alice/.env",
            "deploy\\prod.env",
            "../secret.env",
            "a\nb",
        ).forEach { name ->
            assertThrows<IllegalArgumentException> { DeploymentSource(name, "") }
        }
    }

    @Test
    fun `accepts project relative names`() {
        listOf(".env.example", "deploy/prod.env").forEach { name ->
            assertDoesNotThrow { DeploymentSource(name, "") }
        }
    }
}
