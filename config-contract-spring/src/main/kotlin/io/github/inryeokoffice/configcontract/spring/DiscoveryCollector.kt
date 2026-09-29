package io.github.inryeokoffice.configcontract.spring

import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.DefaultValue
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.Presence
import io.github.inryeokoffice.configcontract.core.SourceMetadata

/** One place where a configuration key is consumed or defined, before keys are merged. */
internal data class KeyOccurrence(
    val key: String,
    val presence: Presence,
    val defaultValue: DefaultValue,
    val source: SourceMetadata,
    val origin: Origin,
) {
    enum class Origin {
        /** Consumed by application code: `@ConfigurationProperties` or `@Value`. */
        CODE,

        /** A key defined with a value by the effective application configuration. */
        APPLICATION_CONFIGURATION,

        /** A `${...}` placeholder inside an application configuration value. */
        CONFIGURATION_PLACEHOLDER,
    }
}

private val LINE_SUFFIX = Regex("""^(.*):(\d+)$""")

/** Orders locations by path and then numerically by a trailing `:line`, so `a.yml:3` precedes `a.yml:13`. */
internal val SOURCE_ORDER: Comparator<SourceMetadata> =
    compareBy<SourceMetadata> { pathAndLine(it).first }.thenBy { pathAndLine(it).second }

private fun pathAndLine(source: SourceMetadata): Pair<String, Int> {
    val match = LINE_SUFFIX.matchEntire(source.location) ?: return source.location to 0
    return match.groupValues[1] to (match.groupValues[2].toIntOrNull() ?: 0)
}

/** Mutable accumulator shared by the scanners of one discovery run. */
internal class DiscoveryCollector {
    val occurrences = mutableListOf<KeyOccurrence>()
    val diagnostics = mutableListOf<SpringDiscoveryDiagnostic>()
    val problems = mutableListOf<SpringDiscoveryProblem>()

    fun occurrence(occurrence: KeyOccurrence) {
        occurrences += occurrence
    }

    fun unsupported(
        location: String,
        message: String,
    ) {
        diagnostics += SpringDiscoveryDiagnostic(SpringDiscoveryDiagnostic.Kind.UNSUPPORTED, message, SourceMetadata(location))
    }

    fun nonCanonical(
        location: String,
        message: String,
    ) {
        diagnostics += SpringDiscoveryDiagnostic(SpringDiscoveryDiagnostic.Kind.NON_CANONICAL_KEY, message, SourceMetadata(location))
    }

    fun problem(
        location: String,
        message: String,
    ) {
        problems += SpringDiscoveryProblem(message, SourceMetadata(location))
    }
}
