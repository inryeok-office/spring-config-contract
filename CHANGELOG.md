# Changelog

All notable changes to this project will be documented here.

## Unreleased

No changes yet.

## v0.1.0 release candidate (draft)

- Introduced the framework-independent configuration contract model and deterministic `MISSING` and `UNUSED` comparison findings.
- Added Spring discovery for the supported `@ConfigurationProperties`, `@Value`, and application configuration inputs, including Spring environment-variable key alignment.
- Added `.env.example` and Docker Compose `services.<name>.environment` deployment inputs.
- Added the unpublished `io.github.inryeok-office.config-contract` Gradle plugin with `configContract` and `configContractCheck`; findings fail the task with a non-zero outcome.
- Added offline end-to-end samples for valid, missing, unused, optional/default, and Kotlin binding scenarios.
- Built and tested Spring discovery against Spring Boot 3.5.x on JDK 21. Other Spring Boot 3.x lines and Spring Boot 4.x are not claimed.
- Known limitations include treating every Compose service environment as application-provided, skipping nested/collection/map `@ConfigurationProperties`, and requiring CI to invoke `configContractCheck` explicitly.
- The plugin is not published and this draft does not announce a release.
