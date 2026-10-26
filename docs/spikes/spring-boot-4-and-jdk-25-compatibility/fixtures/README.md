# Spring Boot 4 and JDK 25 compatibility fixtures

These are the consumer projects behind the [Spring Boot 4 and JDK 25 compatibility spike](../../spring-boot-4-and-jdk-25-compatibility.md). They are manual fixtures: CI does not build them, and they are not part of the repository build. They are not a support claim.

Each project applies the released plugin `0.1.0` from the Gradle Plugin Portal next to the Spring Boot Gradle plugin of its tuple. The source files are small stand-ins that only reproduce each consumer's declared build settings; they are not code from those projects.

| Fixture | Spring Boot | Java toolchain | Gradle | DSL / language | Daemon JVM for a pass |
| --- | --- | --- | --- | --- | --- |
| `goms` | 4.0.2 | 21 | 9.3.0 | Groovy / Kotlin 2.2.21 | 21 or newer |
| `washer` | 4.0.1 | 25 | 9.4.0 | Kotlin DSL / Java | 25 or newer |
| `gsmc` | 4.1.0 | 25 | 9.7.0 | Kotlin DSL / Kotlin 2.4.10 | 25 or newer |
| `control` | 3.5.16 | 21 | 9.8.1 | Kotlin DSL / Java and Kotlin 2.4.21 | 21 or newer |

## Requirements

- JDK 21 and JDK 25 installed where Gradle toolchain detection finds them. Otherwise set `org.gradle.java.installations.paths`. Toolchain auto-download is disabled.
- Network access to the Gradle Plugin Portal, Maven Central, and `services.gradle.org`.
- On Windows, a short project path and a short `GRADLE_USER_HOME`. A Java 25 toolchain under a Java 21 daemon has failed `:compileJava` when the worker argfile path exceeded `MAX_PATH`.

## Setup

The fixtures keep only `gradle/wrapper/gradle-wrapper.properties`, which pins each Gradle version. Copy a fixture out of the repository, then add the Wrapper scripts and JAR from the repository root:

```bash
cp -r docs/spikes/spring-boot-4-and-jdk-25-compatibility/fixtures/gsmc /tmp/gsmc
cp gradlew gradlew.bat /tmp/gsmc/
cp gradle/wrapper/gradle-wrapper.jar /tmp/gsmc/gradle/wrapper/
```

Point `JAVA_HOME` at the daemon JVM for the cell, and use a fresh `GRADLE_USER_HOME`.

## Commands and expected results

Run the commands from the fixture directory. A cell passes only when the report lines match exactly. A task outcome alone does not count.

### `goms`

| Command | Expected result |
| --- | --- |
| `./gradlew --no-daemon configContractCheck` | FAILED: `2 findings (1 missing, 1 unused)`<br>`UNUSED  LEGACY_TOKEN (deploy/positive/.env.example:8)`<br>`MISSING goms.mail.region (com.example.MailProperties#region)` |
| `./gradlew --no-daemon configContractCheck -PdeploySet=negative` | `Configuration contract check passed: no findings` |
| `./gradlew --no-daemon configContractCheck -PdeploySet=allmissing` | FAILED: `5 findings (5 missing)`<br>`MISSING DB_PASSWORD (src/main/resources/application.yml:6)`<br>`MISSING goms.api.key (com.example.ApiClient.<init>(apiKey))`<br>`MISSING goms.feature.owner (com.example.FeatureToggle.owner)`<br>`MISSING goms.mail.region (com.example.MailProperties#region)`<br>`MISSING goms.mail.sender (com.example.MailProperties#sender)` |

### `washer`

| Command | Expected result |
| --- | --- |
| `./gradlew --no-daemon configContractCheck` | FAILED: `2 findings (1 missing, 1 unused)`<br>`UNUSED  LEGACY_FLAG (deploy/.env.example:5)`<br>`MISSING app.notify.url (com.example.Notifier.notifyUrl)` |
| `./gradlew --no-daemon configContractCheck -PcontractVariant=ok` | `Configuration contract check passed: no findings` |
| `./gradlew --no-daemon configContractCheck` with a Java 21 daemon | FAILED: `Application classes could not be loaded:`, with each class listed as `UnsupportedClassVersionError` for class file version 69.0. This combination is not supported. |

### `gsmc`

| Command | Expected result |
| --- | --- |
| `./gradlew --no-daemon configContractCheck` | FAILED: `2 findings (1 missing, 1 unused)`<br>`UNUSED  LEGACY_FEATURE_FLAG (deploy/.env.example:4)`<br>`MISSING app.payment.api-key (com.example.PaymentProperties#apiKey)` |
| `./gradlew --no-daemon configContractCheck -PdeployDir=deploy-ok` | `Configuration contract check passed: no findings` |
| `./gradlew --no-daemon configContractCheck -PdeployDir=deploy-novalue` | FAILED: `1 finding (1 missing)`<br>`MISSING greeting.message (com.example.GreetingService.message)` |
| `./gradlew --no-daemon configContractCheck` with a Java 21 daemon | FAILED: `Application classes could not be loaded:`, as for `washer`. This combination is not supported. |

### `control`

Plugin `0.1.0` does not discover `@Scheduled` placeholders, so the cleanup keys are unused with it.

| Command | Expected result |
| --- | --- |
| `./gradlew --no-daemon configContractCheck` | FAILED: `4 findings (2 missing, 2 unused)`<br>`UNUSED  APP_CLEANUP_ZONE (deploy/.env.example:5)`<br>`UNUSED  LEGACY_FEATURE_FLAG (deploy/.env.example:6)`<br>`MISSING app.notify.url (com.example.Notifier.notifyUrl)`<br>`MISSING app.payment.api-key (com.example.PaymentProperties#apiKey)` |
| `./gradlew --no-daemon configContractCheck -PdeployDir=deploy-ok01` | `Configuration contract check passed: no findings` |
| `./gradlew --no-daemon configContractCheck -PdeployDir=deploy-ok` | FAILED: `2 findings (2 unused)`<br>`UNUSED  APP_CLEANUP_CRON (deploy-ok/.env.example:7)`<br>`UNUSED  APP_CLEANUP_ZONE (deploy-ok/.env.example:8)` |

### Configuration cache

In every fixture, run `configContractCheck --configuration-cache` twice with the same arguments. The second run must print `Reusing configuration cache.` and the same report.

## Varying an axis

The spike's other cells change one setting in a copy of these fixtures:

- **Daemon JVM:** change `JAVA_HOME`.
- **Gradle:** change `distributionUrl` in `gradle/wrapper/gradle-wrapper.properties`.
- **Spring Boot:** change the `org.springframework.boot` plugin version.
- **DSL:** translate the build and settings scripts between Groovy and Kotlin.
- **Plugin build:** to test an unreleased build, publish it with `preparePublicationVerification` as described in the spike's [Commands](../../spring-boot-4-and-jdk-25-compatibility.md#commands). Then add its Portal-like and Central-like repositories to `pluginManagement`, ahead of the Gradle Plugin Portal.
