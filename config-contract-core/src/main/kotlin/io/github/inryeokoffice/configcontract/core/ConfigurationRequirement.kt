package io.github.inryeokoffice.configcontract.core

/**
 * A configuration key that an application may require from its environment.
 *
 * Profile semantics are intentionally not modeled here. Spring-specific
 * profile resolution will be decided by the Spring integration after its
 * behavior has been validated.
 */
data class ConfigurationRequirement
    @JvmOverloads
    constructor(
        val key: ConfigurationKey,
        val presence: Presence = Presence.REQUIRED,
        val defaultValue: DefaultValue = DefaultValue.Absent,
        val source: SourceMetadata? = null,
    ) {
        /** Whether the application requires the key or can operate without it. */
        enum class Presence {
            REQUIRED,
            OPTIONAL,
        }

        /**
         * A distinguishable default state; [Present] also permits an empty value.
         *
         * A required requirement may have a default. In that case the default is
         * the fallback that satisfies the requirement when the provided key is
         * absent; [presence] still describes the requirement when no fallback is
         * available.
         */
        sealed interface DefaultValue {
            data object Absent : DefaultValue

            data class Present(
                val value: String,
            ) : DefaultValue
        }
    }
