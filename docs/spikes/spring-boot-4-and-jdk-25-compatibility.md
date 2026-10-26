# Spike: Spring Boot 4 and JDK 25 compatibility

- Issue: [#70](https://github.com/inryeok-office/spring-config-contract/issues/70)
- Audit baseline: `main` at `0d48497`; released plugin `0.1.0` from the Gradle Plugin Portal
- Dates: 2026-10-24 to 2026-10-25
- CI regression subset: `ConfigContractPluginFunctionalTest` and `PublicationPipelineFunctionalTest` (see [Regression subset](#regression-subset))

This spike records observed behavior of the plugin in Spring Boot 4 and JDK 25 consumer builds. It changes no production code and **does not change the support policy** in [compatibility.md](../compatibility.md). Spring Boot 3.5.16 on JDK 21 remains the only supported combination until maintainers review a separate policy change.

Statements marked "Observed" were produced by real Gradle builds. Cells not listed under [Attempted cells](#attempted-cells) are untested and must not be treated as supported.

## Method

Each cell is a minimal consumer project that applies the plugin next to the Spring Boot Gradle plugin (and the Kotlin plugins where the cell uses Kotlin). Its deployment input is deliberately incomplete. The expected findings were written down before the first run:

- the positive deployment omits one required key and adds one unrelated key, so the expected result is exactly one `MISSING` and one `UNUSED` line (the control expects more, as listed in its fixture);
- the negative control supplies every key and must print `Configuration contract check passed: no findings`;
- optional and defaulted keys are supplied, so a silently skipped annotation or misread Kotlin metadata would show up as an extra `UNUSED` line.

The consumer projects for the four tuples, with their deployment inputs and expected findings, are checked in under [fixtures](spring-boot-4-and-jdk-25-compatibility/fixtures/README.md). Axis variations and additional Spring Boot versions changed only the named axis in a copy of these projects. The discovery-breadth and runtime-parity cells added sources and launch tasks that are not checked in.

A passing task alone was never counted as success. Every cell used an isolated Gradle user home and, except for the long-lived daemon check under [Discovery breadth](#discovery-breadth-on-spring-boot-410-daemon-25), `--no-daemon`. Configuration-cache reuse was checked with two consecutive runs.

The released `0.1.0` was compared with `main` (`0d48497`), which was published locally as an unsigned `0.2.0-local` build. The two differ only in Kotlin 2.4.21 versus 2.4.20 and in `@Scheduled` discovery ([#80](https://github.com/inryeok-office/spring-config-contract/issues/80)). Spring Boot is 3.5.16 in both.

Environments: Windows 11 Pro 10.0.26200 with Microsoft OpenJDK 21.0.10 and Amazon Corretto 25.0.3; Ubuntu 24.04.2 on WSL2 with Temurin 21.0.13 and 25.0.5.

## Commands

Every command ran from the cell's project directory through the cell's pinned Gradle Wrapper. `JAVA_HOME` selected the daemon JVM, and `GRADLE_USER_HOME` pointed to a directory used only by that round. Linux runs also passed `--console=plain`.

Each cell ran this sequence:

```bash
./gradlew --no-daemon --version                 # Gradle version and daemon JVM
./gradlew --no-daemon buildEnvironment          # build script resolution, see Build script resolution
./gradlew --no-daemon configContractCheck       # positive deployment: FAILED with exactly the expected findings
./gradlew --no-daemon configContractCheck <negative-control-property>   # passed: no findings
./gradlew --no-daemon configContractCheck --configuration-cache         # run twice; the second run must reuse the entry
```

The negative-control property selects the complete deployment input:

| Cell | Negative-control property |
| --- | --- |
| GOMS | `-PdeploySet=negative` |
| Washer | `-PcontractVariant=ok` |
| GSMC | `-PdeployDir=deploy-ok` |
| Control | `-PdeployDir=deploy-ok01` with `0.1.0`; `-PdeployDir=deploy-ok` with `0.2.0-local`, which also discovers `@Scheduled` |

The class-file major version was read from bytes 6 and 7 of each compiled class under `build/classes`.

The `0.2.0-local` build was published from `0d48497` on JDK 21, with the repository's offline publication task:

```bash
./gradlew --no-daemon preparePublicationVerification -PreleaseVersion=0.2.0-local \
  -PcentralLikeRepository=<dir>/central -PportalLikeRepository=<dir>/portal
```

Cells that used it declared `<dir>/portal` and `<dir>/central` as `pluginManagement` repositories ahead of the Gradle Plugin Portal and requested plugin version `0.2.0-local`.

The long-lived daemon check ran `./gradlew configContractCheck` without `--no-daemon`, repeated it after source edits and plugin-version switches, and finished with `./gradlew --stop`.

For [runtime binding parity](#runtime-binding-parity), each fixture wrote its runtime classpath to a file. Every case then started the application in a new JVM with only that case's variables:

```bash
env -i <CASE_VARIABLES> java -cp <runtime-classpath> <main-class> --spring.main.web-application-type=none
```

The exit code, and the startup failure when there was one, were recorded for each case.

## Consumer tuples

The tuples come from the prospective-consumer snapshots listed in the issue. They are declared build settings, not evidence of those projects' own integration.

| Tuple | Spring Boot | Java toolchain | Gradle | DSL / language |
| --- | --- | --- | --- | --- |
| GOMS | 4.0.2 | 21 | 9.3.0 | Groovy / Kotlin 2.2.21 |
| Washer | 4.0.1 | 25 | 9.4.0 | Kotlin DSL / Java |
| GSMC | 4.1.0 | 25 | 9.7.0 | Kotlin DSL / Kotlin 2.4.10 |
| Control | 3.5.16 | 21 | 9.8.1 | Kotlin DSL / Java and Kotlin 2.4.21 |

## Attempted cells

All finding lines matched the pre-written expectations exactly, unless the outcome column says otherwise. "Daemon" is the JVM that runs Gradle and therefore the plugin; "major" is the class-file version of the compiled application classes.

### Consumer tuples, axis variations, and OS

| Cell | Daemon | Major | Plugin | OS | Outcome |
| --- | --- | --- | --- | --- | --- |
| GOMS as declared | 21 | 65 | 0.1.0 | Windows, Linux | Pass |
| GOMS, daemon 25 | 25 | 65 | 0.1.0 | Windows | Pass |
| GOMS, Kotlin DSL | 21 | 65 | 0.1.0 | Windows | Pass |
| GOMS on Gradle 9.8.1 | 21 | 65 | 0.1.0, 0.2.0-local | Windows | Pass |
| Washer as declared | 25 | 69 | 0.1.0 | Windows, Linux | Pass |
| Washer, Groovy DSL | 25 | 69 | 0.1.0 | Windows | Pass |
| Washer, daemon 21 | 21 | 69 | 0.1.0 | Windows, Linux | Expected failure: bytecode loading |
| GSMC as declared | 25 | 69 | 0.1.0 | Windows, Linux | Pass |
| GSMC, Groovy DSL | 25 | 69 | 0.1.0 | Windows | Pass |
| GSMC on Gradle 9.8.1 | 25 | 69 | 0.1.0, 0.2.0-local | Windows | Pass |
| GSMC, daemon 21 | 21 | 69 | 0.1.0 | Windows | Expected failure: bytecode loading |
| Control | 21 | 65 | 0.1.0, 0.2.0-local | Windows | Pass |

### Additional Spring Boot versions

Only the Spring Boot plugin version was changed in the copied tuple fixtures. Every cell ran on Windows with plugin `0.1.0` unless the Plugin column says otherwise.

| Spring Boot | Washer (daemon 25) | GSMC (daemon 25) | GOMS (daemon 21) | Control (daemon 21) |
| --- | --- | --- | --- | --- |
| 4.0.0 | Pass | Pass | Pass | Untested |
| 4.0.1 | Pass | Untested | Untested | Untested |
| 4.0.2 | Untested | Untested | Pass | Untested |
| 4.0.9 | Pass | Pass | Pass | Untested |
| 4.1.0 | Untested | Pass | Untested | Untested |
| 4.1.1 | Pass | Pass | Untested | Untested |
| 4.1.2 | Pass | Pass | Untested | Pass (0.1.0 and 0.2.0-local) |
| 4.2.0-RC1 (preview, not a support candidate) | Untested | Pass | Untested | Untested |

Spring Boot 4.0.3 to 4.0.8 are untested.

### Discovery breadth on Spring Boot 4.1.0, daemon 25

Each cell ran with both `0.1.0` and `0.2.0-local`, and the output matched the [usage guide](../usage-guide.md):

- **`@Scheduled` placeholders:** discovered by `0.2.0-local`, with SpEL reported as `UNSUPPORTED`. `0.1.0` ignores them without a diagnostic, as expected for that release.
- **Application configuration:**
  - profile-specific files and `on-profile` documents;
  - `.properties` together with `.yml`, with properties precedence.
- **Bound types:**
  - nested, `List`, `Set`, and `Map` `@ConfigurationProperties` types each produce the documented `UNSUPPORTED` diagnostic;
  - mixed Java and Kotlin sources in one module produce exact findings.
- **Documented unsupported cases:** twelve diagnostics, an unsupported configuration file name, and malformed YAML. Each matched the documented text, or failed with the documented error.
- **Build behavior:**
  - one long-lived daemon handled source edits and plugin-version switches with no stale classes;
  - configuration-cache entries were reused on failing runs with identical output, and the result updated after the deployment file changed.

## Failure classification

| Failure | Classification | Observed | Minimal reproduction |
| --- | --- | --- | --- |
| Java 25 application bytecode under a Java 21 Gradle daemon | Bytecode loading | `Application classes could not be loaded:` lists every class with `UnsupportedClassVersionError ... class file version 69.0 ... up to 65.0`. The task fails, and no class is skipped silently. | Java 25 toolchain, Gradle run on JDK 21. Covered without JDK 25 by `ConfigContractPluginFunctionalTest`, which loads a class file whose major version is newer than the running JVM. |

No resolution, plugin application, API linkage, metadata interpretation, or task execution failure attributable to the plugin was observed.

This bytecode-loading combination is **not a supported execution combination**. The plugin loads application classes inside the Gradle daemon JVM, so the daemon must be at least as new as the application bytecode target. Selecting a newer compilation toolchain does not upgrade the daemon.

One environment issue was seen once and does not come from the plugin. On Windows, with a Java 21 daemon, a Java 25 toolchain, and a long Gradle user home path, `:compileJava` failed with `could not open ...gradle-worker-classpath...txt`, because the argfile path exceeded `MAX_PATH`. A shorter Gradle user home avoided it.

## Build script resolution

The Spring Boot 4 Gradle plugin does not depend on `spring-boot`. It does depend on `spring-core` 7, which raises the plugin's `spring-core` on the build script classpath. `spring-boot` stays at 3.5.16, and `spring-beans` and `spring-context` stay at 6.2.19.

| Consumer Spring Boot | `spring-core` on the build script classpath |
| --- | --- |
| 3.5.16 | 6.2.19 (no mix) |
| 4.0.0 / 4.0.1 / 4.0.2 / 4.0.9 | 7.0.1 / 7.0.2 / 7.0.3 / 7.0.10 |
| 4.1.0 / 4.1.1 / 4.1.2 | 7.0.8 / 7.0.9 / 7.0.10 |
| 4.2.0-RC1 | 7.1.0-RC1 |

So on Spring Boot 4 consumers, discovery runs on a mixed Spring Framework 6.2 and 7.x runtime. No `LinkageError`, `NoSuchMethodError`, or `NoClassDefFoundError` appeared in any covered path. These paths include:

- `.properties` loading and multi-document YAML;
- `@Scheduled`, `Duration`, and `DataSize`;
- an application-side annotation that exists only in Spring Framework 7.

The mix is an unvalidated architectural dependency, not a verified guarantee.

Parent-first class loading resolves `@ConfigurationProperties`, `@DefaultValue`, and `@Value` to the plugin's Spring Boot 3.5.16 and Spring Framework 6.2.19 classes. That is why annotation identity ([ADR-0002](../decisions/0002-spring-discovery-through-spring-boot-apis.md)) holds for code compiled against Spring Boot 4. It would stop holding if a future Spring Boot line moved or renamed one of these annotation types.

Gradle's embedded Kotlin constraint lowers the plugin's `kotlin-stdlib` and `kotlin-reflect` in Kotlin DSL builds:

- 2.3.0 on Gradle 9.4.0;
- 2.4.0 on Gradle 9.7.0;
- 2.4.10 on Gradle 9.8.1.

The lowered `kotlin-reflect` read metadata from Kotlin 2.2.21, 2.4.10, and 2.4.21 compilers correctly. Gradle 9.3.0 compiled Kotlin DSL accessors for the plugin's Kotlin 2.4 extension types with its embedded Kotlin 2.2.21, with no warnings.

## Runtime binding parity

The application ran on its own Spring Boot runtime in a separate JVM, with an explicit environment. There were 42 cases across the GSMC, Washer, and GOMS fixtures, each built against Spring Boot 3.5.16, 4.0.x, and 4.1.0.

**Spring Boot 3.5.16, 4.0.x, and 4.1.0 behaved identically in every case.**

- **Required keys:** every key the plugin reports as required failed startup when it was absent. A Kotlin non-null constructor parameter failed with `NullPointerException: Parameter specified as non-null is null`, and `@Value` without a default failed with `PlaceholderResolutionException`.
- **Optional keys:** every optional key started with its default or `null`.
- **Relaxed binding:** environment names in both relaxed forms, such as `APP_PAYMENT_APIKEY` and `APP_PAYMENT_API_KEY`, bound.
- **Unused keys:** keys reported as `UNUSED` had no observable effect.

Two differences from runtime behavior apply equally to every Spring Boot version, so neither is a Spring Boot 4 issue:

- A `${NAME}` placeholder in application configuration is reported as `MISSING` even when nothing injects the key, and the application starts. This is the documented contract rule in the [usage guide](../usage-guide.md#spring-sources). When such a placeholder is bound into `@ConfigurationProperties` and is unresolved, Spring Boot binds the literal `${NAME}` text and starts. The plugin's `MISSING` finding is then the only signal.
- When deployment overrides the property itself, for example `APP_DB_PASSWORD` for `app.db.password: ${DB_PASSWORD}`, Spring Boot never resolves the placeholder, but the plugin still reports `DB_PASSWORD` as `MISSING`. The usage guide does not describe this case.

## Regression subset

These tests run in CI on the existing JDK 21 job, offline:

- **`ConfigContractPluginFunctionalTest`: explicit failure for newer bytecode.** The test loads a class file whose major version is newer than the running JVM and requires the explicit bytecode-loading failure. This reproduces the Java 25 toolchain under a Java 21 daemon without JDK 25.
- **`PublicationPipelineFunctionalTest`: Spring Boot 4 consumer.** This consumer applies the Spring Boot `compatibility-probe-spring-boot` Gradle plugin (4.1.2, pinned in the version catalog as test-only) and compiles Java sources against that Spring Boot. It checks three things:
  - the exact `MISSING` and `UNUSED` lines;
  - a passing negative control;
  - that the build script classpath really mixes Spring Boot 3.5.16 with `spring-core` 7.

  The producer seeds the consumer's build script graph and application graph into separate disposable repositories.
- **Control:** the existing Spring Boot 3.5.16 coexistence consumer in `PublicationPipelineFunctionalTest`.

The following cells remain manual. The checked-in [fixtures](spring-boot-4-and-jdk-25-compatibility/fixtures/README.md) are their starting point.

| Cell group | Setup requirement |
| --- | --- |
| Java 25 toolchain with a Java 25 daemon (Washer, GSMC) | JDK 25 installed and detectable, with `org.gradle.java.installations.paths` set when auto-detection is off; Gradle run with `JAVA_HOME` pointing to JDK 25 |
| Java 25 toolchain under a Java 21 daemon, with real compilation | Both JDKs installed; on Windows, a short Gradle user home path |
| Kotlin consumers on Spring Boot 4 (GOMS, GSMC) | Network access to resolve the Kotlin Gradle plugin and the Spring Boot BOM |
| Other Spring Boot 4 versions, Gradle 9.3.0 to 9.7.0, Linux | Pinned Wrapper per cell; isolated Gradle user home |
| Runtime binding parity | A separate JVM per case started with the application's runtime classpath and an explicit environment |

## Recommendation

The evidence supports proposing a separately reviewed support boundary of Spring Boot 4.0.x and 4.1.x, under these conditions:

- the Gradle daemon JVM is at least as new as the application bytecode target;
- Gradle 9.3.0 or later;
- Java and Kotlin sources.

The existing support claims stay unchanged until maintainers accept that policy change. The following separately scoped follow-ups are suggested:

1. **Support policy:** update [compatibility.md](../compatibility.md), and add a CI job with JDK 25 if Java 25 targets are to be claimed. Decide which Spring Boot 4 patch versions CI pins, because Dependabot will propose updates to `compatibility-probe-spring-boot`.
2. **Runtime isolation:** decide on discovery runtime isolation or relocation, which [ADR-0003](../decisions/0003-plugin-portal-and-maven-central-publication.md) deferred. The mixed Spring Framework 6.2 and 7.x build script runtime is the main unverified risk.
3. **Daemon JVM diagnostic:** consider adding a hint to the bytecode-loading failure message that the Gradle daemon JVM must be at least the application's target.
4. **Overridden placeholders:** decide whether a deployment override of a property whose application configuration value is a `${NAME}` placeholder should still report `NAME` as missing.

## Untested

- macOS, and Linux outside WSL2.
- Spring Boot 4.0.3 to 4.0.8, and other combinations of tuple and version not marked in [Additional Spring Boot versions](#additional-spring-boot-versions).
- `0.2.0-local` on Washer, and on any Spring Boot 4 version other than 4.1.0 and 4.1.2.
- `@Scheduled` in Java sources, nested Java records, setter-based Java bean `@ConfigurationProperties`, and camelCase `@Value` placeholders.
- Runtime binding parity for `.properties`, nested and collection types, `spring.config.import`, and profile groups.
- Behavior of `spring-core` 7.1 on discovery paths these fixtures do not use.
