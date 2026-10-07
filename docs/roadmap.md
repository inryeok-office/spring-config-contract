# Roadmap

The roadmap is intentionally high-level. v0.1 functionality is implemented on `main` and remains under release-candidate review; the plugin is not yet published. Planned post-v0.1 behavior is not a claim that the feature is implemented.

## v0.1.0

The first usable release establishes the following end-to-end contract workflow:

- Core configuration contract model and deterministic comparison rules.
- Spring Boot 3.5.x discovery for supported `@ConfigurationProperties`, `@Value`, and application configuration cases.
- Deployment parsing for `.env.example` and Docker Compose environment values.
- Cross-module integration between Spring requirements and deployment-provided configuration.
- A Gradle integration with the `configContractCheck` task and CI-friendly output.
- End-to-end sample fixtures covering missing, unused, optional, and default-aware cases.
- A public Quick Start, usage guidance, limitations, and release-candidate review.

The detailed work is tracked in the [v0.1.0 milestone](https://github.com/inryeok-office/spring-config-contract/milestone/1) and [release tracker Issue #26](https://github.com/inryeok-office/spring-config-contract/issues/26). The remaining release-review work is Issue #25.

## Post-v0.1

The following are intentionally outside the first release boundary and will be evaluated after the core workflow is established:

- Kubernetes and Helm.
- Maven integration.
- GitHub PR reporting, dashboards, hosted services, and GitHub Apps.
- Broader Spring and build-tool compatibility matrices.
- Additional deployment adapters and advanced reporting.
