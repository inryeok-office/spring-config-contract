package io.github.inryeokoffice.configcontract.core

/**
 * A configuration key supplied by an external environment.
 *
 * The v0.1 domain model tracks presence, not secret-bearing values. Parsers
 * may retain values in their own layer when needed, but comparison does not
 * require exposing them through the core API.
 */
class ProvidedConfiguration
    @JvmOverloads
    constructor(
        val key: ConfigurationKey,
        val source: SourceMetadata? = null,
    ) {
        /** Source is diagnostic metadata; key identity is independent of origin. */
        override fun equals(other: Any?): Boolean = other is ProvidedConfiguration && key == other.key

        override fun hashCode(): Int = key.hashCode()

        override fun toString(): String = "ProvidedConfiguration(key=$key, source=$source)"
    }
