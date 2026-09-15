package io.github.inryeokoffice.configcontract.spring.spike

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Issue #17: application configuration file resolution, placeholders, and profiles. */
class ApplicationConfigurationSpikeTest {
    private fun javaBean(
        configLocation: String,
        environmentVariables: Map<String, String> = emptyMap(),
    ): JavaBeanProperties =
        SpikeApplication
            .run(
                PropertiesConfiguration::class.java,
                environmentVariables = environmentVariables,
                configLocation = configLocation,
            ).use { it.getBean(JavaBeanProperties::class.java) }

    @Test
    fun `properties file wins over yaml file in the same location while yaml-only keys remain`() {
        val properties = javaBean("classpath:/spike/precedence/")

        assertEquals("from-properties", properties.name)
        assertEquals(1, properties.maxPoolSize)
    }

    @Test
    fun `environment variable overrides application configuration files`() {
        val properties =
            javaBean(
                "classpath:/spike/precedence/",
                environmentVariables = mapOf("SPIKE_JAVABEAN_NAME" to "from-environment"),
            )

        assertEquals("from-environment", properties.name)
    }

    @Test
    fun `unresolved placeholder in a file binds literally into configuration properties`() {
        val properties = javaBean("classpath:/spike/placeholders/")

        assertEquals("\${SPIKE_DATABASE_NAME}", properties.name)
    }

    @Test
    fun `placeholder in a file resolves from an environment variable`() {
        val properties =
            javaBean(
                "classpath:/spike/placeholders/",
                environmentVariables = mapOf("SPIKE_DATABASE_NAME" to "orders"),
            )

        assertEquals("orders", properties.name)
    }

    @Test
    fun `unresolved placeholder in a file fails startup through Value`() {
        val error =
            assertThrows<Exception> {
                SpikeApplication
                    .run(FileValueConfiguration::class.java, configLocation = "classpath:/spike/placeholders/")
                    .close()
            }

        assertTrue(SpikeApplication.rootCauseMessage(error).contains("Could not resolve placeholder 'SPIKE_DATABASE_NAME'"))
    }

    @Test
    fun `placeholder default in a file is used through Value`() {
        SpikeApplication
            .run(FileDefaultValueConfiguration::class.java, configLocation = "classpath:/spike/placeholders/")
            .use { assertEquals("from-file-default", it.getBean(FileDefaultValueConsumer::class.java).withDefault) }
    }

    @Test
    fun `no active profile uses the base document only`() {
        val properties = javaBean("classpath:/spike/profiles/")

        assertEquals("base", properties.name)
        assertNull(properties.maxPoolSize)
    }

    @Test
    fun `profile-specific file overrides base values and adds profile-only placeholders`() {
        val properties =
            javaBean(
                "classpath:/spike/profiles/",
                environmentVariables = mapOf("SPRING_PROFILES_ACTIVE" to "prod", "SPIKE_PROD_POOL_SIZE" to "20"),
            )

        assertEquals("prod-file", properties.name)
        assertEquals(20, properties.maxPoolSize)
    }

    @Test
    fun `profile-activated document inside application yaml overrides base values`() {
        val properties =
            javaBean(
                "classpath:/spike/profiles/",
                environmentVariables = mapOf("SPRING_PROFILES_ACTIVE" to "staging"),
            )

        assertEquals("staging-document", properties.name)
    }
}
