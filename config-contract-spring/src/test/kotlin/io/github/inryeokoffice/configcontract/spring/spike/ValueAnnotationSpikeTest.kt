package io.github.inryeokoffice.configcontract.spring.spike

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Issue #17: @Value placeholder lookup, defaults, and missing-key behavior. */
class ValueAnnotationSpikeTest {
    @Test
    fun `kebab-case placeholder resolves an exact property`() {
        SpikeApplication
            .run(
                KebabValueConfiguration::class.java,
                systemProperties = mapOf("spike.value.max-pool-size" to "7"),
            ).use { assertEquals("7", it.getBean(KebabValueConsumer::class.java).value) }
    }

    @Test
    fun `kebab-case placeholder resolves a relaxed environment variable`() {
        SpikeApplication
            .run(
                KebabValueConfiguration::class.java,
                environmentVariables = mapOf("SPIKE_VALUE_MAXPOOLSIZE" to "7"),
            ).use { assertEquals("7", it.getBean(KebabValueConsumer::class.java).value) }
    }

    @Test
    fun `camelCase placeholder does not resolve a kebab-case property`() {
        val error =
            assertThrows<Exception> {
                SpikeApplication
                    .run(
                        CamelCaseValueConfiguration::class.java,
                        systemProperties = mapOf("spike.value.max-pool-size" to "7"),
                    ).close()
            }

        assertTrue(
            SpikeApplication.rootCauseMessage(error).contains("Could not resolve placeholder 'spike.value.maxPoolSize'"),
        )
    }

    @Test
    fun `placeholder defaults distinguish fallback and empty values`() {
        SpikeApplication.run(DefaultValueConfiguration::class.java).use {
            val consumer = it.getBean(DefaultValueConsumer::class.java)
            assertEquals("fallback", consumer.fallback)
            assertEquals("", consumer.empty)
        }
    }

    @Test
    fun `missing placeholder without default fails startup`() {
        val error =
            assertThrows<Exception> {
                SpikeApplication.run(MissingValueConfiguration::class.java).close()
            }

        assertTrue(SpikeApplication.rootCauseMessage(error).contains("Could not resolve placeholder 'spike.value.missing'"))
    }

    @Test
    fun `missing placeholder is injected literally without a placeholder configurer`() {
        SpikeApplication.run(MissingValueWithoutPlaceholderConfigurerConfiguration::class.java).use {
            assertEquals("\${spike.value.missing}", it.getBean(MissingValueConsumer::class.java).value)
        }
    }
}
