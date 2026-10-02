# Changelog

All notable changes to this project will be documented here.

## Unreleased

- Added the initial multi-module Kotlin/Gradle project infrastructure.
- Added Spring Boot configuration discovery for `@ConfigurationProperties`, `@Value`, and application configuration files, plus Spring environment-variable key alignment.
- Added the `io.github.inryeok-office.config-contract` Gradle plugin with a `configContract` extension and a `configContractCheck` task that reports missing and unused configuration and fails the build on findings.
- Added end-to-end sample projects under `samples/` and Gradle TestKit tests that run `configContractCheck` against them offline, covering valid, missing, unused, optional and default-aware, and Kotlin binding scenarios.
