package io.github.inryeokoffice.configcontract.spring

import io.github.inryeokoffice.configcontract.core.ConfigurationKey
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.DefaultValue
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.Presence
import io.github.inryeokoffice.configcontract.core.SourceMetadata
import org.springframework.boot.env.PropertiesPropertySourceLoader
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.origin.OriginLookup
import org.springframework.boot.origin.OriginTrackedValue
import org.springframework.boot.origin.TextResourceOrigin
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.PropertySource
import org.springframework.core.io.ByteArrayResource

/**
 * Reads the effective application configuration for explicit active profiles.
 *
 * Files are parsed by Spring Boot's own property source loaders. All sources are
 * treated as one configuration location. Precedence, highest first: files for
 * later active profiles, then earlier ones, then the base files; within one
 * profile `.properties` before `.yml` before `.yaml`; within one file, later
 * documents before earlier ones. A document with
 * `spring.config.activate.on-profile` applies only when one of its plain
 * profile names is active.
 */
internal class ApplicationConfigurationReader(
    private val collector: DiscoveryCollector,
) {
    fun read(
        sources: List<ApplicationConfigurationSource>,
        activeProfiles: List<String>,
    ) {
        val files = sources.mapNotNull(::describe)
        if (!validateUniqueness(files)) return
        val effectiveProfiles = activeProfiles.ifEmpty { listOf(DEFAULT_PROFILE) }

        val documents = files.flatMap(::load)
        val ordered =
            (effectiveProfiles.reversed().distinct() + null).flatMap { profile ->
                EXTENSION_ORDER.flatMap { extensions ->
                    documents
                        .filter { it.file.profile == profile && it.file.extension in extensions }
                        .reversed()
                }
            }

        val effective = linkedMapOf<String, Entry>()
        for (document in ordered.filter { isActive(it, effectiveProfiles) }) {
            reportUnsupportedKeys(document)
            for (entry in document.entries) {
                if (!entry.key.startsWith(ACTIVATE_PREFIX)) effective.putIfAbsent(entry.key, entry)
            }
        }
        effective.values.forEach(::record)
    }

    private fun describe(source: ApplicationConfigurationSource): ConfigurationFile? {
        val match = FILE_NAME.matchEntire(source.fileName)
        if (match == null) {
            collector.problem(
                source.name,
                "Unsupported application configuration file name; expected application[-<profile>].properties, .yml, or .yaml",
            )
            return null
        }
        return ConfigurationFile(source, match.groupValues[1].ifEmpty { null }, match.groupValues[2])
    }

    private fun validateUniqueness(files: List<ConfigurationFile>): Boolean {
        var valid = true
        files.groupBy { it.source.fileName }.filterValues { it.size > 1 }.forEach { (fileName, duplicates) ->
            valid = false
            collector.problem(
                duplicates.first().source.name,
                "Multiple sources are named '$fileName' (${duplicates.joinToString { it.source.name }}); " +
                    "only one configuration location is supported",
            )
        }
        val yamlByProfile = files.filter { it.extension != "properties" }.groupBy { it.profile }
        yamlByProfile.values.filter { group -> group.map { it.extension }.distinct().size > 1 }.forEach {
            valid = false
            collector.problem(it.first().source.name, "Both .yml and .yaml files are present for the same profile; precedence is ambiguous")
        }
        return valid
    }

    private fun load(file: ConfigurationFile): List<Document> {
        val propertySources: List<PropertySource<*>> =
            try {
                if (file.extension == "properties") {
                    PropertiesPropertySourceLoader().load(file.source.name, ByteArrayResource(latin1WithEscapes(file.source.content)))
                } else {
                    YamlPropertySourceLoader().load(file.source.name, ByteArrayResource(file.source.content.toByteArray()))
                }
            } catch (exception: Exception) {
                val reason =
                    exception.message
                        ?.lineSequence()
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                collector.problem(
                    file.source.name,
                    "Cannot read application configuration: ${exception.javaClass.simpleName} $reason".trim(),
                )
                return emptyList()
            }
        return propertySources.map { propertySource ->
            val names = (propertySource as EnumerablePropertySource<*>).propertyNames
            Document(file, names.map { name -> entryOf(file, propertySource, name) })
        }
    }

    private fun entryOf(
        file: ConfigurationFile,
        propertySource: PropertySource<*>,
        name: String,
    ): Entry {
        val raw = propertySource.getProperty(name)
        val value = (if (raw is OriginTrackedValue) raw.value else raw)?.toString().orEmpty()
        val origin = OriginLookup.getOrigin<String>(propertySource, name)
        val location =
            if (origin is TextResourceOrigin && origin.location != null) {
                "${file.source.name}:${origin.location.line + 1}"
            } else {
                file.source.name
            }
        return Entry(name, value, location)
    }

    private fun isActive(
        document: Document,
        profiles: List<String>,
    ): Boolean {
        document.entries.firstOrNull { it.key == LEGACY_PROFILES || it.key.startsWith("$LEGACY_PROFILES[") }?.let {
            collector.unsupported(
                it.location,
                "'$LEGACY_PROFILES' is not a supported document activation property; the document is ignored",
            )
            return false
        }
        document.entries.firstOrNull { matches(it.key, ON_CLOUD_PLATFORM) }?.let {
            collector.unsupported(it.location, "'$ON_CLOUD_PLATFORM' is not analyzed; the document is treated as inactive")
            return false
        }
        val onProfileEntries = document.entries.filter { matches(it.key, ON_PROFILE) }
        val onProfile =
            onProfileEntries
                .flatMap { it.value.split(',') }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        if (onProfile.isEmpty()) return true
        val location = onProfileEntries.first().location
        if (document.file.profile != null) {
            collector.unsupported(
                location,
                "'$ON_PROFILE' in a profile-specific file is not supported; the document is ignored",
            )
            return false
        }
        if (onProfile.any { expression -> expression.any { it in PROFILE_EXPRESSION_CHARACTERS } }) {
            collector.unsupported(
                location,
                "Profile expression '${onProfile.joinToString(",")}' is not supported; the document is treated as inactive",
            )
            return false
        }
        return onProfile.any { it in profiles }
    }

    private fun reportUnsupportedKeys(document: Document) {
        for (entry in document.entries) {
            val property = UNSUPPORTED_PROPERTIES.firstOrNull { matches(entry.key, it) } ?: continue
            collector.unsupported(entry.location, "'$property' is not applied; discovery only uses the explicit input profiles and files")
        }
    }

    private fun record(entry: Entry) {
        val validKey = runCatching { ConfigurationKey.of(entry.key) }.isSuccess
        if (!validKey) {
            collector.unsupported(entry.location, "Key '${entry.key}' is not a valid configuration key; it is not discovered")
            return
        }
        collector.occurrence(
            KeyOccurrence(
                key = entry.key,
                presence = Presence.OPTIONAL,
                defaultValue = DefaultValue.Present(entry.value),
                source = SourceMetadata(entry.location),
                origin = KeyOccurrence.Origin.APPLICATION_CONFIGURATION,
            ),
        )
        for (placeholder in PlaceholderParser.parse(entry.value)) {
            PlaceholderOccurrences.record(collector, placeholder, entry.location, KeyOccurrence.Origin.CONFIGURATION_PLACEHOLDER)
        }
    }

    /** `.properties` files are read as ISO-8859-1, so other characters are passed as `\uXXXX` escapes. */
    private fun latin1WithEscapes(content: String): ByteArray =
        buildString {
            for (char in content) {
                if (char.code > 0xFF) append("\\u%04x".format(char.code)) else append(char)
            }
        }.toByteArray(Charsets.ISO_8859_1)

    private fun matches(
        key: String,
        property: String,
    ): Boolean = key == property || key.startsWith("$property.") || key.startsWith("$property[")

    private data class ConfigurationFile(
        val source: ApplicationConfigurationSource,
        val profile: String?,
        val extension: String,
    )

    private class Document(
        val file: ConfigurationFile,
        val entries: List<Entry>,
    )

    private class Entry(
        val key: String,
        val value: String,
        val location: String,
    )

    private companion object {
        const val DEFAULT_PROFILE = "default"
        const val ACTIVATE_PREFIX = "spring.config.activate."
        const val ON_PROFILE = "spring.config.activate.on-profile"
        const val ON_CLOUD_PLATFORM = "spring.config.activate.on-cloud-platform"
        const val LEGACY_PROFILES = "spring.profiles"
        const val PROFILE_EXPRESSION_CHARACTERS = "!&|() "
        val FILE_NAME = Regex("""application(?:-([^.]+))?\.(properties|yml|yaml)""")
        val EXTENSION_ORDER = listOf(setOf("properties"), setOf("yml", "yaml"))
        val UNSUPPORTED_PROPERTIES =
            listOf(
                "spring.config.import",
                "spring.config.location",
                "spring.config.additional-location",
                "spring.config.name",
                "spring.profiles.active",
                "spring.profiles.include",
                "spring.profiles.default",
                "spring.profiles.group",
            )
    }
}
