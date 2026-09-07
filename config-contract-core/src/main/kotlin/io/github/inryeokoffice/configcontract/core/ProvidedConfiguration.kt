package io.github.inryeokoffice.configcontract.core

/**
 * A configuration key supplied by an external environment.
 *
 * The v0.1 domain model tracks presence, not secret-bearing values. Parsers
 * may retain values in their own layer when needed, but comparison does not
 * require exposing them through the core API.
 */
data class ProvidedConfiguration(
    val key: ConfigurationKey,
    val source: SourceMetadata? = null,
)
