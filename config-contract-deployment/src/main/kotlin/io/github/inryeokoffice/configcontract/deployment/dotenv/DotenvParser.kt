package io.github.inryeokoffice.configcontract.deployment.dotenv

import io.github.inryeokoffice.configcontract.deployment.DeploymentInputException
import io.github.inryeokoffice.configcontract.deployment.InputProblem

/**
 * Parses `.env.example` syntax into [DotenvEntry] values.
 *
 * Deterministic and pure: no file I/O, host environment access, shell
 * execution, network access, or `${VAR}` / `$VAR` expansion. Parser-internal;
 * it must never appear in core or in any public signature.
 */
internal object DotenvParser {
    private val KEY_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val LINE_BREAK = Regex("\r\n|\r|\n")
    private const val BOM = "\uFEFF"

    /** @throws DeploymentInputException if [content] contains any problem, reported in line order. */
    fun parse(
        sourceName: String,
        content: String,
    ): List<DotenvEntry> {
        val problems = mutableListOf<InputProblem>()
        val entries = mutableListOf<DotenvEntry>()
        val firstLineByKey = mutableMapOf<String, Int>()

        content.removePrefix(BOM).split(LINE_BREAK).forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val trimmedStart = rawLine.trimStart()
            if (trimmedStart.isEmpty() || trimmedStart.startsWith("#")) {
                return@forEachIndexed
            }

            val declaration = stripExportPrefix(trimmedStart)
            val equalsIndex = declaration.indexOf('=')
            if (equalsIndex < 0) {
                problems += InputProblem(lineNumber, "Missing '=' between key and value")
                return@forEachIndexed
            }

            val key = declaration.substring(0, equalsIndex).trim()
            if (!KEY_PATTERN.matches(key)) {
                problems += InputProblem(lineNumber, "Invalid key '$key'; keys must match [A-Za-z_][A-Za-z0-9_]*")
                return@forEachIndexed
            }

            val value = parseValue(declaration.substring(equalsIndex + 1), lineNumber, problems) ?: return@forEachIndexed

            val firstLine = firstLineByKey[key]
            if (firstLine != null) {
                problems += InputProblem(lineNumber, "Duplicate key '$key' (first defined at line $firstLine)")
                return@forEachIndexed
            }

            firstLineByKey[key] = lineNumber
            entries += DotenvEntry(key, value, lineNumber)
        }

        if (problems.isNotEmpty()) {
            throw DeploymentInputException(sourceName, problems.sortedBy { it.line })
        }
        return entries
    }

    private fun stripExportPrefix(line: String): String {
        if (!line.startsWith("export")) return line
        val rest = line.removePrefix("export")
        if (rest.isEmpty() || !rest[0].isWhitespace()) return line
        return rest.trimStart()
    }

    private fun parseValue(
        rawValue: String,
        lineNumber: Int,
        problems: MutableList<InputProblem>,
    ): String? {
        val leadingTrimmed = rawValue.trimStart()
        return when {
            leadingTrimmed.startsWith("'") -> parseSingleQuoted(leadingTrimmed, lineNumber, problems)
            leadingTrimmed.startsWith("\"") -> parseDoubleQuoted(leadingTrimmed, lineNumber, problems)
            else -> parseUnquoted(rawValue)
        }
    }

    /** `#` starts an inline comment only when preceded by whitespace; the result is trimmed. */
    private fun parseUnquoted(rawValue: String): String {
        var commentStart = -1
        for (i in rawValue.indices) {
            if (rawValue[i] == '#' && i > 0 && rawValue[i - 1].isWhitespace()) {
                commentStart = i
                break
            }
        }
        val withoutComment = if (commentStart >= 0) rawValue.substring(0, commentStart) else rawValue
        return withoutComment.trim()
    }

    /** [fromQuote] starts with the opening `'`. Fully literal; no escapes; must close on the same line. */
    private fun parseSingleQuoted(
        fromQuote: String,
        lineNumber: Int,
        problems: MutableList<InputProblem>,
    ): String? {
        val closingIndex = fromQuote.indexOf('\'', startIndex = 1)
        if (closingIndex < 0) {
            problems += InputProblem(lineNumber, "Unterminated single-quoted value")
            return null
        }
        val remainder = fromQuote.substring(closingIndex + 1)
        if (!isCommentOrBlank(remainder)) {
            problems += InputProblem(lineNumber, "Unexpected text after closing quote")
            return null
        }
        return fromQuote.substring(1, closingIndex)
    }

    /** [fromQuote] starts with the opening `"`. Supports only `\"`, `\\`, `\n`; must close on the same line. */
    private fun parseDoubleQuoted(
        fromQuote: String,
        lineNumber: Int,
        problems: MutableList<InputProblem>,
    ): String? {
        val value = StringBuilder()
        var i = 1
        while (i < fromQuote.length) {
            when (val c = fromQuote[i]) {
                '"' -> {
                    val remainder = fromQuote.substring(i + 1)
                    if (!isCommentOrBlank(remainder)) {
                        problems += InputProblem(lineNumber, "Unexpected text after closing quote")
                        return null
                    }
                    return value.toString()
                }
                '\\' -> {
                    if (i + 1 >= fromQuote.length) {
                        problems += InputProblem(lineNumber, "Unterminated double-quoted value")
                        return null
                    }
                    when (val escaped = fromQuote[i + 1]) {
                        '"' -> value.append('"')
                        '\\' -> value.append('\\')
                        'n' -> value.append('\n')
                        else -> {
                            val message = "Unsupported escape sequence '\\$escaped' in double-quoted value"
                            problems += InputProblem(lineNumber, message)
                            return null
                        }
                    }
                    i += 2
                    continue
                }
                else -> value.append(c)
            }
            i += 1
        }
        problems += InputProblem(lineNumber, "Unterminated double-quoted value")
        return null
    }

    private fun isCommentOrBlank(remainder: String): Boolean {
        val trimmed = remainder.trimStart()
        return trimmed.isEmpty() || trimmed.startsWith("#")
    }
}
