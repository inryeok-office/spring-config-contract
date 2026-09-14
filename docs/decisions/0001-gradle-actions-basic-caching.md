# ADR-0001: Use the basic cache provider for Gradle Actions

- Status: Accepted
- Date: 2026-09-14

## Context

The CI workflow uses `gradle/actions/setup-gradle@v6`, adopted through Dependabot PR #5. Starting with v6.0.0, the action's caching functionality was extracted into `gradle-actions-caching`, a proprietary commercial component that is not covered by the action's MIT License and is governed by the [Gradle Terms of Use](https://gradle.com/legal/terms-of-use/). The v6.0.0 release notes state that upgrading to v6 accepts those terms for that component.

The `@v6` tag currently resolves to v6.3.0, whose `cache-provider` input accepts only these values:

- `enhanced` (default): uses the proprietary `gradle-actions-caching` component. It is free for public repositories and offered as a Free Preview for private repositories, with usage-based pricing planned for large commercial organizations.
- `basic` (since v6.1.0): an MIT-licensed provider built on `actions/cache`, free for all repositories. It does not support `cache-cleanup`.

Any other value fails the `setup-gradle` step. By default, both providers write cache entries only from jobs on the default branch; jobs on other branches read existing entries without saving.

Spring Config Contract is an Apache-2.0 project. CI tooling is not distributed with project artifacts, so the choice does not affect the project's license. However, the [dependency policy](../dependency-policy.md) requires license evaluation. Relying on the default provider would implicitly accept proprietary terms and expose the project's CI to future pricing or terms changes.

## Decision

Keep `gradle/actions/setup-gradle@v6`, and explicitly set `cache-provider: basic` in CI workflows that use it. The project does not use the proprietary `gradle-actions-caching` component.

Workflow permissions remain unchanged: `contents: read`.

## Consequences

- CI Gradle caching uses only MIT-licensed code, and no additional terms of use are accepted for caching.
- Caching stays free regardless of repository visibility or future Enhanced Caching pricing.
- Cache cleanup is unavailable, so Gradle User Home cache entries may be larger or less efficient than with Enhanced Caching. This is acceptable at the project's current size.
- New workflows that use `setup-gradle` must set `cache-provider: basic`, unless a later ADR supersedes this decision.
- Build Scan publishing is not enabled and remains subject to separate terms if adopted later.
- Dependabot updates to `gradle/actions` should be checked for changes to licensing or provider defaults.
