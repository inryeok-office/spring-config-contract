package io.github.inryeokoffice.configcontract.deployment.compose

/**
 * A single environment variable key found under `services.<name>.environment`.
 *
 * Parser-internal representation; it must never appear in core or in any
 * public signature. Only presence is tracked - Docker Compose environment
 * values are never retained, matching the core `ProvidedConfiguration` model.
 */
internal data class ComposeEntry(
    val key: String,
    val line: Int,
)
