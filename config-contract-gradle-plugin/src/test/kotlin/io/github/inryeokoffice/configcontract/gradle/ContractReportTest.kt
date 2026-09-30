package io.github.inryeokoffice.configcontract.gradle

import io.github.inryeokoffice.configcontract.core.ConfigurationKey
import io.github.inryeokoffice.configcontract.core.ContractFinding
import io.github.inryeokoffice.configcontract.core.SourceMetadata
import io.github.inryeokoffice.configcontract.spring.SpringDiscoveryDiagnostic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContractReportTest {
    @Test
    fun `reports a passing contract`() {
        assertEquals(
            "Configuration contract check passed: no findings",
            ContractReport.format(SpringDeploymentContractResult(emptyList(), emptyList())),
        )
    }

    @Test
    fun `reports findings in the given order with aligned kinds`() {
        val result =
            SpringDeploymentContractResult(
                findings =
                    listOf(
                        finding(ContractFinding.Kind.MISSING, "app.api-key", "com.example.ApiClient#apiKey"),
                        finding(ContractFinding.Kind.UNUSED, "LEGACY_FLAG", null),
                    ),
                diagnostics = emptyList(),
            )

        assertEquals(
            """
            Configuration contract check failed: 2 findings (1 missing, 1 unused)
              MISSING app.api-key (com.example.ApiClient#apiKey)
              UNUSED  LEGACY_FLAG (unknown source)
            """.trimIndent(),
            ContractReport.format(result),
        )
    }

    @Test
    fun `lists diagnostics without failing a passing contract`() {
        val result =
            SpringDeploymentContractResult(
                findings = emptyList(),
                diagnostics =
                    listOf(
                        diagnostic(
                            SpringDiscoveryDiagnostic.Kind.UNSUPPORTED,
                            "'spring.config.import' is not applied",
                            "src/main/resources/application.yml:4",
                        ),
                    ),
            )

        assertEquals(
            """
            Configuration contract check passed: no findings
            Diagnostics:
              UNSUPPORTED src/main/resources/application.yml:4: 'spring.config.import' is not applied
            """.trimIndent(),
            ContractReport.format(result),
        )
    }

    private fun diagnostic(
        kind: SpringDiscoveryDiagnostic.Kind,
        message: String,
        location: String,
    ) = SpringDiscoveryDiagnostic(kind, message, SourceMetadata(location))

    private fun finding(
        kind: ContractFinding.Kind,
        key: String,
        location: String?,
    ) = ContractFinding(kind, ConfigurationKey.of(key), location?.let(::SourceMetadata))
}
