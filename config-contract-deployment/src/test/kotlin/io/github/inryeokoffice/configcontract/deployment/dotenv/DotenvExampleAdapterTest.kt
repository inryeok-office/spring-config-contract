package io.github.inryeokoffice.configcontract.deployment.dotenv

import io.github.inryeokoffice.configcontract.core.ConfigurationKey
import io.github.inryeokoffice.configcontract.core.ProvidedConfiguration
import io.github.inryeokoffice.configcontract.core.SourceMetadata
import io.github.inryeokoffice.configcontract.deployment.DeploymentInputException
import io.github.inryeokoffice.configcontract.deployment.DeploymentSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DotenvExampleAdapterTest {
    @Test
    fun `entries map to core provided configuration with source name and 1-based line`() {
        val source = DeploymentSource("config/.env.example", "FOO=bar\nBAZ=qux\n")

        assertEquals(
            listOf(
                ProvidedConfiguration(ConfigurationKey.of("FOO"), SourceMetadata("config/.env.example:1")),
                ProvidedConfiguration(ConfigurationKey.of("BAZ"), SourceMetadata("config/.env.example:2")),
            ),
            DotenvExampleAdapter.read(source),
        )
    }

    @Test
    fun `parsed values are not exposed through the core representation`() {
        val source = DeploymentSource(".env.example", "SECRET='super-secret-value'\n")

        val result = DotenvExampleAdapter.read(source)

        assertEquals(listOf(ProvidedConfiguration(ConfigurationKey.of("SECRET"), SourceMetadata(".env.example:1"))), result)
    }

    @Test
    fun `malformed source throws with the source name and every problem`() {
        val source = DeploymentSource("broken.env.example", "NO_EQUALS\n1INVALID=value\n")

        val exception =
            assertThrows(DeploymentInputException::class.java) {
                DotenvExampleAdapter.read(source)
            }

        assertEquals("broken.env.example", exception.sourceName)
        assertEquals(listOf(1, 2), exception.problems.map { it.line })
    }

    @Test
    fun `empty and comment-only sources produce no provided configuration`() {
        assertEquals(emptyList<ProvidedConfiguration>(), DotenvExampleAdapter.read(DeploymentSource("empty.env.example", "")))
        assertEquals(
            emptyList<ProvidedConfiguration>(),
            DotenvExampleAdapter.read(DeploymentSource("comments.env.example", "# only a comment\n")),
        )
    }
}
