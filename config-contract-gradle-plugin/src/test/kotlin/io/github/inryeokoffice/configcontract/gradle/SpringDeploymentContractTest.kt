package io.github.inryeokoffice.configcontract.gradle

import io.github.inryeokoffice.configcontract.core.ConfigurationKey
import io.github.inryeokoffice.configcontract.core.ContractFinding
import io.github.inryeokoffice.configcontract.core.SourceMetadata
import io.github.inryeokoffice.configcontract.deployment.DeploymentSource
import io.github.inryeokoffice.configcontract.deployment.compose.ComposeAdapter
import io.github.inryeokoffice.configcontract.deployment.dotenv.DotenvExampleAdapter
import io.github.inryeokoffice.configcontract.spring.SpringConfigurationDiscovery
import io.github.inryeokoffice.configcontract.spring.SpringDiscoveryInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value

class SpringDeploymentContractTest {
    @Test
    fun `dotenv input satisfies discovered requirements`() {
        val result = evaluate(dotenv("APP_REQUIRED=value\n"), Required::class.java)

        assertEquals(emptyList<ContractFinding>(), result.findings)
    }

    @Test
    fun `compose input satisfies discovered requirements`() {
        val result =
            evaluate(
                compose(
                    """
                    services:
                      app:
                        environment:
                          APP_REQUIRED: value
                    """.trimIndent(),
                ),
                Required::class.java,
            )

        assertEquals(emptyList<ContractFinding>(), result.findings)
    }

    @Test
    fun `missing optional and defaulted requirements do not produce findings`() {
        val result = evaluate(dotenv("UNUSED_VALUE=value\n"))

        assertEquals(
            listOf(
                ContractFinding(
                    ContractFinding.Kind.UNUSED,
                    ConfigurationKey.of("UNUSED_VALUE"),
                    SourceMetadata("config/.env.example:1"),
                ),
                ContractFinding(ContractFinding.Kind.MISSING, ConfigurationKey.of("app.required"), requiredSource()),
                ContractFinding(
                    ContractFinding.Kind.MISSING,
                    ConfigurationKey.of("spring.datasource.url"),
                    datasourceUrlSource(),
                ),
            ),
            result.findings,
        )
    }

    @Test
    fun `environment-style deployment keys align with Spring requirement keys`() {
        val result = evaluate(dotenv("APP_REQUIRED=value\nSPRING_DATASOURCE_URL=jdbc:postgresql://db/app\n"))

        assertEquals(emptyList<ContractFinding>(), result.findings)
    }

    @Test
    fun `discovery diagnostics are preserved in the composition result`() {
        val input = SpringDeploymentContractInput(SpringDiscoveryInput(listOf(Requirements::class.java)))

        assertEquals(
            SpringConfigurationDiscovery.discover(input.spring).diagnostics,
            SpringDeploymentContract.evaluate(input).diagnostics,
        )
    }

    @Test
    fun `results are deterministic for the same inputs`() {
        val input =
            SpringDeploymentContractInput(
                SpringDiscoveryInput(listOf(Requirements::class.java)),
                listOf(dotenv("UNUSED_VALUE=value\n")),
            )

        assertEquals(SpringDeploymentContract.evaluate(input), SpringDeploymentContract.evaluate(input))
    }

    @Test
    fun `duplicate deployment keys retain the first aligned source`() {
        val result =
            SpringDeploymentContract.evaluate(
                SpringDeploymentContractInput(
                    SpringDiscoveryInput(listOf(Requirements::class.java)),
                    listOf(
                        dotenv("UNUSED_VALUE=first\n", "config/first.env.example"),
                        dotenv("UNUSED_VALUE=second\n", "config/second.env.example"),
                    ),
                ),
            )

        assertEquals(
            SourceMetadata("config/first.env.example:1"),
            result.findings.single { it.kind == ContractFinding.Kind.UNUSED }.source,
        )
    }

    private fun evaluate(
        deploymentInput: DeploymentContractInput,
        type: Class<*> = Requirements::class.java,
    ): SpringDeploymentContractResult =
        SpringDeploymentContract.evaluate(
            SpringDeploymentContractInput(
                SpringDiscoveryInput(listOf(type)),
                listOf(deploymentInput),
            ),
        )

    private fun dotenv(
        content: String,
        name: String = "config/.env.example",
    ): DeploymentContractInput = DeploymentContractInput(DotenvExampleAdapter, DeploymentSource(name, content))

    private fun compose(content: String): DeploymentContractInput =
        DeploymentContractInput(ComposeAdapter, DeploymentSource("compose.yaml", "$content\n"))

    private fun requiredSource() = SourceMetadata("${Requirements::class.java.name}.required")

    private fun datasourceUrlSource() = SourceMetadata("${Requirements::class.java.name}.datasourceUrl")

    private class Required {
        @field:Value("\${app.required}")
        lateinit var required: String
    }

    private class Requirements {
        @field:Value("\${app.required}")
        lateinit var required: String

        @field:Value("\${app.optional:default}")
        lateinit var optional: String

        @field:Value("\${app.defaulted:default}")
        lateinit var defaulted: String

        @field:Value("\${spring.datasource.url}")
        lateinit var datasourceUrl: String

        @field:Value("#{systemProperties['unsupported']}")
        lateinit var unsupported: String
    }
}
