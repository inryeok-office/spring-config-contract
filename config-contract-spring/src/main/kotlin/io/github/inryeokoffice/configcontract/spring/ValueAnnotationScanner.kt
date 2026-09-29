package io.github.inryeokoffice.configcontract.spring

import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.DefaultValue
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.Presence
import io.github.inryeokoffice.configcontract.core.SourceMetadata
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.source.ConfigurationPropertyName
import org.springframework.core.DefaultParameterNameDiscoverer
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

/**
 * Discovers requirements from `@Value` declared directly on fields, methods,
 * constructor parameters, and method parameters.
 *
 * `${key}` is required and `${key:default}` is optional with that default,
 * assuming Spring Boot's placeholder auto-configuration is active. SpEL
 * expressions (`#{...}`) are reported as unsupported and not interpreted.
 */
internal class ValueAnnotationScanner(
    private val collector: DiscoveryCollector,
) {
    private val parameterNameDiscoverer = DefaultParameterNameDiscoverer()

    fun scan(type: Class<*>) {
        type.declaredFields
            .filterNot { it.isSynthetic }
            .forEach { field ->
                field.getDeclaredAnnotation(Value::class.java)?.let { inspect("${type.name}.${field.name}", it.value) }
            }

        type.declaredConstructors
            .filterNot { it.isSynthetic }
            .forEach { scanParameters("${type.name}.<init>", it) }

        type.declaredMethods
            .filterNot { it.isSynthetic || it.isBridge }
            .forEach { method ->
                method.getDeclaredAnnotation(Value::class.java)?.let { inspect("${type.name}.${method.name}()", it.value) }
                scanParameters("${type.name}.${method.name}", method)
            }
    }

    private fun scanParameters(
        owner: String,
        executable: Executable,
    ) {
        val parameters = executable.parameters
        if (parameters.none { it.isAnnotationPresent(Value::class.java) }) return
        // Kotlin does not emit Java parameter names by default; Spring's discoverer also reads Kotlin metadata.
        val names = parameterNames(executable)
        parameters.forEachIndexed { index, parameter ->
            val name = names?.getOrNull(index) ?: parameter.name
            parameter.getDeclaredAnnotation(Value::class.java)?.let { inspect("$owner($name)", it.value) }
        }
    }

    private fun parameterNames(executable: Executable): Array<String>? =
        when (executable) {
            is Constructor<*> -> parameterNameDiscoverer.getParameterNames(executable)
            is Method -> parameterNameDiscoverer.getParameterNames(executable)
            else -> null
        }

    private fun inspect(
        location: String,
        expression: String,
    ) {
        if ("#{" in expression) {
            collector.unsupported(location, "SpEL expressions in @Value are not interpreted; no key is discovered")
            return
        }
        for (placeholder in PlaceholderParser.parse(expression)) {
            PlaceholderOccurrences.record(collector, placeholder, location, KeyOccurrence.Origin.CODE)
        }
    }
}

/** Shared handling of placeholders from `@Value` and from configuration values. */
internal object PlaceholderOccurrences {
    private val ENVIRONMENT_STYLE = Regex("[A-Z][A-Z0-9_]*")

    fun record(
        collector: DiscoveryCollector,
        placeholder: Placeholder,
        location: String,
        origin: KeyOccurrence.Origin,
    ) {
        val key = placeholder.key
        if (key.isEmpty() || key.any { it.isWhitespace() || it.isISOControl() } || PlaceholderParser.containsPlaceholder(key)) {
            collector.unsupported(location, "Placeholder key '$key' is not a plain property name; it is not discovered")
            return
        }
        if (!ConfigurationPropertyName.isValid(key) && !ENVIRONMENT_STYLE.matches(key)) {
            collector.nonCanonical(
                location,
                "Placeholder key '$key' is not in canonical kebab-case form, so relaxed binding does not apply; " +
                    "only sources using this exact name or its environment-variable form satisfy it",
            )
        }
        val defaultValue = placeholder.defaultValue
        if (placeholder.hasNestedDefault) {
            collector.unsupported(
                location,
                "Placeholders nested in the default of '$key' are not interpreted; the raw default text is recorded",
            )
        }
        collector.occurrence(
            KeyOccurrence(
                key = key,
                presence = if (defaultValue == null) Presence.REQUIRED else Presence.OPTIONAL,
                defaultValue = if (defaultValue == null) DefaultValue.Absent else DefaultValue.Present(defaultValue),
                source = SourceMetadata(location),
                origin = origin,
            ),
        )
    }
}
