package io.github.inryeokoffice.configcontract.deployment.compose

import io.github.inryeokoffice.configcontract.core.ConfigurationKey
import io.github.inryeokoffice.configcontract.core.ProvidedConfiguration
import io.github.inryeokoffice.configcontract.core.SourceMetadata
import io.github.inryeokoffice.configcontract.deployment.DeploymentInputException
import io.github.inryeokoffice.configcontract.deployment.DeploymentSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ComposeAdapterTest {
    @Test
    fun `entries map to core provided configuration with source name and 1-based line`() {
        val content =
            """
            services:
              web:
                environment:
                  - FOO=bar
                  - BAZ=qux
            """.trimIndent() + "\n"
        val source = DeploymentSource("docker-compose.yml", content)

        assertEquals(
            listOf(
                ProvidedConfiguration(ConfigurationKey.of("FOO"), SourceMetadata("docker-compose.yml:4")),
                ProvidedConfiguration(ConfigurationKey.of("BAZ"), SourceMetadata("docker-compose.yml:5")),
            ),
            ComposeAdapter.read(source),
        )
    }

    @Test
    fun `values are not exposed through the core representation`() {
        val content =
            """
            services:
              web:
                environment:
                  SECRET: super-secret-value
            """.trimIndent() + "\n"
        val source = DeploymentSource("docker-compose.yml", content)

        val result = ComposeAdapter.read(source)

        assertEquals(
            listOf(ProvidedConfiguration(ConfigurationKey.of("SECRET"), SourceMetadata("docker-compose.yml:4"))),
            result,
        )
    }

    @Test
    fun `malformed source throws with the source name and every problem`() {
        val content =
            """
            services:
              web:
                env_file: .env
                extends:
                  service: base
            """.trimIndent() + "\n"
        val source = DeploymentSource("broken-compose.yml", content)

        val exception =
            assertThrows(DeploymentInputException::class.java) {
                ComposeAdapter.read(source)
            }

        assertEquals("broken-compose.yml", exception.sourceName)
        assertEquals(listOf(3, 4), exception.problems.map { it.line })
    }

    @Test
    fun `empty and services-only sources produce no provided configuration`() {
        assertEquals(emptyList<ProvidedConfiguration>(), ComposeAdapter.read(DeploymentSource("empty.compose.yml", "")))
        assertEquals(
            emptyList<ProvidedConfiguration>(),
            ComposeAdapter.read(DeploymentSource("no-env.compose.yml", "services:\n  web:\n    image: example\n")),
        )
    }
}
