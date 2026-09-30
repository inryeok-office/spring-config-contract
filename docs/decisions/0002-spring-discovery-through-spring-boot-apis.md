# ADR-0002: Discover Spring requirements through Spring Boot's own APIs

- Status: Accepted
- Date: 2026-09-28

## Context

Issue [#18](https://github.com/inryeok-office/spring-config-contract/issues/18) implements Spring-side discovery for the v0.1 cases validated by the [Spring configuration discovery spike](../spikes/spring-configuration-discovery.md). Discovery has to decide which constructor Spring Boot binds, how property names become canonical keys, how application configuration files are parsed, and which environment-variable names satisfy a property. Re-implementing those rules would risk guessing Spring behavior. The spike also showed that `spring-configuration-metadata.json` cannot be the only source, because it omits Kotlin classes and `@Value`.

## Decision

`config-contract-spring` inspects already-loaded application classes by reflection and delegates Spring-specific rules to public Spring Boot APIs:

- `BindConstructorProvider.DEFAULT` decides constructor versus JavaBean binding, `DefaultParameterNameDiscoverer` supplies parameter names, and `DataObjectPropertyName` produces the dashed property form.
- `PropertiesPropertySourceLoader` and `YamlPropertySourceLoader` parse application configuration files. The module applies the documented v0.1 profile and precedence rules on top of their output.
- `SpringEnvironmentKeys` asks a Spring environment, containing only the deployment keys, which requirement keys it resolves. This aligns environment-variable names with canonical property names.
- Kotlin nullability and default arguments come from `kotlin-reflect`.

Consequently, `spring-boot` and `kotlin-reflect` are `implementation` dependencies of `config-contract-spring`, and `snakeyaml` is a `runtimeOnly` dependency. None of their types appear in the public API. Discovery never instantiates application classes or evaluates field initializers or Kotlin default arguments, so those default values remain unknown. Discovery also performs no file or environment access. Callers supply loaded classes and configuration text.

## Consequences

- Behavior follows Spring Boot for the supported cases, and cross-check tests that start a real Spring Boot application detect divergence.
- Callers must load application classes in a class loader where the application sees the same Spring Boot classes that this module uses. The Gradle integration ([#22](https://github.com/inryeok-office/spring-config-contract/issues/22)) owns building that class loader, for example with Gradle worker class-loader isolation.
- The supported Spring Boot line is the version in the version catalog (3.5.x). Supporting another line requires evidence as described in [the compatibility policy](../compatibility.md).
- Java field initializer and Kotlin default values are not recorded as defaults. The affected properties are still `OPTIONAL`, so comparison results do not change.
