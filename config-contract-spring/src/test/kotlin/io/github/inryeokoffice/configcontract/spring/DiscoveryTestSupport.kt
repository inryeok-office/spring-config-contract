package io.github.inryeokoffice.configcontract.spring

import io.github.inryeokoffice.configcontract.core.ConfigurationKey
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.DefaultValue
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.Presence
import io.github.inryeokoffice.configcontract.core.SourceMetadata
import org.junit.jupiter.api.Assertions.fail
import java.io.File

/** Reads every file of a test resource directory as application configuration input. */
internal fun configurationSources(directory: String): List<ApplicationConfigurationSource> {
    val url = DiscoveryTestSupport::class.java.getResource("/$directory") ?: error("Missing test resource directory $directory")
    return File(url.toURI())
        .listFiles()!!
        .sortedBy { it.name }
        .map { ApplicationConfigurationSource("$directory/${it.name}", it.readText()) }
}

internal fun discover(
    vararg classes: Class<*>,
    configurations: List<ApplicationConfigurationSource> = emptyList(),
    profiles: List<String> = emptyList(),
): SpringDiscoveryResult = SpringConfigurationDiscovery.discover(SpringDiscoveryInput(classes.toList(), configurations, profiles))

internal fun SpringDiscoveryResult.requirement(key: String): ConfigurationRequirement =
    requirements.singleOrNull { it.key == ConfigurationKey.of(key) }
        ?: fail<Nothing>("No requirement '$key' in ${requirements.map { it.key }}")

internal fun SpringDiscoveryResult.hasRequirement(key: String): Boolean = requirements.any { it.key.value == key }

internal fun SpringDiscoveryResult.diagnosticsAt(location: String): List<SpringDiscoveryDiagnostic> =
    diagnostics.filter { it.source.location == location }

internal fun requirement(
    key: String,
    presence: Presence,
    defaultValue: DefaultValue = DefaultValue.Absent,
    source: String,
) = ConfigurationRequirement(ConfigurationKey.of(key), presence, defaultValue, SourceMetadata(source))

private object DiscoveryTestSupport
