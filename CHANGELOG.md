# Changelog

All notable changes to this project will be documented here.

## Unreleased

## v0.1.0 - 2026-10-18

- Added the initial multi-module Kotlin/Gradle project infrastructure.
- Introduced the framework-independent configuration contract model and deterministic `MISSING` and `UNUSED` comparison findings.
- Added Spring discovery for the supported `@ConfigurationProperties`, `@Value`, and application configuration inputs, including Spring environment-variable key alignment.
- Added `.env.example` and Docker Compose `services.<name>.environment` deployment inputs.
- Added the `io.github.inryeok-office.config-contract` Gradle plugin with `configContract` and `configContractCheck`; findings fail the task with a non-zero outcome.
- Added offline end-to-end samples for valid, missing, unused, optional/default, and Kotlin binding scenarios.
- Built and tested Spring discovery against Spring Boot 3.5.16 on JDK 21; see [compatibility](docs/compatibility.md). Other Spring Boot versions are not claimed.
- Known limitations include treating every Compose service environment as application-provided, skipping nested/collection/map `@ConfigurationProperties`, and requiring CI to invoke `configContractCheck` explicitly.
- Published the v0.1.0 runtime modules (`config-contract-core`, `config-contract-spring`, and `config-contract-deployment`) to Maven Central.
- Published `io.github.inryeok-office.config-contract` version `0.1.0` to the Gradle Plugin Portal.
- Verified published consumers through both the default Plugin Portal path and the explicit Plugin Portal plus Maven Central mirror path.
- Released [Spring Config Contract v0.1.0](https://github.com/inryeok-office/spring-config-contract/releases/tag/v0.1.0).
- The [publication readiness guide](docs/publication.md) records the artifact topology, release gates, and recovery policy.
- Added a credential-free local publication pipeline that validates Maven/Gradle metadata, Dokka and source artifacts,
  conditional ephemeral signing, and offline consumer resolution through separate Central-like, Portal-like, and
  third-party repositories. This does not publish any production artifact.
