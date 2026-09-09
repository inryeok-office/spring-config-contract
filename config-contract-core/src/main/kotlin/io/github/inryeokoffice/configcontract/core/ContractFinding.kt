package io.github.inryeokoffice.configcontract.core

/** A deterministic result of comparing an application contract with provided keys. */
data class ContractFinding(
    val kind: Kind,
    val key: ConfigurationKey,
    val source: SourceMetadata?,
) {
    /** The mismatch identified by a comparison. */
    enum class Kind {
        MISSING,
        UNUSED,
    }
}
