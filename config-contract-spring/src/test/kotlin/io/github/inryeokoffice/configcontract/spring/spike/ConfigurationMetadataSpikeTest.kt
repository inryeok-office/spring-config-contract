package io.github.inryeokoffice.configcontract.spring.spike

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Issue #17: what spring-boot-configuration-processor records for this module's test sources. */
class ConfigurationMetadataSpikeTest {
    private val metadata: String =
        requireNotNull(javaClass.classLoader.getResource("META-INF/spring-configuration-metadata.json")) {
            "Configuration metadata was not generated"
        }.readText()

    private fun hasProperty(name: String): Boolean = Regex("\"name\"\\s*:\\s*\"${Regex.escape(name)}\"").containsMatchIn(metadata)

    @Test
    fun `Java properties are recorded with canonical names`() {
        assertNotNull(metadata)
        assertTrue(hasProperty("spike.java-bean.max-pool-size"))
        assertTrue(hasProperty("spike.java-bean.name"))
        assertTrue(hasProperty("spike.java-record.url"))
        assertTrue(hasProperty("spike.java-record.retries"))
    }

    @Test
    fun `Java field initializer and DefaultValue defaults are recorded`() {
        assertTrue(Regex("\"defaultValue\"\\s*:\\s*\"java-default\"").containsMatchIn(metadata))
        assertTrue(Regex("\"defaultValue\"\\s*:\\s*5\\b").containsMatchIn(metadata))
    }

    @Test
    fun `Kotlin properties and Value placeholders are not recorded by the Java annotation processor`() {
        assertFalse(hasProperty("spike.kotlin.url"))
        assertFalse(hasProperty("spike.kotlin-mutable.url"))
        assertFalse(hasProperty("spike.value.max-pool-size"))
    }
}
