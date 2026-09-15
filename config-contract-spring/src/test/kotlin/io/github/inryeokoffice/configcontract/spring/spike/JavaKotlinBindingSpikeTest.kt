package io.github.inryeokoffice.configcontract.spring.spike

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Issue #17: missing-property and default-value semantics that differ between Java and Kotlin. */
class JavaKotlinBindingSpikeTest {
    @Test
    fun `missing properties do not fail startup for Java and nullable or defaulted Kotlin types`() {
        SpikeApplication.run(PropertiesConfiguration::class.java).use {
            val javaBean = it.getBean(JavaBeanProperties::class.java)
            assertNull(javaBean.maxPoolSize)
            assertEquals("java-default", javaBean.name)

            val javaRecord = it.getBean(JavaRecordProperties::class.java)
            assertNull(javaRecord.url)
            assertEquals(5, javaRecord.retries)

            val kotlinConstructor = it.getBean(KotlinConstructorProperties::class.java)
            assertNull(kotlinConstructor.url)
            assertEquals(3, kotlinConstructor.retries)

            val kotlinMutable = it.getBean(KotlinMutableProperties::class.java)
            assertFalse(kotlinMutable.hasUrl())
            assertEquals(3, kotlinMutable.retries)
        }
    }

    @Test
    fun `missing Kotlin non-null constructor property without default fails startup`() {
        val error =
            assertThrows<Exception> {
                SpikeApplication.run(KotlinNonNullConfiguration::class.java).close()
            }

        assertTrue(
            SpikeApplication.rootCauseMessage(error).contains("Parameter specified as non-null is null"),
        )
        assertTrue(SpikeApplication.rootCauseMessage(error).endsWith("parameter url"))
    }

    @Test
    fun `supplied Kotlin non-null constructor property binds`() {
        SpikeApplication
            .run(
                KotlinNonNullConfiguration::class.java,
                environmentVariables = mapOf("SPIKE_KOTLINREQUIRED_URL" to "jdbc:test"),
            ).use { assertEquals("jdbc:test", it.getBean(KotlinNonNullProperties::class.java).url) }
    }
}
