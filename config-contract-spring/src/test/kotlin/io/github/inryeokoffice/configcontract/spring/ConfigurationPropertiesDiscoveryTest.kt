package io.github.inryeokoffice.configcontract.spring

import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.DefaultValue
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.Presence
import io.github.inryeokoffice.configcontract.spring.SpringDiscoveryDiagnostic.Kind
import io.github.inryeokoffice.configcontract.spring.fixtures.BeanMethodFixture
import io.github.inryeokoffice.configcontract.spring.fixtures.InvalidPrefixFixture
import io.github.inryeokoffice.configcontract.spring.fixtures.JavaBeanFixture
import io.github.inryeokoffice.configcontract.spring.fixtures.JavaRecordFixture
import io.github.inryeokoffice.configcontract.spring.fixtures.KotlinConstructorFixture
import io.github.inryeokoffice.configcontract.spring.fixtures.KotlinMutableFixture
import io.github.inryeokoffice.configcontract.spring.fixtures.ValidatedFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConfigurationPropertiesDiscoveryTest {
    @Test
    fun `java bean setters become optional canonical keys without evaluating field initializers`() {
        val result = discover(JavaBeanFixture::class.java)
        val type = JavaBeanFixture::class.java.name

        assertEquals(
            listOf(
                requirement("fixture.java-bean.max-pool-size", Presence.OPTIONAL, source = "$type#maxPoolSize"),
                requirement("fixture.java-bean.url", Presence.OPTIONAL, source = "$type#url"),
            ),
            result.requirements,
        )
    }

    @Test
    fun `java bean collection property is reported as unsupported and read-only property is ignored`() {
        val result = discover(JavaBeanFixture::class.java)
        val type = JavaBeanFixture::class.java.name

        assertEquals(listOf(Kind.UNSUPPORTED), result.diagnosticsAt("$type#hosts").map { it.kind })
        assertTrue(result.diagnosticsAt("$type#description").isEmpty())
        assertFalse(result.hasRequirement("fixture.java-bean.description"))
    }

    @Test
    fun `java record components are optional and record DefaultValue and Name`() {
        val result = discover(JavaRecordFixture::class.java)
        val type = JavaRecordFixture::class.java.name

        assertEquals(
            listOf(
                requirement("fixture.java-record.pool", Presence.OPTIONAL, source = "$type#poolName"),
                requirement("fixture.java-record.retries", Presence.OPTIONAL, DefaultValue.Present("5"), "$type#retries"),
                requirement("fixture.java-record.url", Presence.OPTIONAL, source = "$type#url"),
            ),
            result.requirements,
        )
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `kotlin constructor parameters are required only when non-null and without a default`() {
        val result = discover(KotlinConstructorFixture::class.java)
        val type = KotlinConstructorFixture::class.java.name

        assertEquals(
            listOf(
                requirement("fixture.kotlin.name", Presence.OPTIONAL, DefaultValue.Present("fixture-name"), "$type#name"),
                requirement("fixture.kotlin.nullable-url", Presence.OPTIONAL, source = "$type#nullableUrl"),
                requirement("fixture.kotlin.required-port", Presence.REQUIRED, source = "$type#requiredPort"),
                requirement("fixture.kotlin.required-url", Presence.REQUIRED, source = "$type#requiredUrl"),
                requirement("fixture.kotlin.retries", Presence.OPTIONAL, source = "$type#retries"),
            ),
            result.requirements,
        )
    }

    @Test
    fun `kotlin nested and collection constructor parameters are reported as unsupported`() {
        val result = discover(KotlinConstructorFixture::class.java)
        val type = KotlinConstructorFixture::class.java.name

        assertEquals(listOf(Kind.UNSUPPORTED), result.diagnosticsAt("$type#nested").map { it.kind })
        assertEquals(listOf(Kind.UNSUPPORTED), result.diagnosticsAt("$type#tags").map { it.kind })
        assertFalse(result.hasRequirement("fixture.kotlin.nested"))
        assertFalse(result.hasRequirement("fixture.kotlin.tags"))
    }

    @Test
    fun `kotlin mutable properties are optional including lateinit`() {
        val result = discover(KotlinMutableFixture::class.java)

        assertEquals(
            listOf("fixture.kotlin-mutable.retries", "fixture.kotlin-mutable.timeout", "fixture.kotlin-mutable.url"),
            result.requirements.map { it.key.value },
        )
        assertTrue(result.requirements.all { it.presence == Presence.OPTIONAL && it.defaultValue == DefaultValue.Absent })
    }

    @Test
    fun `Validated is reported as unsupported while keys are still discovered`() {
        val result = discover(ValidatedFixture::class.java)

        assertEquals(listOf(Kind.UNSUPPORTED), result.diagnosticsAt(ValidatedFixture::class.java.name).map { it.kind })
        assertEquals(Presence.OPTIONAL, result.requirement("fixture.validated.url").presence)
    }

    @Test
    fun `ConfigurationProperties on a Bean method is reported as unsupported`() {
        val result = discover(BeanMethodFixture::class.java)

        assertTrue(result.requirements.isEmpty())
        assertEquals(
            listOf(Kind.UNSUPPORTED),
            result.diagnosticsAt("${BeanMethodFixture::class.java.name}.beanMethodProperties()").map { it.kind },
        )
    }

    @Test
    fun `non-canonical prefix is rejected as Spring Boot would reject it`() {
        val exception = assertThrows<SpringDiscoveryException> { discover(InvalidPrefixFixture::class.java) }

        assertEquals(listOf(InvalidPrefixFixture::class.java.name), exception.problems.map { it.source.location })
        assertTrue(
            exception.problems
                .single()
                .message
                .contains("fixture.invalidPrefix"),
        )
    }

    @Test
    fun `classes without Spring annotations produce nothing`() {
        val result = discover(String::class.java, Any::class.java)

        assertTrue(result.requirements.isEmpty())
        assertTrue(result.diagnostics.isEmpty())
    }
}
