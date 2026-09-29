package io.github.inryeokoffice.configcontract.spring

import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement
import io.github.inryeokoffice.configcontract.core.SourceMetadata

/**
 * Requirements discovered from a Spring Boot application.
 *
 * [requirements] have unique keys and are sorted by key. [diagnostics] report
 * input that discovery recognized but does not interpret in v0.1, sorted by
 * source location, kind, and message. A diagnostic never silently changes a
 * requirement; it explains why something was skipped or only partially
 * interpreted.
 */
data class SpringDiscoveryResult(
    val requirements: List<ConfigurationRequirement>,
    val diagnostics: List<SpringDiscoveryDiagnostic>,
)

/** A non-fatal explanation of discovery behavior for one source location. */
data class SpringDiscoveryDiagnostic(
    val kind: Kind,
    val message: String,
    val source: SourceMetadata,
) {
    /** The reason for a diagnostic. */
    enum class Kind {
        /** A Spring feature that v0.1 discovery does not interpret; any affected key is skipped or approximated as described. */
        UNSUPPORTED,

        /** A placeholder name that is not in canonical form, so Spring's relaxed binding does not apply to it. */
        NON_CANONICAL_KEY,
    }
}

/** A problem that makes the discovery input invalid, anchored to its source. */
data class SpringDiscoveryProblem(
    val message: String,
    val source: SourceMetadata,
)

/**
 * Thrown when discovery input is malformed, for example invalid YAML or a
 * `@ConfigurationProperties` prefix that Spring Boot itself would reject.
 *
 * Every problem is collected before throwing, ordered by source location.
 */
class SpringDiscoveryException(
    val problems: List<SpringDiscoveryProblem>,
) : RuntimeException(formatMessage(problems)) {
    init {
        require(problems.isNotEmpty()) { "SpringDiscoveryException requires at least one problem" }
    }

    private companion object {
        fun formatMessage(problems: List<SpringDiscoveryProblem>): String =
            problems.joinToString(prefix = "Spring configuration discovery failed:\n", separator = "\n") {
                "  ${it.source.location}: ${it.message}"
            }
    }
}
