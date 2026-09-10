package io.github.inryeokoffice.configcontract.deployment.dotenv

import io.github.inryeokoffice.configcontract.deployment.DeploymentInputException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DotenvParserTest {
    @Test
    fun `normal entries support export, spaced keys, and literal placeholders`() {
        val expected =
            listOf(
                DotenvEntry("APP_ENV", "production", 1),
                DotenvEntry("PORT", "8080", 2),
                DotenvEntry("DATABASE_URL", "postgres://localhost:5432/app", 3),
                DotenvEntry("GREETING", "Hello, \${NAME}!", 4),
                DotenvEntry("DEBUG", "true", 5),
            )

        assertEquals(expected, parse("normal.env.example"))
    }

    @Test
    fun `blank and empty-quoted values are present with an empty value`() {
        val expected =
            listOf(
                DotenvEntry("KEY_BLANK", "", 1),
                DotenvEntry("KEY_BLANK_COMMENT", "", 2),
                DotenvEntry("KEY_DOUBLE_QUOTED", "", 3),
                DotenvEntry("KEY_SINGLE_QUOTED", "", 4),
            )

        assertEquals(expected, parse("empty-values.env.example"))
    }

    @Test
    fun `quoted values are literal, escaped, or trimmed of trailing comments as appropriate`() {
        val expected =
            listOf(
                DotenvEntry("SINGLE_LITERAL", "raw \$VAR and # not a comment", 1),
                DotenvEntry("SINGLE_PADDED", "  spaced value  ", 2),
                DotenvEntry("DOUBLE_ESCAPED", "line1\nline2 \"quoted\" and \\backslash", 3),
                DotenvEntry("DOUBLE_TRAILING", "value", 4),
            )

        assertEquals(expected, parse("quoted.env.example"))
    }

    @Test
    fun `full-line, indented, and inline comments are skipped or stripped, but mid-value hashes are kept`() {
        val expected =
            listOf(
                DotenvEntry("FOO", "bar", 3),
                DotenvEntry("URL", "https://example.com/path#fragment", 4),
            )

        assertEquals(expected, parse("commented.env.example"))
    }

    @Test
    fun `every malformed line is reported once, in line order`() {
        val exception =
            assertThrows(DeploymentInputException::class.java) {
                parse("malformed.env.example")
            }

        assertEquals("malformed.env.example", exception.sourceName)
        assertEquals(listOf(1, 2, 3, 4, 5, 7), exception.problems.map { it.line })
        assertEquals(
            listOf(
                "Missing '=' between key and value",
                "Invalid key '1INVALID'; keys must match [A-Za-z_][A-Za-z0-9_]*",
                "Unterminated single-quoted value",
                "Unsupported escape sequence '\\x' in double-quoted value",
                "Unexpected text after closing quote",
                "Duplicate key 'DUP_KEY' (first defined at line 6)",
            ),
            exception.problems.map { it.message },
        )
    }

    @Test
    fun `empty content produces an empty result`() {
        assertEquals(emptyList<DotenvEntry>(), DotenvParser.parse("empty.env.example", ""))
    }

    @Test
    fun `comments-only content produces an empty result`() {
        val content = "# just a comment\n   # indented comment\n"

        assertEquals(emptyList<DotenvEntry>(), DotenvParser.parse("comments-only.env.example", content))
    }

    @Test
    fun `LF, CRLF, and CR line endings produce identical results`() {
        val expected = listOf(DotenvEntry("FOO", "bar", 1), DotenvEntry("BAZ", "qux", 2))

        assertEquals(expected, DotenvParser.parse("lf.env.example", "FOO=bar\nBAZ=qux\n"))
        assertEquals(expected, DotenvParser.parse("crlf.env.example", "FOO=bar\r\nBAZ=qux\r\n"))
        assertEquals(expected, DotenvParser.parse("cr.env.example", "FOO=bar\rBAZ=qux\r"))
    }

    @Test
    fun `a UTF-8 BOM is stripped before parsing`() {
        val withoutBom = DotenvParser.parse("plain.env.example", "FOO=bar\n")
        val withBom = DotenvParser.parse("bom.env.example", "﻿FOO=bar\n")

        assertEquals(withoutBom, withBom)
    }

    private fun parse(fixtureName: String): List<DotenvEntry> = DotenvParser.parse(fixtureName, fixture(fixtureName))

    private fun fixture(name: String): String {
        val stream =
            requireNotNull(javaClass.getResourceAsStream("/dotenv/$name")) {
                "Missing test fixture: dotenv/$name"
            }
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
