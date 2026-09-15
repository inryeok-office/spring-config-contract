package io.github.inryeokoffice.configcontract.deployment.dotenv

import io.github.inryeokoffice.configcontract.core.ConfigurationKey
import io.github.inryeokoffice.configcontract.core.ProvidedConfiguration
import io.github.inryeokoffice.configcontract.core.SourceMetadata
import io.github.inryeokoffice.configcontract.deployment.DeploymentInputAdapter
import io.github.inryeokoffice.configcontract.deployment.DeploymentInputException
import io.github.inryeokoffice.configcontract.deployment.DeploymentSource

/**
 * Reads `.env.example` [DeploymentSource] content into core provided configuration.
 *
 * Only key presence is mapped into [ProvidedConfiguration]; parsed dotenv
 * values are used solely to validate syntax and are never exposed, matching
 * the core `ProvidedConfiguration` contract, which tracks presence rather
 * than secret-bearing values. Source metadata is the source name and the
 * entry's 1-based line, formatted as `name:line`.
 */
object DotenvExampleAdapter : DeploymentInputAdapter {
    /** @throws DeploymentInputException if [source] contains unsupported or malformed dotenv syntax. */
    override fun read(source: DeploymentSource): List<ProvidedConfiguration> =
        DotenvParser.parse(source.name, source.content).map { entry ->
            ProvidedConfiguration(
                ConfigurationKey.of(entry.key),
                SourceMetadata("${source.name}:${entry.line}"),
            )
        }
}
