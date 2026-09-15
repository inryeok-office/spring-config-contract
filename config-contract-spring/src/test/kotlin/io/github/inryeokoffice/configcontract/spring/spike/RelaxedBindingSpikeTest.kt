package io.github.inryeokoffice.configcontract.spring.spike

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Issue #17: which source key spellings bind to one @ConfigurationProperties property. */
class RelaxedBindingSpikeTest {
    private fun maxPoolSize(
        environmentVariables: Map<String, String> = emptyMap(),
        systemProperties: Map<String, String> = emptyMap(),
    ): Int? =
        SpikeApplication
            .run(
                PropertiesConfiguration::class.java,
                environmentVariables = environmentVariables,
                systemProperties = systemProperties,
            ).use { it.getBean(JavaBeanProperties::class.java).maxPoolSize }

    @Test
    fun `canonical kebab-case property binds`() {
        assertEquals(7, maxPoolSize(systemProperties = mapOf("spike.java-bean.max-pool-size" to "7")))
    }

    @Test
    fun `camelCase property binds`() {
        assertEquals(7, maxPoolSize(systemProperties = mapOf("spike.java-bean.maxPoolSize" to "7")))
    }

    @Test
    fun `underscore property binds`() {
        assertEquals(7, maxPoolSize(systemProperties = mapOf("spike.java-bean.max_pool_size" to "7")))
    }

    @Test
    fun `environment variable with dashes removed binds`() {
        assertEquals(7, maxPoolSize(environmentVariables = mapOf("SPIKE_JAVABEAN_MAXPOOLSIZE" to "7")))
    }

    @Test
    fun `environment variable with dashes replaced by underscores binds`() {
        assertEquals(7, maxPoolSize(environmentVariables = mapOf("SPIKE_JAVA_BEAN_MAX_POOL_SIZE" to "7")))
    }

    @Test
    fun `lowercase environment variable binds`() {
        assertEquals(7, maxPoolSize(environmentVariables = mapOf("spike_javabean_maxpoolsize" to "7")))
    }
}
