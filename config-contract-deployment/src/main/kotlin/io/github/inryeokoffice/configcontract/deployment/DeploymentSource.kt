package io.github.inryeokoffice.configcontract.deployment

/**
 * Raw deployment configuration content to be read by a [DeploymentInputAdapter].
 *
 * [name] must be a project-relative path suitable for inclusion in findings
 * (for example `config/.env.example`), never an absolute, developer-specific
 * filesystem path. This is enforced by the constructor.
 * Obtaining [content] is the caller's responsibility;
 * adapters must not perform file I/O themselves.
 *
 * Not a `data class`: [content] may hold an entire `.env` file, including
 * secret-like values, and a generated `toString()` would print it verbatim;
 * do not revert this back to a `data class` without redacting [content].
 * As a consequence, `equals`/`hashCode` are reference identity, not structural
 * equality - two instances built from the same [name] and [content] are not
 * `equal`. Callers relying on value equality (collections, `assertEquals`)
 * must compare [name]/[content] directly instead.
 */
class DeploymentSource(
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

    override fun toString(): String = "DeploymentSource(name=$name, content=<redacted>)"

    private companion object {
        val ABSOLUTE_PATH = Regex("""^(/|[A-Za-z]:)""")
    }
}
