package io.github.inryeokoffice.configcontract.spring

import io.github.inryeokoffice.configcontract.core.ConfigurationKey
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.DefaultValue
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.Presence

/**
 * Discovers the configuration requirements of a Spring Boot 3.5 application.
 *
 * Supported v0.1 behavior, as validated by the Issue #17 spike:
 * - `@ConfigurationProperties` declared directly on a class. Keys are the
 *   canonical kebab-case prefix and property name. A property is `REQUIRED` only
 *   for a Kotlin non-null constructor parameter without a Kotlin default or
 *   `@DefaultValue`; every other property is `OPTIONAL`. A single-value
 *   `@DefaultValue` is recorded as the default. Kotlin default arguments and Java
 *   field initializers are not evaluated, so their values stay unknown.
 * - `@Value` declared directly on a field, method, or parameter. `${key}` is
 *   `REQUIRED`; `${key:default}` is `OPTIONAL` with that default, including the
 *   empty default. This assumes Spring Boot's placeholder auto-configuration.
 * - Application configuration files for the explicit input profiles. Each
 *   effective key is an `OPTIONAL` requirement whose default is the configured
 *   text, so deployment may override it without being reported as unused.
 *   `${NAME}` and `${NAME:default}` inside a value are requirements like `@Value`
 *   placeholders; the enclosing key keeps the raw text as its default because
 *   that is what Spring binds when the placeholder cannot be resolved.
 *
 * When several sources use one key they are merged: the key is `REQUIRED` if any
 * consumer requires it, a configured file value is its default, and otherwise a
 * default is kept only when a `REQUIRED` consumer does not exist and all
 * consumers with a default agree on it.
 *
 * Unsupported constructs produce [SpringDiscoveryDiagnostic]s instead of guessed
 * requirements. Invalid input throws [SpringDiscoveryException]. Discovery is
 * pure: it performs no file, environment, or network access, and results do
 * not depend on input order.
 */
object SpringConfigurationDiscovery {
    private val PROFILE_NAME = Regex("[A-Za-z0-9_.-]+")

    /** @throws SpringDiscoveryException if the input is malformed. */
    @JvmStatic
    fun discover(input: SpringDiscoveryInput): SpringDiscoveryResult {
        val collector = DiscoveryCollector()
        input.activeProfiles.forEachIndexed { index, profile ->
            if (!PROFILE_NAME.matches(profile)) {
                collector.problem(
                    "activeProfiles[$index]",
                    "Profile '$profile' must contain only letters, digits, '.', '_', and '-'",
                )
            }
        }

        ApplicationConfigurationReader(collector).read(input.applicationConfigurations, input.activeProfiles)

        val propertiesScanner = ConfigurationPropertiesScanner(collector)
        val valueScanner = ValueAnnotationScanner(collector)
        for (type in input.classes.distinct().sortedBy { it.name }) {
            try {
                propertiesScanner.scan(type)
                valueScanner.scan(type)
            } catch (error: LinkageError) {
                collector.unsupported(type.name, "Class cannot be inspected (${error.javaClass.simpleName}: ${error.message})")
            } catch (exception: TypeNotPresentException) {
                collector.unsupported(type.name, "Class cannot be inspected (${exception.message})")
            }
        }

        if (collector.problems.isNotEmpty()) {
            throw SpringDiscoveryException(
                collector.problems.sortedWith(compareBy(SOURCE_ORDER) { it: SpringDiscoveryProblem -> it.source }.thenBy { it.message }),
            )
        }
        return SpringDiscoveryResult(
            requirements = merge(collector.occurrences),
            diagnostics =
                collector.diagnostics
                    .distinct()
                    .sortedWith(
                        compareBy(SOURCE_ORDER) { it: SpringDiscoveryDiagnostic -> it.source }
                            .thenBy { it.kind.ordinal }
                            .thenBy { it.message },
                    ),
        )
    }

    private fun merge(occurrences: List<KeyOccurrence>): List<ConfigurationRequirement> =
        occurrences
            .groupBy { it.key }
            .toSortedMap()
            .map { (key, group) ->
                val byLocation = group.sortedWith(compareBy(SOURCE_ORDER) { it.source })
                val definition = byLocation.firstOrNull { it.origin == KeyOccurrence.Origin.APPLICATION_CONFIGURATION }
                val consumers = byLocation.filter { it.origin != KeyOccurrence.Origin.APPLICATION_CONFIGURATION }
                val required = consumers.filter { it.presence == Presence.REQUIRED }
                val defaultValue =
                    when {
                        definition != null -> definition.defaultValue
                        required.isNotEmpty() -> DefaultValue.Absent
                        else ->
                            consumers
                                .map { it.defaultValue }
                                .filterIsInstance<DefaultValue.Present>()
                                .distinct()
                                .singleOrNull()
                    } ?: DefaultValue.Absent
                ConfigurationRequirement(
                    key = ConfigurationKey.of(key),
                    presence = if (required.isEmpty()) Presence.OPTIONAL else Presence.REQUIRED,
                    defaultValue = defaultValue,
                    source = (required.firstOrNull() ?: definition ?: consumers.first()).source,
                )
            }
}
