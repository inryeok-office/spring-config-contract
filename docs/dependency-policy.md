# Dependency policy

Before adding a dependency, determine whether JDK, Kotlin, Gradle, or an existing project capability is sufficient. A dependency must have an appropriate scope and be evaluated for maintenance health, license compatibility, transitive cost, public API leakage, and security implications.

Do not add a library solely to simplify a trivial amount of code. Keep `config-contract-core`'s dependency surface as small as possible. Keep integration-specific libraries in their owning modules and do not let them leak into core APIs.

Centralize versions in `gradle/libs.versions.toml` where practical. Dependency changes should be focused, justified in the PR, validated with `./gradlew check` and `./gradlew build`, and documented when they affect compatibility, licensing, or runtime behavior. Avoid drive-by upgrades.
