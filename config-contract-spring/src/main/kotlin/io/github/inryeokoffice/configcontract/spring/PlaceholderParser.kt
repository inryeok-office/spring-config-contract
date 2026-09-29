package io.github.inryeokoffice.configcontract.spring

/** A `${key}` or `${key:default}` placeholder found in a `@Value` or configuration value. */
internal data class Placeholder(
    val key: String,
    /** The raw default text after the first top-level `:`, or `null` when there is none. */
    val defaultValue: String?,
) {
    /** Whether the default itself contains a placeholder, which v0.1 does not interpret. */
    val hasNestedDefault: Boolean
        get() = defaultValue != null && PlaceholderParser.containsPlaceholder(defaultValue)
}

/**
 * Finds top-level `${...}` placeholders using Spring Framework 6.2 syntax.
 *
 * The first top-level `:` separates the key from its default. Braces nest, so
 * `${a:{b}}` has the default `{b}`. A placeholder preceded by the `\` escape
 * character is literal text, and an unterminated `${` is literal text, as in
 * Spring. Placeholders nested inside a default are not expanded; callers see
 * them through [Placeholder.hasNestedDefault].
 */
internal object PlaceholderParser {
    private const val PREFIX = "\${"
    private const val ESCAPE = '\\'

    fun containsPlaceholder(text: String): Boolean = parse(text).isNotEmpty()

    fun parse(text: String): List<Placeholder> {
        val placeholders = mutableListOf<Placeholder>()
        var index = 0
        while (index < text.length) {
            val start = text.indexOf(PREFIX, index)
            if (start < 0) break
            if (start > 0 && text[start - 1] == ESCAPE) {
                index = start + PREFIX.length
                continue
            }
            val end = closingBrace(text, start + PREFIX.length) ?: break
            placeholders += split(text.substring(start + PREFIX.length, end))
            index = end + 1
        }
        return placeholders
    }

    private fun closingBrace(
        text: String,
        from: Int,
    ): Int? {
        var depth = 1
        for (index in from until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return index
            }
        }
        return null
    }

    private fun split(content: String): Placeholder {
        var depth = 0
        for ((index, char) in content.withIndex()) {
            when (char) {
                '{' -> depth++
                '}' -> depth--
                ':' -> if (depth == 0) return Placeholder(content.substring(0, index), content.substring(index + 1))
            }
        }
        return Placeholder(content, null)
    }
}
