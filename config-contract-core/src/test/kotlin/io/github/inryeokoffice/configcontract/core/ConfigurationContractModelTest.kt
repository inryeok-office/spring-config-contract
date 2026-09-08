package io.github.inryeokoffice.configcontract.core

import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.DefaultValue
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.Presence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ConfigurationContractModelTest {
    @Test
    fun `accepts common framework-neutral key syntax`() {
        assertEquals("app.jwt-secret", ConfigurationKey.of("app.jwt-secret").value)
        assertEquals("DB_HOST", ConfigurationKey.of("DB_HOST").toString())
    }

    @Test
    fun `rejects blank or whitespace-containing keys`() {
        assertThrows(IllegalArgumentException::class.java) { ConfigurationKey.of(" ") }
        assertThrows(IllegalArgumentException::class.java) { ConfigurationKey.of("app host") }
        assertThrows(IllegalArgumentException::class.java) { ConfigurationKey.of(" app.host") }
        assertThrows(IllegalArgumentException::class.java) { ConfigurationKey.of("app.host ") }
        assertThrows(IllegalArgumentException::class.java) { ConfigurationKey.of("app.\u0000host") }
    }

    @Test
    fun `requirements use required and absent defaults by default`() {
        val requirement = ConfigurationRequirement(ConfigurationKey.of("app.timeout"))

        assertEquals(Presence.REQUIRED, requirement.presence)
        assertEquals(DefaultValue.Absent, requirement.defaultValue)
        assertEquals(null, requirement.source)
    }

    @Test
    fun `requirements preserve presence default and source semantics`() {
        val source = SourceMetadata("application.yml:4")
        val required =
            ConfigurationRequirement(
                key = ConfigurationKey.of("app.timeout"),
                presence = Presence.REQUIRED,
                defaultValue = DefaultValue.Present(""),
                source = source,
            )
        val optional = required.copy(presence = Presence.OPTIONAL)

        assertEquals(source, required.source)
        assertNotEquals(required, optional)
        assertNotEquals(DefaultValue.Absent, required.defaultValue)
        assertEquals(DefaultValue.Present(""), required.defaultValue)
    }

    @Test
    fun `provided configurations use key as identity and source as diagnostic metadata`() {
        val key = ConfigurationKey.of("DB_HOST")
        val source = SourceMetadata(".env.example:2")

        assertEquals(
            ProvidedConfiguration(key, source),
            ProvidedConfiguration(ConfigurationKey.of("DB_HOST"), SourceMetadata(".env.example:2")),
        )
        assertEquals(ProvidedConfiguration(key), ProvidedConfiguration(key, source))
    }

    @Test
    fun `source metadata rejects blank locations`() {
        assertThrows(IllegalArgumentException::class.java) { SourceMetadata("") }
    }
}
