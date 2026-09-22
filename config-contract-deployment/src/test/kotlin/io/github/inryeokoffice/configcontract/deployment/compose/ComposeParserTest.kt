package io.github.inryeokoffice.configcontract.deployment.compose

import io.github.inryeokoffice.configcontract.deployment.DeploymentInputException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ComposeParserTest {
    @Test
    fun `list form entries report each item's own line`() {
        val expected =
            listOf(
                ComposeEntry("APP_ENV", 4),
                ComposeEntry("PORT", 5),
                ComposeEntry("DATABASE_URL", 6),
            )

        assertEquals(expected, parse("list-form.compose.yml"))
    }

    @Test
    fun `map form entries report each key's own line`() {
        val expected =
            listOf(
                ComposeEntry("APP_ENV", 4),
                ComposeEntry("PORT", 5),
                ComposeEntry("DEBUG", 6),
            )

        assertEquals(expected, parse("map-form.compose.yml"))
    }

    @Test
    fun `the same key in different services produces separate entries in document order`() {
        val expected =
            listOf(
                ComposeEntry("SHARED_KEY", 4),
                ComposeEntry("API_ONLY", 5),
                ComposeEntry("SHARED_KEY", 8),
                ComposeEntry("WORKER_ONLY", 9),
            )

        assertEquals(expected, parse("multi-service.compose.yml"))
    }

    @Test
    fun `passthrough and empty values are present in both list and map form`() {
        val expected =
            listOf(
                ComposeEntry("PASSTHROUGH", 4),
                ComposeEntry("EMPTY_EQ", 5),
                ComposeEntry("REGULAR", 6),
                ComposeEntry("MAP_PASSTHROUGH", 9),
                ComposeEntry("MAP_EMPTY", 10),
                ComposeEntry("MAP_REGULAR", 11),
            )

        assertEquals(expected, parse("passthrough-and-empty.compose.yml"))
    }

    @Test
    fun `interpolation inside a value is kept as-is and never produces a problem`() {
        val expected =
            listOf(
                ComposeEntry("DOLLAR_VAR", 4),
                ComposeEntry("BRACED_VAR", 5),
                ComposeEntry("MAP_VAR", 8),
            )

        assertEquals(expected, parse("interpolation-value.compose.yml"))
    }

    @Test
    fun `a null environment and a null service body are empty, not a problem`() {
        val expected = listOf(ComposeEntry("KEEP", 9))

        assertEquals(expected, parse("null-values.compose.yml"))
    }

    @Test
    fun `an aliased environment is reported at the anchor's definition line, not the alias site`() {
        val expected =
            listOf(
                ComposeEntry("SHARED_KEY", 4),
                ComposeEntry("SHARED_KEY", 4),
            )

        assertEquals(expected, parse("alias.compose.yml"))
    }

    @Test
    fun `every malformed or unsupported line is reported once, in line order`() {
        val exception =
            assertThrows(DeploymentInputException::class.java) {
                parse("malformed.compose.yml")
            }

        assertEquals("malformed.compose.yml", exception.sourceName)
        assertEquals(
            listOf(1, 5, 6, 10, 11, 13, 14, 18, 19, 20, 24),
            exception.problems.map { it.line },
        )
        assertEquals(
            listOf(
                "Docker Compose construct 'include' is not supported",
                "Docker Compose construct 'env_file' is not supported",
                "Docker Compose construct 'extends' is not supported",
                "Duplicate key 'FOO' (first defined at line 9)",
                "Key '\$BAD' must not contain interpolation",
                "Value for key 'NESTED' must not be a nested map or list",
                "Docker Compose construct '<<' is not supported",
                "Duplicate key 'FOO' (first defined at line 17)",
                "Environment list item must be a string",
                "Service name '\$bad' must not contain interpolation",
                "'environment' must be a list or a mapping",
            ),
            exception.problems.map { it.message },
        )
    }

    @Test
    fun `a document root that is not a mapping is reported at its own line`() {
        val exception =
            assertThrows(DeploymentInputException::class.java) {
                parse("bad-root.compose.yml")
            }

        assertEquals(listOf(1), exception.problems.map { it.line })
        assertEquals(listOf("Root must be a YAML mapping"), exception.problems.map { it.message })
    }

    @Test
    fun `a second YAML document is reported once, at its own start line`() {
        val exception =
            assertThrows(DeploymentInputException::class.java) {
                parse("multi-document.compose.yml")
            }

        assertEquals(listOf("Multiple YAML documents are not supported"), exception.problems.map { it.message })
    }

    @Test
    fun `invalid YAML syntax is reported without exposing scanned content`() {
        val exception =
            assertThrows(DeploymentInputException::class.java) {
                parse("invalid-syntax.compose.yml")
            }

        assertEquals(listOf("Invalid YAML syntax"), exception.problems.map { it.message })
    }

    @Test
    fun `content without a services mapping is reported as a problem`() {
        val exception =
            assertThrows(DeploymentInputException::class.java) {
                ComposeParser.parse("no-services.compose.yml", "version: \"3\"\n")
            }

        assertEquals(listOf("Missing 'services' mapping"), exception.problems.map { it.message })
    }

    @Test
    fun `empty content produces an empty result`() {
        assertEquals(emptyList<ComposeEntry>(), ComposeParser.parse("empty.compose.yml", ""))
    }

    @Test
    fun `a non-mapping non-null service body is reported at the service's line`() {
        listOf("[]", "disabled", "5", "true", "[a, b]").forEach { body ->
            val exception =
                assertThrows(DeploymentInputException::class.java) {
                    ComposeParser.parse("svc.compose.yml", "services:\n  web: $body\n")
                }

            assertEquals(listOf(2), exception.problems.map { it.line }, "body: $body")
            assertEquals(listOf("Service 'web' must be a mapping"), exception.problems.map { it.message }, "body: $body")
        }
    }

    @Test
    fun `a null service body is empty in both bare and tilde form`() {
        assertEquals(emptyList<ComposeEntry>(), ComposeParser.parse("svc.compose.yml", "services:\n  web:\n"))
        assertEquals(emptyList<ComposeEntry>(), ComposeParser.parse("svc.compose.yml", "services:\n  web: ~\n"))
    }

    @Test
    fun `core-schema null literals are an empty service body and environment`() {
        listOf("NULL", "Null").forEach { literal ->
            assertEquals(
                emptyList<ComposeEntry>(),
                ComposeParser.parse("svc.compose.yml", "services:\n  web: $literal\n"),
                literal,
            )
        }
        assertEquals(
            emptyList<ComposeEntry>(),
            ComposeParser.parse("env.compose.yml", "services:\n  web:\n    environment: NULL\n"),
        )
    }

    @Test
    fun `core-schema bool and null literals are rejected as list items and keys, quoted stay strings`() {
        val list = "services:\n  web:\n    environment:\n      - TRUE\n      - Null\n      - False\n      - \"TRUE\"\n"
        val listException = assertThrows(DeploymentInputException::class.java) { ComposeParser.parse("l.compose.yml", list) }
        assertEquals(listOf(4, 5, 6), listException.problems.map { it.line })
        assertEquals(List(3) { "Environment list item must be a string" }, listException.problems.map { it.message })

        val map = "services:\n  web:\n    environment:\n      TRUE: x\n      \"TRUE\": y\n"
        val mapException = assertThrows(DeploymentInputException::class.java) { ComposeParser.parse("m.compose.yml", map) }
        assertEquals(listOf(4), mapException.problems.map { it.line })
        assertEquals(listOf("Mapping key must be a string"), mapException.problems.map { it.message })

        assertEquals(
            listOf(ComposeEntry("TRUE", 4)),
            ComposeParser.parse("q.compose.yml", "services:\n  web:\n    environment:\n      - \"TRUE\"\n"),
        )
    }

    @Test
    fun `only a null environment is empty, other scalars are reported`() {
        assertEquals(
            emptyList<ComposeEntry>(),
            ComposeParser.parse("env.compose.yml", "services:\n  web:\n    environment: ~\n"),
        )
        val exception =
            assertThrows(DeploymentInputException::class.java) {
                ComposeParser.parse("env.compose.yml", "services:\n  web:\n    environment: 5\n")
            }
        assertEquals(listOf("'environment' must be a list or a mapping"), exception.problems.map { it.message })
    }

    @Test
    fun `non-string environment list items are reported without their values`() {
        val content =
            """
            services:
              web:
                environment:
                  - true
                  - 123
                  - null
                  - "FOO=1"
                  - "true"
            """.trimIndent()

        val exception = assertThrows(DeploymentInputException::class.java) { ComposeParser.parse("list.compose.yml", content) }

        assertEquals(listOf(4, 5, 6), exception.problems.map { it.line })
        assertEquals(
            List(3) { "Environment list item must be a string" },
            exception.problems.map { it.message },
        )
    }

    @Test
    fun `quoted list items that look like non-strings stay valid strings`() {
        val content = "services:\n  web:\n    environment:\n      - \"FOO=1\"\n      - \"true\"\n"

        assertEquals(
            listOf(ComposeEntry("FOO", 4), ComposeEntry("true", 5)),
            ComposeParser.parse("list.compose.yml", content),
        )
    }

    @Test
    fun `unquoted bool and number map keys are reported without their values`() {
        val content = "services:\n  web:\n    environment:\n      true: x\n      123: x\n      OK: y\n"

        val exception = assertThrows(DeploymentInputException::class.java) { ComposeParser.parse("map.compose.yml", content) }

        assertEquals(listOf(4, 5), exception.problems.map { it.line })
        assertEquals(List(2) { "Mapping key must be a string" }, exception.problems.map { it.message })
    }

    @Test
    fun `bool and number service names are reported without their values`() {
        val content = "services:\n  true:\n    image: a\n  123:\n    image: b\n"

        val exception = assertThrows(DeploymentInputException::class.java) { ComposeParser.parse("svc.compose.yml", content) }

        assertEquals(listOf(2, 4), exception.problems.map { it.line })
        assertEquals(List(2) { "Mapping key must be a string" }, exception.problems.map { it.message })
    }

    private fun parse(fixtureName: String): List<ComposeEntry> = ComposeParser.parse(fixtureName, fixture(fixtureName))

    private fun fixture(name: String): String {
        val stream =
            requireNotNull(javaClass.getResourceAsStream("/compose/$name")) {
                "Missing test fixture: compose/$name"
            }
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
