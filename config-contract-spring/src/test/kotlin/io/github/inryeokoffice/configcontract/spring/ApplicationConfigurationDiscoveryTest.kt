package io.github.inryeokoffice.configcontract.spring

import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.DefaultValue
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.Presence
import io.github.inryeokoffice.configcontract.spring.SpringDiscoveryDiagnostic.Kind
import io.github.inryeokoffice.configcontract.spring.fixtures.KotlinValueFixture
import io.github.inryeokoffice.configcontract.spring.fixtures.RequiredStringProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ApplicationConfigurationDiscoveryTest {
    private val placeholders = configurationSources("discovery/placeholders")
    private val placeholdersFile = "discovery/placeholders/application.yml"

    @Test
    fun `configured keys are optional with the configured text as default`() {
        val result = discover(configurations = placeholders)

        assertEquals(
            requirement("fixture.plain", Presence.OPTIONAL, DefaultValue.Present("plain-value"), "$placeholdersFile:7"),
            result.requirement("fixture.plain"),
        )
    }

    @Test
    fun `placeholders in configuration values are requirements anchored to their line`() {
        val result = discover(configurations = placeholders)

        assertEquals(
            requirement("FIXTURE_DATABASE_NAME", Presence.REQUIRED, source = "$placeholdersFile:3"),
            result.requirement("FIXTURE_DATABASE_NAME"),
        )
        assertEquals(
            requirement("FIXTURE_DATABASE_USER", Presence.OPTIONAL, DefaultValue.Present("app"), "$placeholdersFile:4"),
            result.requirement("FIXTURE_DATABASE_USER"),
        )
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `key holding an unresolved placeholder keeps the raw text Spring would bind`() {
        val result = discover(configurations = placeholders)

        assertEquals(DefaultValue.Present("\${FIXTURE_DATABASE_NAME}"), result.requirement("fixture.database.name").defaultValue)
    }

    @Test
    fun `profile documents apply only when the profile is explicitly active`() {
        assertFalse(discover(configurations = placeholders).hasRequirement("FIXTURE_PROD_POOL_SIZE"))

        val prod = discover(configurations = placeholders, profiles = listOf("prod"))

        assertEquals(Presence.REQUIRED, prod.requirement("FIXTURE_PROD_POOL_SIZE").presence)
    }

    @Test
    fun `configured value satisfies a Value placeholder as its default while it stays required`() {
        val result = discover(KotlinValueFixture::class.java, configurations = placeholders)
        val requirement = result.requirement("fixture.value.required")

        assertEquals(Presence.REQUIRED, requirement.presence)
        assertEquals(DefaultValue.Present("configured-in-file"), requirement.defaultValue)
        assertEquals("${KotlinValueFixture::class.java.name}.<init>(required)", requirement.source?.location)
    }

    @Test
    fun `configured value backs a required Kotlin property`() {
        val configuration = ApplicationConfigurationSource("application.properties", "crosscheck.required-string.value=configured\n")
        val result = discover(RequiredStringProperties::class.java, configurations = listOf(configuration))
        val requirement = result.requirement("crosscheck.required-string.value")

        assertEquals(Presence.REQUIRED, requirement.presence)
        assertEquals(DefaultValue.Present("configured"), requirement.defaultValue)
    }

    @Test
    fun `properties content outside ISO-8859-1 is preserved`() {
        val configuration = ApplicationConfigurationSource("application.properties", "fixture.greeting=안녕 ✓\n")

        assertEquals(
            DefaultValue.Present("안녕 ✓"),
            discover(configurations = listOf(configuration)).requirement("fixture.greeting").defaultValue,
        )
    }

    @Test
    fun `profile-changing, import, and non-plain activation properties are reported and not applied`() {
        val result = discover(configurations = configurationSources("discovery/unsupported"))
        val file = "discovery/unsupported/application.yml"

        assertEquals(
            listOf(
                "$file:3" to Kind.UNSUPPORTED,
                "$file:6" to Kind.UNSUPPORTED,
                "$file:13" to Kind.UNSUPPORTED,
                "$file:20" to Kind.UNSUPPORTED,
            ),
            result.diagnostics.map { it.source.location to it.kind },
        )
        assertTrue(result.hasRequirement("fixture.base"))
        assertFalse(result.hasRequirement("fixture.not-prod"))
        assertFalse(result.hasRequirement("fixture.cloud"))
    }

    @Test
    fun `malformed YAML is rejected with its source name`() {
        val configuration = ApplicationConfigurationSource("config/application.yml", "fixture:\n  key: [unclosed\n")

        val exception = assertThrows<SpringDiscoveryException> { discover(configurations = listOf(configuration)) }

        assertEquals(listOf("config/application.yml"), exception.problems.map { it.source.location })
    }

    @Test
    fun `unsupported file names and ambiguous inputs are rejected`() {
        val exception =
            assertThrows<SpringDiscoveryException> {
                discover(
                    configurations =
                        listOf(
                            ApplicationConfigurationSource("bootstrap.yml", ""),
                            ApplicationConfigurationSource("a/application.yml", ""),
                            ApplicationConfigurationSource("b/application.yml", ""),
                            ApplicationConfigurationSource("application-prod.yml", ""),
                            ApplicationConfigurationSource("application-prod.yaml", ""),
                        ),
                    profiles = listOf("prod,staging"),
                )
            }

        assertEquals(
            listOf("a/application.yml", "activeProfiles[0]", "application-prod.yml", "bootstrap.yml"),
            exception.problems.map { it.source.location },
        )
    }

    @Test
    fun `source names must be project-relative`() {
        assertThrows<IllegalArgumentException> { ApplicationConfigurationSource("/abs/application.yml", "") }
        assertThrows<IllegalArgumentException> { ApplicationConfigurationSource("C:/abs/application.yml", "") }
        assertThrows<IllegalArgumentException> { ApplicationConfigurationSource("config\\application.yml", "") }
        assertThrows<IllegalArgumentException> { ApplicationConfigurationSource("../application.yml", "") }
    }

    @Test
    fun `source toString redacts content`() {
        val source = ApplicationConfigurationSource("application.properties", "password=secret")

        assertFalse(source.toString().contains("secret"))
    }

    @Test
    fun `result does not depend on input order`() {
        val classes = listOf(KotlinValueFixture::class.java, RequiredStringProperties::class.java)
        val precedence = configurationSources("discovery/precedence")

        val forward = SpringConfigurationDiscovery.discover(SpringDiscoveryInput(classes, precedence, listOf("prod")))
        val backward =
            SpringConfigurationDiscovery.discover(SpringDiscoveryInput(classes.reversed(), precedence.reversed(), listOf("prod")))

        assertEquals(forward, backward)
    }
}
