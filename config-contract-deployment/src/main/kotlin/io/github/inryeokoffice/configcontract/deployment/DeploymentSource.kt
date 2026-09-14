package io.github.inryeokoffice.configcontract.deployment

/**
 * Raw deployment configuration content to be read by a [DeploymentInputAdapter].
 *
 * [name] must be a project-relative path suitable for inclusion in findings
 * (for example `config/.env.example`), never an absolute, developer-specific
 * filesystem path. This is enforced by the constructor.
 * Obtaining [content] is the caller's responsibility;
 * adapters must not perform file I/O themselves.
 */
data class DeploymentSource(
    val name: String,
    val content: String,
) {
    init {
        require(name.isNotBlank()) { "Deployment source name must not be blank" }
        require(name.none { it.isISOControl() }) {
            "Deployment source name must not contain control characters"
        }
        require(!name.contains('\\')) {
            "Deployment source name must use '/' as the path separator, but was: $name"
        }
        require(!ABSOLUTE_PATH.containsMatchIn(name)) {
            "Deployment source name must be a project-relative path, but was: $name"
        }
        require(name.split('/').none { it == ".." }) {
            "Deployment source name must not escape the project directory, but was: $name"
        }
    }

    private companion object {
        val ABSOLUTE_PATH = Regex("""^(/|[A-Za-z]:)""")
    }
}
