# Testing

Tests verify behavior at the narrowest useful boundary. They must be deterministic and must not require external network access, private credentials, developer-specific paths, or mutable global machine state.

| Change type | Expected coverage |
| --- | --- |
| Core domain behavior | Fast unit tests for deterministic models and rules; avoid testing implementation details. |
| Parser behavior | Unit tests for valid, empty, malformed, and boundary inputs; use minimal fixtures. |
| Spring integration | Focused integration tests for supported Spring semantics, including relevant Java and Kotlin usage when applicable. |
| Gradle plugin integration | Gradle TestKit for meaningful plugin behavior and task outcomes; keep samples minimal. |
| Bug fix | A regression test whenever reasonably possible, reproducing the failure before the fix. |
| Documentation-only change | No code test is required, but run link, formatting, and any affected validation checks. |

Use fixture tests for representative input files and temporary directories for filesystem behavior. Do not hide product logic in `config-contract-test` helpers. Malformed or unsupported input should have explicit, tested behavior.

## Verification

Run the narrowest relevant test first, then the repository checks:

```bash
./gradlew test
./gradlew check
./gradlew build
```

`./gradlew verifyRepositoryRules` can be run directly when reviewing module boundaries or secret-file hygiene. It is also included in `check` and CI.

Formatting and lint checks are part of `check`. Never remove or weaken a test to resolve a failure; understand and report failures instead.
