# Compatibility

Compatibility claims follow the Gradle build, version catalog, and CI configuration. Spring dependencies are declared by `config-contract-spring`.

- JDK: develop and test with the Java 21 toolchain configured by Gradle. Other local JVMs are not a supported project target unless CI and the build are updated.
- Kotlin: use the Kotlin version in `gradle/libs.versions.toml`; do not assume compatibility with arbitrary compiler versions.
- Gradle: use the checked-in Gradle 9.8.1 Wrapper. Wrapper upgrades require validation of all checks and CI.
- Spring Boot: `config-contract-spring` discovery is built and tested against Spring Boot `3.5.16`, the version in `gradle/libs.versions.toml` for v0.1.0. The release notes describe this as the 3.5.x compatibility line; other Spring Boot lines are not claimed, and other 3.5 patch versions have not been verified. See [ADR-0002](decisions/0002-spring-discovery-through-spring-boot-apis.md).
- 0.x releases: APIs and behavior may change between minor releases, but changes should still be deliberate, documented, tested, and reviewed.
- Unsupported environments: do not infer support for other JDK, Spring, Gradle, operating-system, or deployment-format versions without evidence.

Compatibility expansions should be introduced through an Issue, explicit tests/CI coverage where practical, documentation updates, and an ADR when the decision changes architecture or support policy.
