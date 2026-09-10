package io.github.inryeokoffice.configcontract.deployment

/**
 * Raw deployment configuration content to be read by a [DeploymentInputAdapter].
 *
 * [name] must be a project-relative path suitable for inclusion in findings
 * (for example `config/.env.example`), never an absolute, developer-specific
 * filesystem path. Obtaining [content] is the caller's responsibility;
 * adapters must not perform file I/O themselves.
 */
data class DeploymentSource(
    val name: String,
    val content: String,
) {
    init {
        require(name.isNotBlank()) { "Deployment source name must not be blank" }
    }
}
