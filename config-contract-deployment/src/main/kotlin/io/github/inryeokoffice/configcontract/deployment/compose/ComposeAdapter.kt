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
 */
object ComposeAdapter : DeploymentInputAdapter {
    /** @throws DeploymentInputException if [source] contains unsupported or malformed Docker Compose syntax. */
    override fun read(source: DeploymentSource): List<ProvidedConfiguration> =
        ComposeParser.parse(source.name, source.content).map { entry ->
            ProvidedConfiguration(
                ConfigurationKey.of(entry.key),
                SourceMetadata(sourceLocation(source.name, entry.line)),
            )
        }
}
