package io.github.inryeokoffice.configcontract.gradle

import io.github.inryeokoffice.configcontract.core.ContractFinding

/** Formats a contract result as stable, line-oriented console text. */
internal object ContractReport {
    private val KIND_WIDTH = ContractFinding.Kind.entries.maxOf { it.name.length }

    /** Keeps the order already defined by core and Spring discovery; adds no timestamps or absolute paths. */
    fun format(result: SpringDeploymentContractResult): String =
        buildString {
            val findings = result.findings
            if (findings.isEmpty()) {
                append("Configuration contract check passed: no findings")
            } else {
                val counts =
                    ContractFinding.Kind.entries
                        .map { kind -> kind to findings.count { it.kind == kind } }
                        .filter { (_, count) -> count > 0 }
                        .joinToString { (kind, count) -> "$count ${kind.name.lowercase()}" }
                append("Configuration contract check failed: ${findings.size} ${plural(findings.size, "finding")} ($counts)")
                findings.forEach { finding ->
                    append("\n  ${finding.kind.name.padEnd(KIND_WIDTH)} ${finding.key} (${finding.source?.location ?: "unknown source"})")
                }
            }
            if (result.diagnostics.isNotEmpty()) {
                append("\nDiagnostics:")
                result.diagnostics.forEach { diagnostic ->
                    append("\n  ${diagnostic.kind.name} ${diagnostic.source.location}: ${diagnostic.message}")
                }
            }
        }

    private fun plural(
        count: Int,
        noun: String,
    ): String = if (count == 1) noun else "${noun}s"
}
