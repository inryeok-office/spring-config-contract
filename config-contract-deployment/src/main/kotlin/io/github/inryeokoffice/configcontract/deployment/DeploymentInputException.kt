package io.github.inryeokoffice.configcontract.deployment

/** A single problem found while reading a [DeploymentSource], anchored to its 1-based line. */
data class InputProblem(
    val line: Int,
    val message: String,
)

/**
 * Thrown when a [DeploymentSource] cannot be read.
 *
 * Adapters collect every problem found while parsing, in line order, and
 * throw once rather than failing on the first problem encountered.
 */
class DeploymentInputException(
    val sourceName: String,
    val problems: List<InputProblem>,
) : RuntimeException(formatMessage(sourceName, problems)) {
    init {
        require(problems.isNotEmpty()) { "DeploymentInputException requires at least one problem" }
    }

    companion object {
        private fun formatMessage(
            sourceName: String,
            problems: List<InputProblem>,
        ): String =
            problems.joinToString(prefix = "$sourceName:\n", separator = "\n") { problem ->
                "  line ${problem.line}: ${problem.message}"
            }
    }
}
