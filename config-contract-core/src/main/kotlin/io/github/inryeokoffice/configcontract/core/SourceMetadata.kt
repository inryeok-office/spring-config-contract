package io.github.inryeokoffice.configcontract.core

/** Framework-neutral origin information suitable for diagnostics. */
data class SourceMetadata(
    val location: String,
) {
    init {
        require(location.isNotBlank()) { "Source location must not be blank" }
    }
}
