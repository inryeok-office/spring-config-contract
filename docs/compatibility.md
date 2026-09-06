# Compatibility

Compatibility claims follow the Gradle build, version catalog, and CI configuration. The initial target is JDK 21 and Spring Boot 3.x compatibility as planned; Spring dependencies and supported Spring versions will be declared when the Spring integration is implemented.

- JDK: develop and test with the Java 21 toolchain configured by Gradle. Other local JVMs are not a supported project target unless CI and the build are updated.
- Kotlin: use the Kotlin version in `gradle/libs.versions.toml`; do not assume compatibility with arbitrary compiler versions.
- Gradle: use the checked-in Wrapper version. Wrapper upgrades require validation of all checks and CI.
- Spring Boot: Spring Boot 3.x is the initial target, not a claim that Spring behavior is implemented today.
- 0.x releases: APIs and behavior may change between minor releases, but changes should still be deliberate, documented, tested, and reviewed.
- Unsupported environments: do not infer support for other JDK, Spring, Gradle, operating-system, or deployment-format versions without evidence.

Compatibility expansions should be introduced through an Issue, explicit tests/CI coverage where practical, documentation updates, and an ADR when the decision changes architecture or support policy.
