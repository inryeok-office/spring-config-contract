# Usage guide

This guide describes the v0.1 configuration contract workflow: a Gradle task that compares configuration a Spring Boot application requires with configuration its deployment files provide. It documents only behavior implemented and covered by the [sample projects](../samples/README.md).

v0.1.0 is publicly available: the runtime modules resolve from Maven Central and the Gradle plugin resolves from the
Gradle Plugin Portal. The [published-consumer verification](https://github.com/inryeok-office/spring-config-contract/actions/runs/39112089966)
passed for both the default Portal resolution path and the explicit Portal-plus-Maven-Central mirror path.

## What the check does

`configContractCheck` reads two sides of a contract:

- **Requirements** that Spring Boot code and application configuration declare: `@ConfigurationProperties`, `@Value`, `@Scheduled`, and `application*.properties|yml|yaml`.
- **Provided configuration** that deployment files supply: `.env.example` files and the `environment` entries of Docker Compose services.

It reports a `MISSING` finding for a required key that deployment does not provide, and an `UNUSED` finding for a provided key that nothing in the application recognizes. It compares key presence only; values are never compared and never appear in the output.

## Quick Start

Apply the published plugin by version in your application's `build.gradle.kts`:

```kotlin
plugins {
    java
    id("io.github.inryeok-office.config-contract") version "0.1.0"
}

configContract {
    dotenvExampleFiles.from("deploy/.env.example")
    composeFiles.from("deploy/compose.yml")
}
```

Or use Groovy DSL in `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'io.github.inryeok-office.config-contract' version '0.1.0'
}

configContract {
    dotenvExampleFiles.from('deploy/.env.example')
    composeFiles.from('deploy/compose.yml')
}
```

The configured deployment input paths are relative to the project root. Every configured file must exist inside that project. Without an explicit `applicationConfigurationFiles` setting, top-level `application.properties`, `application.yml`, `application.yaml`, and their `application-<profile>` variants are discovered in the `main` resource directories.

Put the deployment keys to check in the configured `.env.example` or Docker Compose `services.<name>.environment` file, then run the task explicitly; it is not attached to `check`:

```bash
./gradlew configContractCheck
```

See [findings](#findings) for how to read the result and the [end-to-end example](#end-to-end-example) for a missing-key example. For tested versions, see [compatibility](compatibility.md).

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

For source-checkout development, the plugin is applied by id without a version by including this repository as a build in your project's `settings.gradle.kts`.

Requirements:

- JDK 21 and Spring Boot 3.5.16, the versions listed in [compatibility](compatibility.md). Other Spring Boot versions are not claimed.
- Network access, to resolve your project's Spring dependencies from the repository you declare, and the plugin's own dependencies when the included build is compiled.
- Run Gradle with the Wrapper of the checkout you include. Other Gradle versions are not claimed.

`settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("/path/to/spring-config-contract")
}

rootProject.name = "my-application"
```

Or use Groovy DSL in `settings.gradle`:

```groovy
pluginManagement {
    includeBuild('/path/to/spring-config-contract')
}

rootProject.name = 'my-application'
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

Or use Groovy DSL in `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'io.github.inryeok-office.config-contract'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation('org.springframework.boot:spring-boot:3.5.16')
}

configContract {
    activeProfiles.set(['prod'])             // optional; empty analyzes the default profile
    dotenvExampleFiles.from('deploy/.env.example')   // optional
    composeFiles.from('deploy/compose.yml')          // optional
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
| `composeServices` | Optional set of Docker Compose service names to read from `composeFiles`. Empty or unset reads every service. See [Selecting Docker Compose services](#selecting-docker-compose-services). |
| `applicationConfigurationFiles` | Application configuration files to analyze. Defaults to the top-level `application.*` and `application-*.*` files with a `properties`, `yml`, or `yaml` extension in the `main` resource directories. |

Deployment and application configuration files must resolve inside the root project. Reported paths are relative to the root project, so output does not contain machine-specific paths. Once the `java` plugin is applied, the task analyzes the compiled classes of the `main` source set.

## Supported sources

### Spring sources

| Source | What it contributes |
| --- | --- |
| `@ConfigurationProperties` on a class (Java bean, constructor binding, Kotlin) | One key per single-value property, named `<prefix>.<dashed-property-name>`. A property is required only for a Kotlin non-null constructor parameter that has no Kotlin default and no `@DefaultValue`. Every other property is optional. A single-value `@DefaultValue` is recorded as the default. |
| `@Value` on a field, method, constructor parameter, or method parameter | `${key}` is a required key. `${key:default}` is an optional key with that default, including an empty default. |
| `@Scheduled` on a method: `cron`, `zone`, `fixedDelayString`, `fixedRateString`, and `initialDelayString` | Placeholders are discovered with the same rules as `@Value`. |
| `application.properties`, `application.yml`, `application.yaml`, and `application-<profile>.*` | Each effective key is an optional key whose default is the configured value, so deployment can override it without being reported as unused. A `${NAME}` or `${NAME:default}` placeholder inside a value is a requirement, as with `@Value`. |

Single-value property types are `String`, primitives and their wrappers, enums, `BigDecimal`, `BigInteger`, `Duration`, `Period`, `Charset`, `Locale`, `UUID`, `URI`, `URL`, and `DataSize`.

Deployment keys are matched to requirement keys with Spring Boot's environment-variable rules, so `APP_API_KEY` provides `app.api-key`. A deployment key that matches no requirement keeps its own name and is reported as unused.

### Deployment sources

| Source | What it contributes |
| --- | --- |
| `.env.example` | One provided key per `KEY=value` line. Comments, blank lines, an `export` prefix, and single- or double-quoted values are accepted. |
| Docker Compose `services.<name>.environment` | One provided key per entry, in list form (`- KEY=value`, `- KEY=`, `- KEY`) or map form (`KEY: value`, `KEY:`). A key without a value still counts as provided. |

Only key names and their line numbers are read from deployment files. Values are never exposed, and `$VAR` or `${VAR}` in a value is not expanded. All other Compose fields are ignored. By default every Compose service is read; to read only the services that configure your application, see the next section.

### Selecting Docker Compose services

By default, the `environment` of every service in `composeFiles` is treated as provided to the application, so variables of other containers can be reported as `UNUSED`, and a key that only another container provides can satisfy an application requirement and hide a `MISSING` finding. `composeServices` is an opt-in way to read only the services you name. It is not part of the published v0.1.0 plugin; it is available in builds that include this change.

Kotlin DSL (`build.gradle.kts`):

```kotlin
configContract {
    composeFiles.from("deploy/compose.yml")
    composeServices.set(setOf("app"))   // or: composeServices = setOf("app")
}
```

Groovy DSL (`build.gradle`):

```groovy
configContract {
    composeFiles.from('deploy/compose.yml')
    composeServices = ['app']
}
```

`composeServices` is a Gradle `SetProperty<String>`.

Behavior:

- An empty or unset selection reads every service, exactly as in v0.1.0.
- Names are matched exactly. Order and duplicates do not matter.
- Several names read the union of their environment keys. A union does not prove that each selected service is independently complete.
- Each file in `composeFiles` is parsed independently; files are not merged as Compose overlays. A name is valid if at least one configured file declares it, and a name declared in several files contributes from every file that declares it, in file and document order.
- A selected service without an `environment` contributes nothing and is not an error.
- The application service is never detected automatically. You must name it.

The build fails with a message starting `Invalid composeServices:` when a name is blank, a name contains interpolation (`$`), a name is not declared in any configured Compose file, or services are selected but `composeFiles` is empty. The message lists the offending names.

Validation scope: file-wide problems still apply to every configured Compose file, selected or not. These are invalid YAML, multiple YAML documents, a non-mapping root, a missing or non-mapping `services`, a top-level `include`, and invalid, duplicate, or interpolated service names. Constructs inside a service body are interpreted only for selected services: a non-mapping body, the `environment` shape, `env_file`, `extends`, the merge key `<<`, and duplicate environment keys. An unselected service may therefore use them without failing the build. `env_file` on a selected service remains unsupported and is rejected.

The same selection is available programmatically as `ComposeAdapter.selecting(services, sources)` in `config-contract-deployment`, which throws `IllegalArgumentException` for selector errors.

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

Or use Groovy DSL in `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'io.github.inryeok-office.config-contract'
}

configContract {
    dotenvExampleFiles.from('deploy/.env.example')
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
- Only Spring Boot 3.5.16 and JDK 21 are claimed; see [compatibility](compatibility.md).

Deployment files:

- By default, every Compose service's `environment` is treated as provided to the application. Variables of other services (for example a database container) and generic ones such as `TZ` or `SPRING_PROFILES_ACTIVE` can therefore be reported as `UNUSED`. Because `UNUSED` fails the build and there is no allowlist, this can fail builds for multi-service Compose files. Opt in to [explicit service selection](#selecting-docker-compose-services) with `composeServices` to read only the services you name; the application service is never auto-detected.
- Docker Compose input is limited to `services.<name>.environment`. The constructs `env_file`, `extends`, a top-level `include`, and the YAML merge key `<<` are rejected, as are multiple YAML documents, a duplicate key, a nested map or list value, and `$` interpolation in an environment key or service name. With `composeServices`, constructs inside a service body are rejected only for selected services; see [Selecting Docker Compose services](#selecting-docker-compose-services).
- `.env.example` input is rejected for a line without `=`, an invalid key (keys must match `[A-Za-z_][A-Za-z0-9_]*`), a duplicate key, an unterminated quote, an unsupported escape sequence in a double-quoted value, and text after a closing quote. A quoted value must close on the same line.
- Only `.env.example` and Docker Compose are read. Other deployment formats are not supported.

Spring discovery:

- Nested, collection, and map `@ConfigurationProperties` types are not checked; keys under them are skipped.
- `@ConfigurationProperties` on a `@Bean` method is not supported, and `@Validated` and Bean Validation constraints do not change whether a key is required.
- SpEL expressions in `@Value` and `@Scheduled` (`#{...}`) are not interpreted.
- Placeholders in other annotation attributes, such as `@KafkaListener` topics or `@RequestMapping` paths, and in annotations meta-annotated with `@Value` are not discovered.
- Placeholders nested in a default value are not interpreted; the raw default text is recorded.
- Java field initializers and Kotlin default arguments are not evaluated, because that would run application code.
- Active profiles are never inferred from deployment input or from the application configuration. `spring.profiles.active`, `spring.profiles.include`, `spring.profiles.group`, `spring.config.import`, `spring.config.location`, `spring.config.additional-location`, and `spring.config.name` are not applied. Profile expressions and `spring.config.activate.on-cloud-platform` are not supported.
- Application configuration is read only from top-level files matching `application[-<profile>].properties|yml|yaml`. Nested locations such as `config/` are not included by default, and a file with another name is rejected.

## Related documents

- [Architecture](architecture.md): module boundaries and dependency direction.
- [Compatibility](compatibility.md): supported JDK, Gradle, and Spring Boot versions.
- [Roadmap](roadmap.md): implemented v0.1 scope and planned post-v0.1 work.
- [Samples](../samples/README.md): the end-to-end scenarios and why they are not standalone.
