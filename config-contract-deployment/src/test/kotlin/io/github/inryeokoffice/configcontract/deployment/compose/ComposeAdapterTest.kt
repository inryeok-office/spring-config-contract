package io.github.inryeokoffice.configcontract.deployment.compose

import io.github.inryeokoffice.configcontract.core.ConfigurationKey
import io.github.inryeokoffice.configcontract.core.ProvidedConfiguration
import io.github.inryeokoffice.configcontract.core.SourceMetadata
import io.github.inryeokoffice.configcontract.deployment.DeploymentInputException
import io.github.inryeokoffice.configcontract.deployment.DeploymentSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
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

    @Test
    fun `an empty selection reads every service, with or without sources`() {
        assertSame(ComposeAdapter, ComposeAdapter.selecting(emptyList(), emptyList()))
        assertSame(ComposeAdapter, ComposeAdapter.selecting(emptySet(), listOf(appAndDatabase)))
    }

    @Test
    fun `a selected service provides only its own keys with name and line provenance`() {
        val adapter = ComposeAdapter.selecting(listOf("app"), listOf(appAndDatabase))

        assertEquals(
            listOf(provided("SHARED_KEY", "compose.yml:4"), provided("APP_ONLY", "compose.yml:5")),
            adapter.read(appAndDatabase),
        )
    }

    @Test
    fun `selection order and duplicate names do not change results or provenance`() {
        val sources = listOf(appAndDatabase)
        val expected =
            listOf(
                provided("SHARED_KEY", "compose.yml:4"),
                provided("APP_ONLY", "compose.yml:5"),
                provided("SHARED_KEY", "compose.yml:8"),
                provided("DATABASE_ONLY", "compose.yml:9"),
            )

        assertEquals(expected, ComposeAdapter.selecting(listOf("app", "database"), sources).read(appAndDatabase))
        assertEquals(expected, ComposeAdapter.selecting(listOf("database", "app", "database"), sources).read(appAndDatabase))
        assertEquals(ComposeAdapter.read(appAndDatabase), ComposeAdapter.selecting(setOf("database", "app"), sources).read(appAndDatabase))
    }

    @Test
    fun `a selected service without an environment is valid and provides nothing`() {
        val source = DeploymentSource("compose.yml", "services:\n  app:\n    image: example\n  db:\n")

        assertEquals(emptyList<ProvidedConfiguration>(), ComposeAdapter.selecting(listOf("app", "db"), listOf(source)).read(source))
    }

    @Test
    fun `names are validated across every source and each source is read independently`() {
        val workerFile =
            DeploymentSource("deploy/worker.compose.yml", "services:\n  worker:\n    environment:\n      - WORKER_ONLY=1\n")
        val sharedFile =
            DeploymentSource("deploy/shared.compose.yml", "services:\n  app:\n    environment:\n      - EXTRA=1\n")
        val sources = listOf(appAndDatabase, workerFile, sharedFile)

        val worker = ComposeAdapter.selecting(listOf("worker"), sources)
        assertEquals(emptyList<ProvidedConfiguration>(), worker.read(appAndDatabase))
        assertEquals(listOf(provided("WORKER_ONLY", "deploy/worker.compose.yml:4")), worker.read(workerFile))

        val app = ComposeAdapter.selecting(listOf("app"), sources)
        assertEquals(
            listOf(
                provided("SHARED_KEY", "compose.yml:4"),
                provided("APP_ONLY", "compose.yml:5"),
                provided("EXTRA", "deploy/shared.compose.yml:4"),
            ),
            sources.flatMap { app.read(it) },
        )
    }

    @Test
    fun `a name declared by no source fails with every unknown and declared name in sorted order`() {
        val workerFile = DeploymentSource("worker.compose.yml", "services:\n  worker:\n")

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                ComposeAdapter.selecting(listOf("wroker", "app", "dtabase"), listOf(appAndDatabase, workerFile))
            }

        assertEquals(
            "Selected Docker Compose services are not declared in any configured Docker Compose file: " +
                "'dtabase', 'wroker' (declared services: 'app', 'database', 'worker')",
            exception.message,
        )
    }

    @Test
    fun `a name is unknown when every source is empty`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                ComposeAdapter.selecting(listOf("app"), listOf(DeploymentSource("empty.compose.yml", "")))
            }

        assertEquals(
            "Selected Docker Compose services are not declared in any configured Docker Compose file: " +
                "'app' (declared services: none)",
            exception.message,
        )
    }

    @Test
    fun `names are matched exactly`() {
        listOf("App", " app", "app ").forEach { name ->
            assertThrows(IllegalArgumentException::class.java, { ComposeAdapter.selecting(listOf(name), listOf(appAndDatabase)) }, name)
        }
    }

    @Test
    fun `blank and interpolated names fail before sources are inspected`() {
        listOf("", " ", "\t").forEach { blank ->
            val exception =
                assertThrows(IllegalArgumentException::class.java) {
                    ComposeAdapter.selecting(listOf("app", blank), emptyList())
                }
            assertEquals("Selected Docker Compose service names must not be blank", exception.message)
        }

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                ComposeAdapter.selecting(listOf("\${APP}", "app", "\$app"), listOf(appAndDatabase))
            }
        assertEquals("Selected Docker Compose service names must not contain interpolation: '\$app', '\${APP}'", exception.message)
    }

    @Test
    fun `a non-empty selection without sources fails`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                ComposeAdapter.selecting(listOf("worker", "app"), emptyList())
            }

        assertEquals(
            "Docker Compose services are selected ('app', 'worker') but no Docker Compose files are configured",
            exception.message,
        )
    }

    @Test
    fun `a global problem in any source fails the selection with that source's problems`() {
        val broken = DeploymentSource("broken.compose.yml", "include:\n  - other.yml\nservices:\n  app:\n")

        val exception =
            assertThrows(DeploymentInputException::class.java) {
                ComposeAdapter.selecting(listOf("app"), listOf(appAndDatabase, broken))
            }

        assertEquals("broken.compose.yml", exception.sourceName)
        assertEquals(listOf(1), exception.problems.map { it.line })
    }

    @Test
    fun `unsupported content in an unselected service is ignored but fails when selected`() {
        val source =
            DeploymentSource(
                "compose.yml",
                "services:\n  app:\n    environment:\n      - APP_KEY=1\n  db:\n    env_file: .env\n",
            )

        assertEquals(
            listOf(provided("APP_KEY", "compose.yml:4")),
            ComposeAdapter.selecting(listOf("app"), listOf(source)).read(source),
        )
        val exception =
            assertThrows(DeploymentInputException::class.java) {
                ComposeAdapter.selecting(listOf("db"), listOf(source)).read(source)
            }
        assertEquals(listOf(6), exception.problems.map { it.line })
        assertThrows(DeploymentInputException::class.java) { ComposeAdapter.read(source) }
    }

    private val appAndDatabase =
        DeploymentSource(
            "compose.yml",
            """
            services:
              app:
                environment:
                  - SHARED_KEY=1
                  - APP_ONLY=2
              database:
                environment:
                  SHARED_KEY: 3
                  DATABASE_ONLY: 4
            """.trimIndent() + "\n",
        )

    private fun provided(
        key: String,
        source: String,
    ) = ProvidedConfiguration(ConfigurationKey.of(key), SourceMetadata(source))
}
