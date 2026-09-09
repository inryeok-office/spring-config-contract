package io.github.inryeokoffice.configcontract.core

import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.DefaultValue
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.Presence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ConfigurationContractComparisonTest {
    @Test
    fun `required provided and defaulted provided keys have no findings`() {
        val required = requirement("app.required")
        val defaulted = requirement("app.defaulted", defaultValue = DefaultValue.Present("fallback"))

        assertEquals(
            emptyList<ContractFinding>(),
            compare(listOf(required, defaulted), listOf(provided("app.required"), provided("app.defaulted"))),
        )
    }

    @Test
    fun `missing required key is reported with requirement source`() {
        val source = SourceMetadata("application.yml:4")

        assertEquals(
            listOf(ContractFinding(ContractFinding.Kind.MISSING, key("app.required"), source)),
            compare(listOf(requirement("app.required", source = source)), emptyList()),
        )
    }

    @Test
    fun `optional and defaulted missing keys are not reported`() {
        val requirements =
            listOf(
                requirement("app.optional", Presence.OPTIONAL),
                requirement("app.defaulted", defaultValue = DefaultValue.Present("")),
            )

        assertEquals(emptyList<ContractFinding>(), compare(requirements, emptyList()))
    }

    @Test
    fun `provided optional key is known and not unused`() {
        assertEquals(
            emptyList<ContractFinding>(),
            compare(listOf(requirement("app.optional", Presence.OPTIONAL)), listOf(provided("app.optional"))),
        )
    }

    @Test
    fun `unknown provided key is reported with provided source`() {
        val source = SourceMetadata(".env.example:2")

        assertEquals(
            listOf(ContractFinding(ContractFinding.Kind.UNUSED, key("app.unknown"), source)),
            compare(emptyList(), listOf(provided("app.unknown", source))),
        )
    }

    @Test
    fun `findings have deterministic key ordering regardless of input order`() {
        val requirements = listOf(requirement("z.required"), requirement("a.required"))
        val provided = listOf(provided("z.unknown"), provided("a.unknown"))
        val expected =
            listOf(
                ContractFinding(ContractFinding.Kind.MISSING, key("a.required"), null),
                ContractFinding(ContractFinding.Kind.UNUSED, key("a.unknown"), null),
                ContractFinding(ContractFinding.Kind.MISSING, key("z.required"), null),
                ContractFinding(ContractFinding.Kind.UNUSED, key("z.unknown"), null),
            )

        assertEquals(expected, compare(requirements, provided))
        assertEquals(expected, compare(requirements.reversed(), provided.reversed()))
    }

    @Test
    fun `empty inputs produce deterministic empty result`() {
        assertEquals(emptyList<ContractFinding>(), compare(emptyList(), emptyList()))
    }

    @Test
    fun `duplicate requirement and provided keys are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            compare(listOf(requirement("app.key"), requirement("app.key")), emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(emptyList(), listOf(provided("app.key"), provided("app.key")))
        }
    }

    @Test
    fun `invalid states are rejected by the domain model`() {
        assertThrows(IllegalArgumentException::class.java) { ConfigurationKey.of("app key") }
        assertThrows(IllegalArgumentException::class.java) { SourceMetadata(" ") }
    }

    private fun key(value: String) = ConfigurationKey.of(value)

    private fun requirement(
        value: String,
        presence: Presence = Presence.REQUIRED,
        defaultValue: DefaultValue = DefaultValue.Absent,
        source: SourceMetadata? = null,
    ) = ConfigurationRequirement(key(value), presence, defaultValue, source)

    private fun provided(
        value: String,
        source: SourceMetadata? = null,
    ) = ProvidedConfiguration(key(value), source)

    private fun compare(
        requirements: Iterable<ConfigurationRequirement>,
        provided: Iterable<ProvidedConfiguration>,
    ) = ConfigurationContractComparison.compare(requirements, provided)
}
