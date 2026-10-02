# Samples

Minimal projects that [`SampleProjectsFunctionalTest`](../config-contract-gradle-plugin/src/test/kotlin/io/github/inryeokoffice/configcontract/gradle/SampleProjectsFunctionalTest.kt) runs `configContractCheck` against. They exist to prove the v0.1 flow end to end: Spring requirements, `.env.example` and Docker Compose input, the Gradle task, and the report. They are test inputs, not applications and not part of any published artifact, and they contain no product logic.

## Not standalone

None of these projects builds on its own, by design:

- The projects declare no Spring dependency and no repository. The functional test copies a sample into a temporary directory and supplies the Spring jars already on its own classpath through a generated init script, then runs Gradle with `--offline`. Resolving anything remotely therefore fails instead of silently succeeding.
- `kotlin-binding` is further from standalone. Its Kotlin sources would need the Kotlin Gradle plugin, which comes from the Gradle Plugin Portal, and tests must not reach the network. The `config-contract-gradle-plugin` build compiles `kotlin-binding/src/main/kotlin` in a separate `kotlinSample` source set, and the test points `configContractCheck.applicationClassesDirs` at the result. That source set is not part of the plugin's `main` or `test` classpath and is not packaged in the plugin jar.

Do not run Gradle inside a sample directory.

## Scenarios

Each row is the actual output of the task, copied from a test run. Paths are relative to the project root. `deploy/*.env.example` and `deploy/compose.yml` lines are 1-based.

| Sample | Scenario | Finding category | Build | Report |
| --- | --- | --- | --- | --- |
| `valid` | Every required key is provided by `.env.example` or Compose | none | success | `Configuration contract check passed: no findings` |
| `missing-required` | A required `@Value` key is provided nowhere | `MISSING` | failed | `Configuration contract check failed: 1 finding (1 missing)`<br>`MISSING app.api-key (com.example.AppSettings.apiKey)` |
| `unused-configuration` | `.env.example` and Compose provide keys that nothing consumes | `UNUSED` | failed | `Configuration contract check failed: 2 findings (2 unused)`<br>`UNUSED  LEGACY_FLAG (deploy/.env.example:3)`<br>`UNUSED  OLD_ENDPOINT (deploy/compose.yml:4)` |
| `optional-and-default` | `@Value` defaults, a Java bean `@ConfigurationProperties`, and an `application.yml` key; deployment overrides two of them | none | success | `Configuration contract check passed: no findings` |
| `kotlin-binding` | Kotlin `@ConfigurationProperties` constructor: a non-null parameter without a default, a nullable one, a Kotlin default, and `@DefaultValue` | `MISSING` | failed | `Configuration contract check failed: 1 finding (1 missing)`<br>`MISSING app.mail.sender (com.example.MailProperties#sender)` |

v0.1 has two finding categories, `MISSING` and `UNUSED`. A failed build exits non-zero, which is what CI observes.

## Why one Kotlin sample

Java and Kotlin differ in what makes a `@ConfigurationProperties` property required. A Java property is never required. Only a Kotlin non-null constructor parameter without a Kotlin default or `@DefaultValue` is, so `kotlin-binding` is the only way to show a `MISSING` finding from that annotation and that nullable and default-backed parameters are not reported. `@Value` behaves the same in both languages, so every other sample is Java.

## Line endings

`.gitattributes` checks `samples/**` out with LF on every operating system, because expected locations contain line numbers. The test also covers a CRLF checkout.
