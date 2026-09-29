package io.github.inryeokoffice.configcontract.spring

/**
 * The decoded text of one Spring Boot application configuration file.
 *
 * [name] must be a project-relative path using `/` separators (for example
 * `src/main/resources/application-prod.yml`); it is used in diagnostics and
 * source metadata. Its last segment selects how the content is read and must
 * be `application.properties`, `application.yml`, `application.yaml`, or the
 * profile-specific `application-<profile>` form of one of them. Obtaining
 * [content] is the caller's responsibility; discovery performs no file I/O.
 *
 * Not a `data class`: [content] may contain secret-like values, and a
 * generated `toString()` would print it verbatim. Equality is therefore
 * reference identity.
 */
class ApplicationConfigurationSource(
    val name: String,
    val content: String,
) {
    init {
        require(name.isNotBlank()) { "Application configuration source name must not be blank" }
        require(name.none { it.isISOControl() }) {
            "Application configuration source name must not contain control characters"
        }
        require(!name.contains('\\')) {
            "Application configuration source name must use '/' as the path separator, but was: $name"
        }
        require(!ABSOLUTE_PATH.containsMatchIn(name)) {
            "Application configuration source name must be a project-relative path, but was: $name"
        }
        require(name.split('/').none { it == ".." }) {
            "Application configuration source name must not escape the project directory, but was: $name"
        }
    }

    /** The last path segment, such as `application-prod.yml`. */
    val fileName: String
        get() = name.substringAfterLast('/')

    override fun toString(): String = "ApplicationConfigurationSource(name=$name, content=<redacted>)"

    private companion object {
        val ABSOLUTE_PATH = Regex("""^(/|[A-Za-z]:)""")
    }
}
