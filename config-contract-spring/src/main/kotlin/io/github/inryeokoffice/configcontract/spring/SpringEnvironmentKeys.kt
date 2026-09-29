package io.github.inryeokoffice.configcontract.spring

import io.github.inryeokoffice.configcontract.core.ConfigurationKey
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement
import io.github.inryeokoffice.configcontract.core.ProvidedConfiguration
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.core.env.AbstractEnvironment
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource

/**
 * Applies Spring Boot's environment-variable key mapping to deployment input.
 *
 * Deployment formats provide environment-variable names such as
 * `SPRING_DATASOURCE_URL`, while discovered requirements use canonical property
 * names such as `spring.datasource.url`. Core compares keys exactly, so provided
 * keys must be aligned with requirement keys before comparison.
 */
object SpringEnvironmentKeys {
    /**
     * Rewrites each provided key to every requirement key it satisfies.
     *
     * A provided key satisfies a requirement when a Spring Boot environment
     * containing only that key as an environment variable resolves the
     * requirement key, using Spring's own relaxed-binding and
     * environment-variable rules. Provided keys that satisfy no requirement are
     * returned unchanged. When several provided keys map to the same key, the
     * first one in [provided] order is kept, so the result has unique keys and
     * keeps the order of first appearance.
     */
    @JvmStatic
    fun align(
        requirements: Iterable<ConfigurationRequirement>,
        provided: Iterable<ProvidedConfiguration>,
    ): List<ProvidedConfiguration> {
        val requirementKeys = requirements.map { it.key }.distinct()
        val aligned = LinkedHashMap<ConfigurationKey, ProvidedConfiguration>()
        for (entry in provided) {
            val environment = environmentWith(entry.key.value)
            val satisfied = requirementKeys.filter { environment.containsProperty(it.value) }
            for (key in satisfied.ifEmpty { listOf(entry.key) }) {
                aligned.putIfAbsent(key, ProvidedConfiguration(key, entry.source))
            }
        }
        return aligned.values.toList()
    }

    private fun environmentWith(variable: String): AbstractEnvironment {
        // AbstractEnvironment adds no system property sources, so the host machine cannot affect results.
        val environment = object : AbstractEnvironment() {}
        environment.propertySources.addLast(
            SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                mapOf<String, Any>(variable to ""),
            ),
        )
        ConfigurationPropertySources.attach(environment)
        return environment
    }
}
