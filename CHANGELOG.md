# Changelog

All notable changes to this project will be documented here.

## Unreleased

### v0.1.0 release preparation

- Added the initial multi-module Kotlin/Gradle project infrastructure.
- Introduced the framework-independent configuration contract model and deterministic `MISSING` and `UNUSED` comparison findings.
- Added Spring discovery for the supported `@ConfigurationProperties`, `@Value`, and application configuration inputs, including Spring environment-variable key alignment.
- Added `.env.example` and Docker Compose `services.<name>.environment` deployment inputs.
- Added the `io.github.inryeok-office.config-contract` Gradle plugin with `configContract` and `configContractCheck`; findings fail the task with a non-zero outcome.
- Added offline end-to-end samples for valid, missing, unused, optional/default, and Kotlin binding scenarios.
- Built and tested Spring discovery against Spring Boot 3.5.x on JDK 21; see [compatibility](docs/compatibility.md). Other Spring Boot lines are not claimed.
- Known limitations include treating every Compose service environment as application-provided, skipping nested/collection/map `@ConfigurationProperties`, and requiring CI to invoke `configContractCheck` explicitly.
- Published the v0.1.0 runtime modules (`config-contract-core`, `config-contract-spring`, and `config-contract-deployment`) to Maven Central.
- Submitted the initial v0.1.0 Gradle Plugin Portal publication for approval. Public Plugin Portal resolution and published-consumer verification are still pending, so this changelog does not announce a completed release.
- The [publication readiness guide](docs/publication.md) records the artifact topology, release gates, and recovery policy. The release remains tracked by [Issue #61](https://github.com/inryeok-office/spring-config-contract/issues/61).
- Added a credential-free local publication pipeline that validates Maven/Gradle metadata, Dokka and source artifacts,
  conditional ephemeral signing, and offline consumer resolution through separate Central-like, Portal-like, and
  third-party repositories. This does not publish any production artifact.
