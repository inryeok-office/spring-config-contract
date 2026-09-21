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

    private fun parse(fixtureName: String): List<ComposeEntry> = ComposeParser.parse(fixtureName, fixture(fixtureName))

    private fun fixture(name: String): String {
        val stream =
            requireNotNull(javaClass.getResourceAsStream("/compose/$name")) {
                "Missing test fixture: compose/$name"
            }
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
