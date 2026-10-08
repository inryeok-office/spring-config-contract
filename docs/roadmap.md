# Roadmap

The roadmap is intentionally high-level. v0.1 functionality is implemented on `main`, but the plugin is not yet published. Planned post-v0.1 behavior is not a claim that the feature is implemented.

## v0.1.0

The first usable release establishes the following end-to-end contract workflow:

- Core configuration contract model and deterministic comparison rules.
- Spring discovery for supported `@ConfigurationProperties`, `@Value`, and application configuration cases on the [tested compatibility line](compatibility.md).
- Deployment parsing for `.env.example` and Docker Compose environment values.
- Cross-module integration between Spring requirements and deployment-provided configuration.
- A Gradle integration with the `configContractCheck` task and CI-friendly output.
- End-to-end sample fixtures covering missing, unused, optional, and default-aware cases.
- A public Quick Start, usage guidance, limitations, and release-readiness review.

The completed scope is recorded in the [v0.1.0 milestone](https://github.com/inryeok-office/spring-config-contract/milestone/1), [release tracker Issue #26](https://github.com/inryeok-office/spring-config-contract/issues/26), and [release-readiness record Issue #25](https://github.com/inryeok-office/spring-config-contract/issues/25). The [publication readiness guide](publication.md) defines the future artifact topology; implementation is tracked separately in [Issue #55](https://github.com/inryeok-office/spring-config-contract/issues/55), and publishing remains a maintainer decision.

## Post-v0.1

The following are intentionally outside the first release boundary and will be evaluated after the core workflow is established:

- Kubernetes and Helm.
- Maven integration.
- GitHub PR reporting, dashboards, hosted services, and GitHub Apps.
- Broader Spring and build-tool compatibility matrices.
- Additional deployment adapters and advanced reporting.
