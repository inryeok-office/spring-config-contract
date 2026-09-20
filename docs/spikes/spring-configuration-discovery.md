# Spike: Spring Boot configuration discovery semantics

- Issue: [#17](https://github.com/inryeok-office/spring-config-contract/issues/17)
- Spring Boot version exercised: 3.5.16 (JDK 21 toolchain, Kotlin version from the version catalog)
- Evidence: `config-contract-spring/src/test/kotlin/io/github/inryeokoffice/configcontract/spring/spike/`

This spike records observed Spring Boot behavior that affects how a future extractor should build `ConfigurationRequirement` values. It adds no production code and no production dependency. Spring Boot, SnakeYAML, `kotlin-reflect`, and `spring-boot-configuration-processor` are test-scope dependencies of `config-contract-spring` only.

Every "Observed" statement below is backed by a test that starts a real `SpringApplication` with the OS environment and JVM system properties replaced by explicit maps, so the results are deterministic. Statements marked "Not verified" were not tested in this spike and must not be treated as supported behavior.

## Findings

### Relaxed binding for `@ConfigurationProperties`

Test: `RelaxedBindingSpikeTest`. For `@ConfigurationProperties("spike.java-bean")` with property `maxPoolSize`, every one of these source keys binds:

| Source | Key |
| --- | --- |
| Property | `spike.java-bean.max-pool-size` (canonical) |
| Property | `spike.java-bean.maxPoolSize` |
| Property | `spike.java-bean.max_pool_size` |
| Environment variable | `SPIKE_JAVABEAN_MAXPOOLSIZE` (dashes removed) |
| Environment variable | `SPIKE_JAVA_BEAN_MAX_POOL_SIZE` (dashes as underscores) |
| Environment variable | `spike_javabean_maxpoolsize` (lowercase) |

Consequence: a requirement discovered from `@ConfigurationProperties` is one logical key with many accepted spellings. Comparison against deployment-provided keys must normalize both sides with Spring's rules; exact string matching would report false missing keys.

### `@Value` placeholders

Test: `ValueAnnotationSpikeTest`.

- Observed: `@Value("${spike.value.max-pool-size}")` resolves both the exact property and the environment variable `SPIKE_VALUE_MAXPOOLSIZE`.
- Observed: `@Value("${spike.value.maxPoolSize}")` does **not** resolve the property `spike.value.max-pool-size`; startup fails with `Could not resolve placeholder`. Relaxed matching for `@Value` only works when the placeholder uses the canonical kebab-case form.
- Observed: `${key:fallback}` yields `fallback` and `${key:}` yields an empty string when the key is missing. Both are distinguishable default states, matching `DefaultValue.Present`.
- Observed: a missing placeholder without a default fails startup when a `PropertySourcesPlaceholderConfigurer` is registered, which Spring Boot auto-configuration does in a normal application.
- Observed: without a `PropertySourcesPlaceholderConfigurer`, the same missing placeholder is injected as the literal string `${spike.value.missing}`. Whether `@Value` is "required" therefore depends on application wiring.
- Kotlin difference: placeholders must be written as `"\${...}"` in Kotlin string literals. Semantics are otherwise identical, so no separate Java `@Value` fixture was added.

### Missing properties and defaults: Java versus Kotlin

Test: `JavaKotlinBindingSpikeTest`. With no configuration supplied at all:

| Declaration | Observed result |
| --- | --- |
| Java JavaBean field without initializer | `null`, startup succeeds |
| Java JavaBean field with initializer | initializer value, startup succeeds |
| Java record component | `null`, startup succeeds |
| Java record component with `@DefaultValue("5")` | `5` |
| Kotlin nullable constructor parameter | `null`, startup succeeds |
| Kotlin constructor parameter with a default value | Kotlin default value |
| Kotlin `lateinit var` | left uninitialized, startup succeeds |
| Kotlin non-null constructor parameter without default | startup fails: `Parameter specified as non-null is null ... parameter url` |

Consequence: `@ConfigurationProperties` does not make properties required by itself. Only the Kotlin non-null constructor parameter without a default is required at binding time. Java binding needs `-parameters` for constructor binding (configured in the build to mirror the Spring Boot Gradle plugin), and Kotlin constructor binding was exercised with `kotlin-reflect` on the runtime classpath.

Not verified: `@Validated` with Bean Validation constraints such as `@NotNull`, and binding behavior without `-parameters` or without `kotlin-reflect`.

### Application configuration files

Test: `ApplicationConfigurationSpikeTest`.

- Observed: when `application.properties` and `application.yml` are in the same location, the `.properties` value wins for a shared key, and keys only in `.yml` remain visible.
- Observed: an environment variable overrides both files.
- Observed: an unresolved placeholder in a file, such as `name: ${SPIKE_DATABASE_NAME}`, binds into `@ConfigurationProperties` as the literal string `${SPIKE_DATABASE_NAME}` without failing startup.
- Observed: the same unresolved placeholder read through `@Value` fails startup, and the message includes the chain `'SPIKE_DATABASE_NAME' ... <-- "${spike.value.required}"`.
- Observed: `${SPIKE_MISSING_WITH_DEFAULT:from-file-default}` in a file resolves to its default through `@Value`.

Consequence: placeholders inside application configuration files are a primary source of environment requirements, such as `SPIKE_DATABASE_NAME`. Whether a missing one is fatal depends on how the enclosing key is consumed, not on the file.

### Profiles

Test: `ApplicationConfigurationSpikeTest`.

- Observed: with no active profile, only the base document applies; profile-only keys are absent.
- Observed: `SPRING_PROFILES_ACTIVE=prod` applies `application-prod.yml`, which overrides base values and introduces a placeholder (`SPIKE_PROD_POOL_SIZE`) that does not exist without that profile.
- Observed: `SPRING_PROFILES_ACTIVE=staging` applies a `spring.config.activate.on-profile: staging` document inside `application.yml`.

Consequence: the set of required environment keys depends on the active profiles, which are themselves supplied by the deployment environment.

### Configuration metadata

Test: `ConfigurationMetadataSpikeTest`.

- Observed: `spring-boot-configuration-processor`, run on Java sources, records Java JavaBean and record properties with canonical names. It also records field initializer defaults (`java-default`) and `@DefaultValue` defaults (`5`).
- Observed: in this build, Kotlin `@ConfigurationProperties` classes and `@Value` placeholders are absent from the generated metadata. The processor is a Java annotation processor. Kotlin classes would need kapt, which this spike did not configure or verify.

Consequence: generated metadata is a useful but incomplete source. It cannot be the only discovery mechanism for Kotlin applications or `@Value`, and it does not encode requiredness.

## Recommended v0.1 boundaries

These are recommendations for the extractor implementation Issue, not implemented behavior.

1. **Key identity:** Represent Spring requirements by their canonical kebab-case name, and compare deployment keys after applying Spring's relaxed binding and environment-variable mapping. Keep this normalization in `config-contract-spring`. Core already leaves normalization to integration modules, and no Spring types are needed in core.
2. **`@ConfigurationProperties` presence:** Default to `OPTIONAL`. Treat only Kotlin non-null constructor parameters without a default as `REQUIRED`. Record `DefaultValue.Present` for Kotlin defaults, Java field initializers, and `@DefaultValue`. Report Bean Validation constraints as unsupported until they are verified.
3. **`@Value` presence:** Treat `${key}` as `REQUIRED` and `${key:default}` as `OPTIONAL` with `DefaultValue.Present`, which includes the empty default. Assume a Spring Boot application with placeholder auto-configuration, and document that assumption.
4. **Non-canonical `@Value` keys:** Treat a camelCase or otherwise non-canonical `@Value` placeholder as exact-match only, and emit an explicit diagnostic. Do not apply relaxed matching to it.
5. **File placeholders:** Extract `${NAME}` and `${NAME:default}` from `application.properties` and `application.yml` as requirements. For unresolved placeholders consumed by `@ConfigurationProperties`, report the value as unknown instead of silently treating it as satisfied, because Spring binds the literal text.
6. **Profiles:** Analyze the base documents by default, and accept an explicit list of active profiles as input. Do not infer active profiles from the deployment environment in v0.1. Report profile expressions (`on-profile` with `!`, `&`, or `|`), profile groups, and `spring.config.import` as unsupported.
7. **Metadata:** Do not rely on `spring-configuration-metadata.json` as the only source. It may complement source or bytecode inspection for Java.

## Explicitly out of scope or unverified

- `@Validated`/Bean Validation, `@ConfigurationPropertiesScan`, and nested or collection binding (lists, maps, indexed environment variables)
- `spring.config.import`, config trees, `SPRING_APPLICATION_JSON`, command-line arguments, and custom `PropertySource` or `EnvironmentPostProcessor` implementations
- Profile expressions, profile groups, `spring.profiles.include`, and `spring.profiles.default`
- Spring Boot versions other than 3.5.16, Spring Boot 4.x, and kapt-generated Kotlin metadata
