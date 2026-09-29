package io.github.inryeokoffice.configcontract.spring

import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.DefaultValue
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.Presence
import io.github.inryeokoffice.configcontract.spring.SpringDiscoveryDiagnostic.Kind
import io.github.inryeokoffice.configcontract.spring.fixtures.EscapedValueConsumer
import io.github.inryeokoffice.configcontract.spring.fixtures.JavaValueFixture
import io.github.inryeokoffice.configcontract.spring.fixtures.KotlinValueFixture
import io.github.inryeokoffice.configcontract.spring.fixtures.SharedKeyFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValueDiscoveryTest {
    private val kotlinResult = discover(KotlinValueFixture::class.java)

    @Test
    fun `java Value on field, constructor parameter, and setter`() {
        val result = discover(JavaValueFixture::class.java)
        val type = JavaValueFixture::class.java.name

        assertEquals(
            listOf(
                requirement(
                    "fixture.java-value.constructor",
                    Presence.OPTIONAL,
                    DefaultValue.Present("fallback"),
                    "$type.<init>(constructorValue)",
                ),
                requirement("fixture.java-value.field", Presence.REQUIRED, source = "$type.field"),
                requirement("fixture.java-value.setter", Presence.REQUIRED, source = "$type.setSetterValue()"),
            ),
            result.requirements,
        )
    }

    @Test
    fun `placeholder without default is required`() {
        val requirement = kotlinResult.requirement("fixture.value.required")

        assertEquals(Presence.REQUIRED, requirement.presence)
        assertEquals(DefaultValue.Absent, requirement.defaultValue)
    }

    @Test
    fun `placeholder defaults are optional and keep the empty default distinguishable`() {
        assertEquals(DefaultValue.Present("fallback"), kotlinResult.requirement("fixture.value.fallback").defaultValue)
        assertEquals(DefaultValue.Present(""), kotlinResult.requirement("fixture.value.empty").defaultValue)
        assertEquals(Presence.OPTIONAL, kotlinResult.requirement("fixture.value.empty").presence)
    }

    @Test
    fun `camelCase placeholder is kept exactly and reported as non-canonical`() {
        val requirement = kotlinResult.requirement("fixture.value.maxPoolSize")

        assertEquals(Presence.REQUIRED, requirement.presence)
        assertFalse(kotlinResult.hasRequirement("fixture.value.max-pool-size"))
        assertEquals(
            listOf(Kind.NON_CANONICAL_KEY),
            kotlinResult.diagnosticsAt("${KotlinValueFixture::class.java.name}.<init>(camelCase)").map { it.kind },
        )
    }

    @Test
    fun `SpEL expression is reported as unsupported without discovering its placeholder`() {
        assertFalse(kotlinResult.hasRequirement("fixture.value.spel"))
        assertTrue(kotlinResult.diagnostics.any { it.kind == Kind.UNSUPPORTED && "SpEL" in it.message })
    }

    @Test
    fun `every placeholder embedded in literal text is discovered`() {
        assertEquals(Presence.REQUIRED, kotlinResult.requirement("fixture.db.host").presence)
        assertEquals(DefaultValue.Present("5432"), kotlinResult.requirement("fixture.db.port").defaultValue)
    }

    @Test
    fun `placeholder nested in a default keeps the raw default and is reported`() {
        assertEquals(DefaultValue.Present("\${FIXTURE_NESTED}"), kotlinResult.requirement("fixture.value.nested").defaultValue)
        assertFalse(kotlinResult.hasRequirement("FIXTURE_NESTED"))
        assertTrue(kotlinResult.diagnostics.any { it.kind == Kind.UNSUPPORTED && "fixture.value.nested" in it.message })
    }

    @Test
    fun `escaped placeholder is literal text`() {
        val result = discover(EscapedValueConsumer::class.java)

        assertTrue(result.requirements.isEmpty())
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `occurrences of one key merge to the strictest presence and an agreed default`() {
        val result = discover(SharedKeyFixture::class.java)

        assertEquals(Presence.OPTIONAL, result.requirement("fixture.shared.optional").presence)
        assertEquals(DefaultValue.Absent, result.requirement("fixture.shared.optional").defaultValue)
        assertEquals(DefaultValue.Present("same"), result.requirement("fixture.shared.agreed").defaultValue)
        assertEquals(Presence.REQUIRED, result.requirement("fixture.shared.strict").presence)
        assertEquals(DefaultValue.Absent, result.requirement("fixture.shared.strict").defaultValue)
    }
}
