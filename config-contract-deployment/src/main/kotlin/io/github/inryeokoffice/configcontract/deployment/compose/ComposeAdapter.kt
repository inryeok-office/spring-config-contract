package io.github.inryeokoffice.configcontract.deployment.compose

import io.github.inryeokoffice.configcontract.core.ConfigurationKey
import io.github.inryeokoffice.configcontract.core.ProvidedConfiguration
import io.github.inryeokoffice.configcontract.core.SourceMetadata
import io.github.inryeokoffice.configcontract.deployment.DeploymentInputAdapter
import io.github.inryeokoffice.configcontract.deployment.DeploymentInputException
import io.github.inryeokoffice.configcontract.deployment.DeploymentSource
import io.github.inryeokoffice.configcontract.deployment.sourceLocation

/**
 * Reads Docker Compose [DeploymentSource] content into core provided configuration.
 *
 * Only `services.<name>.environment` is interpreted; every other field (`image`, `ports`,
 * `volumes`, `x-*`, `profiles`, and so on) is ignored. Only key presence is mapped into
 * [ProvidedConfiguration] - environment values are used solely to locate the key and are never
 * exposed, matching the core `ProvidedConfiguration` contract. Source metadata is the source
 * name and the entry's 1-based line, formatted as `name:line`.
 *
 * Supported:
 * - List form: `- KEY=value`, `- KEY=`, and `- KEY` (a passthrough reference, with no value in
 *   the deployment environment; it still counts as provided).
 * - Map form: `KEY: value`, `KEY: ""`, and `KEY:` / `KEY: null` (also passthrough; counts as
 *   provided). Scalar values of any type (string, number, boolean) are accepted.
 * - A null `environment` (omitted value, or explicit `null`) is treated as no configuration, not
 *   an error. Other null-like spellings (for example `~`) are not resolved as null and fall into
 *   the "must be a list or a mapping" problem below - this project does not guess at YAML
 *   resolution behavior it has not verified.
 * - A `null` service body (`web:` with nothing under it) is treated as no configuration.
 * - `$VAR` / `${VAR}` interpolation inside a value is kept as-is; values are never inspected or
 *   exposed, so interpolated values never produce a problem.
 * - YAML anchors and aliases (`&name` / `*name`) other than the merge key. Because
 *   snakeyaml-engine's composer resolves an alias to the exact same node as its anchor, an entry
 *   reached through an alias is reported at the anchor's definition line, not the alias site.
 *
 * Reported as [InputProblem][io.github.inryeokoffice.configcontract.deployment.InputProblem]s
 * (never silently ignored):
 * - `env_file`, `extends`, a top-level `include`, and the YAML merge key (`<<`).
 * - A duplicate key within one service's `environment` (list or map form); the same key in
 *   different services produces separate entries.
 * - Duplicate YAML mapping keys anywhere this adapter reads (service names, a service's own
 *   keys, map-form environment keys).
 * - `environment` that is neither a list nor a mapping, including a scalar built entirely from
 *   interpolation (for example `environment: ${ENV_FILE}`).
 * - A non-string list item, or a nested map/list value in map form.
 * - Interpolation in a key or in a service name.
 * - A document root that is not a YAML mapping, a missing or non-mapping `services`, multiple
 *   YAML documents, and invalid YAML syntax.
 *
 * Empty or entirely-comment content produces an empty result; non-empty content without a
 * `services` mapping is reported as a problem.
 *
 * [read] interprets every service. Use [selecting] to interpret only named services.
 */
object ComposeAdapter : DeploymentInputAdapter {
    /** @throws DeploymentInputException if [source] contains unsupported or malformed Docker Compose syntax. */
    override fun read(source: DeploymentSource): List<ProvidedConfiguration> = readServices(source, services = null)

    /**
     * Returns an adapter that reads only the Docker Compose services named in [services].
     *
     * An empty [services] collection selects every service and returns [ComposeAdapter] itself,
     * without inspecting [sources]. Otherwise, names are matched exactly and treated as a set, so
     * their order and any duplicates do not affect the result.
     *
     * [sources] are the Docker Compose sources the selection applies to. Each one is parsed
     * independently - files are never merged as Compose overlays - and a name is valid if at
     * least one source declares it, so a name may be declared by several sources and need not be
     * declared by all of them. Selecting several services reads their union, which does not show
     * that any one selected service is complete on its own.
     *
     * The returned adapter's [read][DeploymentInputAdapter.read] reports entries of selected
     * services only, in document order and with the same `name:line` source metadata as [read].
     * A selected service without an `environment` contributes nothing and is not an error.
     * Global problems - invalid YAML syntax, multiple documents, a non-mapping root, a missing or
     * non-mapping `services`, a top-level `include`, and invalid, duplicate, or interpolated
     * service names - are still reported for the whole source. Everything inside a service body,
     * including a non-mapping body, `env_file`, `extends`, the merge key (`<<`), and every
     * `environment` problem, is reported only for selected services; unselected services are not
     * interpreted.
     *
     * @throws IllegalArgumentException if [services] is not empty and [sources] is empty, if a
     *   name is blank or contains interpolation (`$`), or if a name is declared by none of
     *   [sources]. Messages list the offending names in sorted order.
     * @throws DeploymentInputException if a source in [sources] has a global problem, since its
     *   service names cannot then be determined.
     */
    @JvmStatic
    fun selecting(
        services: Collection<String>,
        sources: Collection<DeploymentSource>,
    ): DeploymentInputAdapter {
        if (services.isEmpty()) {
            return ComposeAdapter
        }
        val selected = services.toSortedSet()
        require(selected.none { it.isBlank() }) {
            "Selected Docker Compose service names must not be blank"
        }
        val interpolated = selected.filter { it.contains('$') }
        require(interpolated.isEmpty()) {
            "Selected Docker Compose service names must not contain interpolation: ${quoted(interpolated)}"
        }
        require(sources.isNotEmpty()) {
            "Docker Compose services are selected (${quoted(selected)}) but no Docker Compose files are configured"
        }
        val declared = sources.flatMapTo(sortedSetOf()) { ComposeParser.serviceNames(it.name, it.content) }
        val unknown = selected - declared
        require(unknown.isEmpty()) {
            "Selected Docker Compose services are not declared in any configured Docker Compose file: " +
                "${quoted(unknown)} (declared services: ${quoted(declared).ifEmpty { "none" }})"
        }
        return SelectedServices(selected)
    }

    private fun quoted(names: Iterable<String>): String = names.joinToString { "'$it'" }

    private fun readServices(
        source: DeploymentSource,
        services: Set<String>?,
    ): List<ProvidedConfiguration> =
        ComposeParser.parse(source.name, source.content, services).map { entry ->
            ProvidedConfiguration(
                ConfigurationKey.of(entry.key),
                SourceMetadata(sourceLocation(source.name, entry.line)),
            )
        }

    private class SelectedServices(
        private val services: Set<String>,
    ) : DeploymentInputAdapter {
        override fun read(source: DeploymentSource): List<ProvidedConfiguration> = readServices(source, services)

        override fun toString(): String = "ComposeAdapter.selecting(${quoted(services)})"
    }
}
