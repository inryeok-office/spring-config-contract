# Roadmap

The roadmap is intentionally high-level. The v0.1 implementation is complete; publication is progressing through its final external approval and consumer-verification gates. Planned post-v0.1 behavior is not a claim that the feature is implemented.

## v0.1.0 implementation complete

The first usable release establishes the following end-to-end contract workflow:

- Core configuration contract model and deterministic comparison rules.
- Spring discovery for supported `@ConfigurationProperties`, `@Value`, and application configuration cases on the [tested compatibility line](compatibility.md).
- Deployment parsing for `.env.example` and Docker Compose environment values.
- Cross-module integration between Spring requirements and deployment-provided configuration.
- A Gradle integration with the `configContractCheck` task and CI-friendly output.
- End-to-end sample fixtures covering missing, unused, optional, and default-aware cases.
- A public Quick Start, usage guidance, limitations, and release-readiness review.

The completed scope is recorded in the [v0.1.0 milestone](https://github.com/inryeok-office/spring-config-contract/milestone/1), [release tracker Issue #26](https://github.com/inryeok-office/spring-config-contract/issues/26), and [release-readiness record Issue #25](https://github.com/inryeok-office/spring-config-contract/issues/25).

## Publication in progress

- The v0.1.0 runtime modules are publicly available from Maven Central.
- The initial Gradle Plugin Portal submission is awaiting first-publication approval. Public resolution and published-consumer verification remain required before the release is complete.
- [Issue #61](https://github.com/inryeok-office/spring-config-contract/issues/61) tracks the remaining release work. The [publication readiness guide](publication.md) defines the release sequence and partial-release recovery policy.

## Post-v0.1

The following are intentionally outside the first release boundary and will be evaluated after the core workflow is established:

- Kubernetes and Helm.
- Maven integration.
- GitHub PR reporting, dashboards, hosted services, and GitHub Apps.
- Broader Spring and build-tool compatibility matrices.
- Additional deployment adapters and advanced reporting.
