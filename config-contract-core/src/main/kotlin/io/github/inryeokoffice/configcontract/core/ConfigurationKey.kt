package io.github.inryeokoffice.configcontract.core

/**
 * A validated, framework-neutral configuration key.
 *
 * The core deliberately does not normalize keys. Integration modules own
 * format-specific normalization, such as Spring relaxed binding rules.
 */
class ConfigurationKey private constructor(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Configuration key must not be blank" }
        require(value == value.trim()) { "Configuration key must not have surrounding whitespace" }
        require(value.none(Char::isWhitespace)) { "Configuration key must not contain whitespace" }
        require(value.none(Char::isISOControl)) { "Configuration key must not contain control characters" }
    }

    override fun equals(other: Any?): Boolean = other is ConfigurationKey && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        /** Creates a key or throws [IllegalArgumentException] for invalid input. */
        @JvmStatic
        fun of(value: String): ConfigurationKey = ConfigurationKey(value)
    }
}
