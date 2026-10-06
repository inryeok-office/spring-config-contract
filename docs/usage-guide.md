# Usage guide

This guide describes how to use the v0.1 configuration contract workflow: a Gradle task that compares the configuration a Spring Boot application requires with the configuration its deployment files provide. It documents only behavior that is implemented and covered by the [sample projects](../samples/README.md). The Gradle plugin is not published, so it can be used only from a source checkout of this repository.

## What the check does

`configContractCheck` reads two sides of a contract:

- **Requirements** that Spring Boot code and application configuration declare: `@ConfigurationProperties`, `@Value`, and `application*.properties|yml|yaml`.
- **Provided configuration** that deployment files supply: `.env.example` files and the `environment` entries of Docker Compose services.

It reports a `MISSING` finding for a required key that deployment does not provide, and an `UNUSED` finding for a provided key that nothing in the application recognizes. It compares key presence only; values are never compared and never appear in the output.

## Quick Start

### A. Verify from a clean checkout

This is how the v0.1 flow is verified. The command runs `configContractCheck` against every project under `samples/` and asserts the exact report of each. The samples are copied into temporary directories and built with `--offline`, so they do not need network access themselves. They are deliberately not buildable in place; see [samples/README.md](../samples/README.md) for why.

Requirements: JDK 21 (see [compatibility](compatibility.md)). The first run downloads the repository's own build dependencies.

```bash
git clone https://github.com/inryeok-office/spring-config-contract.git
cd spring-config-contract
./gradlew :config-contract-gradle-plugin:test --tests "io.github.inryeokoffice.configcontract.gradle.SampleProjectsFunctionalTest"
```

On Windows, use `gradlew.bat`. The build ends with `BUILD SUCCESSFUL` when every sample produces the report documented in [samples/README.md](../samples/README.md).

### B. Try it on your own Spring project

The plugin is applied by id, without a version, by including this repository as a build in your project's `settings.gradle.kts`.

Requirements:

- JDK 21 and Spring Boot 3.5.x, the targets listed in [compatibility](compatibility.md). Other versions are not claimed.
- Network access, to resolve your project's Spring dependencies from the repository you declare, and the plugin's own dependencies when the included build is compiled.
- Run Gradle with the Wrapper of the checkout you include. Other Gradle versions are not claimed.

`settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("/path/to/spring-config-contract")
}

rootProject.name = "my-application"
```

On Windows, use a forward-slash absolute path such as `C:/path/to/spring-config-contract`. Git Bash style `/c/...` paths are not resolved by Gradle.

`build.gradle.kts`:

```kotlin
plugins {
    java
    id("io.github.inryeok-office.config-contract")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot:3.5.16")
}

configContract {
    activeProfiles.set(listOf("prod"))        // optional; empty analyzes the default profile
    dotenvExampleFiles.from("deploy/.env.example")   // optional
    composeFiles.from("deploy/compose.yml")          // optional
    // applicationConfigurationFiles defaults to the top-level application*.properties|yml|yaml files in main resources
}
```

Run the task explicitly from your project directory. It is not attached to `check`:

```bash
/path/to/spring-config-contract/gradlew configContractCheck
```

To see the task work without your own project, copy `samples/missing-required` out of the repository. In the copy, add the `pluginManagement` block from the snippet above to `settings.gradle.kts`, and add the `repositories` and `dependencies` blocks to `build.gradle.kts`. Then run the command above in the copy. The task fails and Gradle prints:

```text
* What went wrong:
Execution failed for task ':configContractCheck' (registered by plugin 'io.github.inryeok-office.config-contract').
> Configuration contract check failed: 1 finding (1 missing)
    MISSING app.api-key (com.example.AppSettings.apiKey)
```

The same report appears in the [example](#end-to-end-example) below.

## Configuration

| Property | Meaning |
| --- | --- |
| `activeProfiles` | Spring profiles to analyze, in `spring.profiles.active` order; later profiles take precedence. Empty analyzes the `default` profile. Profile names may contain letters, digits, `.`, `_`, and `-`. |
| `dotenvExampleFiles` | `.env.example` files that provide configuration. |
| `composeFiles` | Docker Compose files whose `services.<name>.environment` entries provide configuration. |
| `applicationConfigurationFiles` | Application configuration files to analyze. Defaults to the top-level `application.*` and `application-*.*` files with a `properties`, `yml`, or `yaml` extension in the `main` resource directories. |

Deployment and application configuration files must resolve inside the root project. Reported paths are relative to the root project, so output does not contain machine-specific paths. Once the `java` plugin is applied, the task analyzes the compiled classes of the `main` source set.

## Supported sources

### Spring sources

| Source | What it contributes |
| --- | --- |
| `@ConfigurationProperties` on a class (Java bean, constructor binding, Kotlin) | One key per single-value property, named `<prefix>.<dashed-property-name>`. A property is required only for a Kotlin non-null constructor parameter that has no Kotlin default and no `@DefaultValue`. Every other property is optional. A single-value `@DefaultValue` is recorded as the default. |
| `@Value` on a field, method, constructor parameter, or method parameter | `${key}` is a required key. `${key:default}` is an optional key with that default, including an empty default. |
| `application.properties`, `application.yml`, `application.yaml`, and `application-<profile>.*` | Each effective key is an optional key whose default is the configured value, so deployment can override it without being reported as unused. A `${NAME}` or `${NAME:default}` placeholder inside a value is a requirement, as with `@Value`. |

Single-value property types are `String`, primitives and their wrappers, enums, `BigDecimal`, `BigInteger`, `Duration`, `Period`, `Charset`, `Locale`, `UUID`, `URI`, `URL`, and `DataSize`.

Deployment keys are matched to requirement keys with Spring Boot's environment-variable rules, so `APP_API_KEY` provides `app.api-key`. A deployment key that matches no requirement keeps its own name and is reported as unused.

### Deployment sources

| Source | What it contributes |
| --- | --- |
| `.env.example` | One provided key per `KEY=value` line. Comments, blank lines, an `export` prefix, and single- or double-quoted values are accepted. |
| Docker Compose `services.<name>.environment` | One provided key per entry, in list form (`- KEY=value`, `- KEY=`, `- KEY`) or map form (`KEY: value`, `KEY:`). A key without a value still counts as provided. |

Only key names and their line numbers are read from deployment files. Values are never exposed, and `$VAR` or `${VAR}` in a value is not expanded. All other Compose fields are ignored.

## Findings

v0.1 has two finding kinds. There is no severity level.

| Finding | Fires when |
| --- | --- |
| `MISSING` | A requirement is required, has no known default, and no deployment file provides its key. |
| `UNUSED` | A deployment file provides a key that no requirement recognizes. |

Required, optional, and default behavior:

| Requirement | Result when deployment does not provide the key |
| --- | --- |
| Required, no default (`@Value("${key}")`, Kotlin non-null constructor parameter without a default) | `MISSING` |
| Optional (`@Value("${key:default}")`, `@Value("${key:}")`, Java `@ConfigurationProperties` property, nullable Kotlin parameter) | No finding |
| Has a default (`@DefaultValue`, a key set in `application.yml`) | No finding |

A provided key that matches an optional or default-backed requirement is a recognized override and is not `UNUSED`.

### Output and exit behavior

The task prints one line per finding, ordered by key, in the form `KIND key (source)`. The source is the Java or Kotlin location that requires a key, or the file and 1-based line that provides it.

- With no findings, the task succeeds and prints `Configuration contract check passed: no findings`.
- With any `MISSING` or `UNUSED` finding, the task fails the build with `Configuration contract check failed: N finding(s) (...)` followed by the finding lines. A failed build exits non-zero, which is what CI observes. `UNUSED` findings fail the build in the same way as `MISSING` findings.
- Malformed or unsupported input, such as invalid YAML or unsupported Docker Compose syntax, also fails the build with a message that names the offending file; messages for deployment files include the line.
- Diagnostics for input that discovery recognizes but does not interpret are printed after the findings. They do not fail the build. For example, a nested `@ConfigurationProperties` type is skipped and reported with an `UNSUPPORTED` diagnostic.

## End-to-end example

This example is the [`missing-required`](../samples/missing-required) sample. The application requires `app.api-key`; deployment provides only the region.

`src/main/java/com/example/AppSettings.java`:

```java
package com.example;

import org.springframework.beans.factory.annotation.Value;

public class AppSettings {
    @Value("${app.api-key}")
    private String apiKey;

    @Value("${app.region:us}")
    private String region;
}
```

`deploy/.env.example`:

```text
# The deployment supplies the region but not the required API key.
APP_REGION=eu
```

`build.gradle.kts`:

```kotlin
plugins {
    java
    id("io.github.inryeok-office.config-contract")
}

configContract {
    dotenvExampleFiles.from("deploy/.env.example")
}
```

`app.api-key` is required and has no default, and no deployment file provides it, so the task fails. `app.region` has a default, and `APP_REGION` provides it, so neither is reported.

```text
Configuration contract check failed: 1 finding (1 missing)
MISSING app.api-key (com.example.AppSettings.apiKey)
```

Gradle indents the finding line under the failure message. The sample is verified by [`SampleProjectsFunctionalTest`](../config-contract-gradle-plugin/src/test/kotlin/io/github/inryeokoffice/configcontract/gradle/SampleProjectsFunctionalTest.kt). The other samples cover passing, unused, optional and default-aware, and Kotlin binding cases; see [samples/README.md](../samples/README.md).

## Known limitations and unsupported inputs

General:

- `configContractCheck` is not attached to `check`; CI must call it explicitly.
- Only key presence is compared. Values are not compared or validated.
- Only the `main` source set is analyzed.
- Only Spring Boot 3.5.x and JDK 21 are targeted; see [compatibility](compatibility.md).

Deployment files:

- Every Compose service's `environment` is treated as provided to the application. Variables of other services (for example a database container) and generic ones such as `TZ` or `SPRING_PROFILES_ACTIVE` can therefore be reported as `UNUSED`. Because `UNUSED` fails the build and there is no allowlist, this can fail builds for multi-service Compose files.
- Docker Compose input is limited to `services.<name>.environment`. The constructs `env_file`, `extends`, a top-level `include`, and the YAML merge key `<<` are rejected, as are multiple YAML documents, a duplicate key, a nested map or list value, and `$` interpolation in an environment key or service name.
- `.env.example` input is rejected for a line without `=`, an invalid key (keys must match `[A-Za-z_][A-Za-z0-9_]*`), a duplicate key, an unterminated quote, an unsupported escape sequence in a double-quoted value, and text after a closing quote. A quoted value must close on the same line.
- Only `.env.example` and Docker Compose are read. Other deployment formats are not supported.

Spring discovery:

- Nested, collection, and map `@ConfigurationProperties` types are not checked; keys under them are skipped.
- `@ConfigurationProperties` on a `@Bean` method is not supported, and `@Validated` and Bean Validation constraints do not change whether a key is required.
- SpEL expressions in `@Value` (`#{...}`) are not interpreted.
- Placeholders nested in a default value are not interpreted; the raw default text is recorded.
- Java field initializers and Kotlin default arguments are not evaluated, because that would run application code.
- Active profiles are never inferred from deployment input or from the application configuration. `spring.profiles.active`, `spring.profiles.include`, `spring.profiles.group`, `spring.config.import`, `spring.config.location`, `spring.config.additional-location`, and `spring.config.name` are not applied. Profile expressions and `spring.config.activate.on-cloud-platform` are not supported.
- Application configuration is read only from top-level files matching `application[-<profile>].properties|yml|yaml`. Nested locations such as `config/` are not included by default, and a file with another name is rejected.

## Related documents

- [Architecture](architecture.md): module boundaries and dependency direction.
- [Compatibility](compatibility.md): supported JDK, Gradle, and Spring Boot versions.
- [Roadmap](roadmap.md): what is planned for v0.1.0 and later.
- [Samples](../samples/README.md): the end-to-end scenarios and why they are not standalone.
