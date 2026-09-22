package io.github.inryeokoffice.configcontract.deployment

/**
 * Shared key syntax rules for deployment formats that expose environment-style keys.
 *
 * Used by both the dotenv and Docker Compose parsers so that the accepted key
 * shape and its error wording stay identical across formats.
 */
internal object KeySyntax {
    private val PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /** Human-readable description of [PATTERN], for use in problem messages. */
    const val DESCRIPTION = "[A-Za-z_][A-Za-z0-9_]*"

    fun isValid(key: String): Boolean = PATTERN.matches(key)
}
