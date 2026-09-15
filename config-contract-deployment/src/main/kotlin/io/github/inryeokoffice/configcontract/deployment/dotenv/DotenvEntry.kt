package io.github.inryeokoffice.configcontract.deployment.dotenv

/**
 * A single successfully parsed dotenv line.
 *
 * Parser-internal representation; it must never appear in core or in any
 * public signature. [value] is retained only to validate syntax - the core
 * `ProvidedConfiguration` model tracks key presence, not values.
 */
internal data class DotenvEntry(
    val key: String,
    val value: String,
    val line: Int,
)
