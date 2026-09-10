package io.github.inryeokoffice.configcontract.deployment

import io.github.inryeokoffice.configcontract.core.ProvidedConfiguration

/**
 * Reads a [DeploymentSource] into core provided configuration.
 *
 * Implementations must be pure: no host environment access, shell execution,
 * network access, or file I/O. Callers are responsible for obtaining source
 * content and for any I/O needed to produce it.
 */
fun interface DeploymentInputAdapter {
    /** @throws DeploymentInputException if [source] cannot be read. */
    fun read(source: DeploymentSource): List<ProvidedConfiguration>
}
