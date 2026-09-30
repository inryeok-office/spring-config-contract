package io.github.inryeokoffice.configcontract.spring

import io.github.inryeokoffice.configcontract.core.ConfigurationKey
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement
import io.github.inryeokoffice.configcontract.core.ProvidedConfiguration
import io.github.inryeokoffice.configcontract.core.SourceMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SpringEnvironmentKeysTest {
    private fun requirements(vararg keys: String) = keys.map { ConfigurationRequirement(ConfigurationKey.of(it)) }

    private fun provided(
        key: String,
        location: String = ".env.example:1",
    ) = ProvidedConfiguration(ConfigurationKey.of(key), SourceMetadata(location))

    private fun alignedKeys(
        requirements: List<ConfigurationRequirement>,
        vararg provided: ProvidedConfiguration,
    ) = SpringEnvironmentKeys.align(requirements, provided.toList()).map { it.key.value }

    /** The environment-variable spellings the Issue #17 spike observed binding to one property. */
    @ParameterizedTest
    @ValueSource(strings = ["SPIKE_JAVABEAN_MAXPOOLSIZE", "SPIKE_JAVA_BEAN_MAX_POOL_SIZE", "spike_javabean_maxpoolsize"])
    fun `environment variable spellings align to the canonical property`(variable: String) {
        assertEquals(
            listOf("spike.java-bean.max-pool-size"),
            alignedKeys(requirements("spike.java-bean.max-pool-size"), provided(variable)),
        )
    }

    @Test
    fun `environment-style placeholder is satisfied by the same variable`() {
        assertEquals(listOf("SPIKE_DATABASE_NAME"), alignedKeys(requirements("SPIKE_DATABASE_NAME"), provided("SPIKE_DATABASE_NAME")))
    }

    @Test
    fun `unrelated provided key is returned unchanged with its source`() {
        val entry = provided("UNRELATED_KEY", ".env.example:7")

        assertEquals(listOf(entry), SpringEnvironmentKeys.align(requirements("spike.java-bean.url"), listOf(entry)))
        assertEquals(
            ".env.example:7",
            SpringEnvironmentKeys
                .align(emptyList(), listOf(entry))
                .single()
                .source
                ?.location,
        )
    }

    @Test
    fun `one variable satisfying several requirements is aligned to each of them`() {
        assertEquals(
            listOf("SPRING_DATASOURCE_URL", "spring.datasource.url"),
            alignedKeys(requirements("SPRING_DATASOURCE_URL", "spring.datasource.url"), provided("SPRING_DATASOURCE_URL")),
        )
    }

    @Test
    fun `several spellings of one property collapse to the first provided entry`() {
        val aligned =
            SpringEnvironmentKeys.align(
                requirements("spike.java-bean.max-pool-size"),
                listOf(
                    provided("SPIKE_JAVABEAN_MAXPOOLSIZE", "compose.yml:4"),
                    provided("SPIKE_JAVA_BEAN_MAX_POOL_SIZE", "compose.yml:5"),
                ),
            )

        assertEquals(listOf("compose.yml:4"), aligned.map { it.source?.location })
    }

    @Test
    fun `a similar but different property is not satisfied`() {
        assertEquals(
            listOf("SPIKE_JAVABEAN_MAXPOOL"),
            alignedKeys(requirements("spike.java-bean.max-pool-size"), provided("SPIKE_JAVABEAN_MAXPOOL")),
        )
    }
}
