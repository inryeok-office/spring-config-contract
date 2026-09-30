package io.github.inryeokoffice.configcontract.gradle

import io.github.inryeokoffice.configcontract.core.ConfigurationContractComparison
import io.github.inryeokoffice.configcontract.core.ContractFinding
import io.github.inryeokoffice.configcontract.deployment.DeploymentInputAdapter
import io.github.inryeokoffice.configcontract.deployment.DeploymentSource
import io.github.inryeokoffice.configcontract.spring.SpringConfigurationDiscovery
import io.github.inryeokoffice.configcontract.spring.SpringDiscoveryDiagnostic
import io.github.inryeokoffice.configcontract.spring.SpringDiscoveryInput
import io.github.inryeokoffice.configcontract.spring.SpringEnvironmentKeys

/** One deployment source and the adapter that interprets it. */
data class DeploymentContractInput(
    val adapter: DeploymentInputAdapter,
    val source: DeploymentSource,
)

/** Everything needed to evaluate one Spring configuration contract. */
class SpringDeploymentContractInput
    @JvmOverloads
    constructor(
        val spring: SpringDiscoveryInput,
        deploymentInputs: Iterable<DeploymentContractInput> = emptyList(),
    ) {
        val deploymentInputs: List<DeploymentContractInput> = deploymentInputs.toList()
    }

/** The programmatic result of evaluating Spring and deployment configuration together. */
data class SpringDeploymentContractResult(
    val findings: List<ContractFinding>,
    val diagnostics: List<SpringDiscoveryDiagnostic>,
)

/** Composes Spring discovery, deployment adapters, and core comparison without adding product rules. */
object SpringDeploymentContract {
    /**
     * Evaluates [input] using the existing Spring, deployment, and core contracts.
     *
     * Deployment inputs are read in input order. Spring environment-key alignment
     * retains the first provided entry for a key, as defined by [SpringEnvironmentKeys].
     * Discovery and deployment failures are propagated through their existing exception types.
     */
    @JvmStatic
    fun evaluate(input: SpringDeploymentContractInput): SpringDeploymentContractResult {
        val discovery = SpringConfigurationDiscovery.discover(input.spring)
        val provided = input.deploymentInputs.flatMap { it.adapter.read(it.source) }
        val aligned = SpringEnvironmentKeys.align(discovery.requirements, provided)
        return SpringDeploymentContractResult(
            findings = ConfigurationContractComparison.compare(discovery.requirements, aligned),
            diagnostics = discovery.diagnostics,
        )
    }
}
