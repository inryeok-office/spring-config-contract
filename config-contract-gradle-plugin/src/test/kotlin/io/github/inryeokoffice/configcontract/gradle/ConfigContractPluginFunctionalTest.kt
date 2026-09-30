package io.github.inryeokoffice.configcontract.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import java.io.File

class ConfigContractPluginFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `passes when deployment input satisfies every requirement`() {
        writeProject(
            extension =
                """
                dotenvExampleFiles.from(".env.example")
                composeFiles.from("compose.yml")
                """,
        )
        write(".env.example", "APP_API_KEY=example\n")
        write(
            "compose.yml",
            """
            services:
              app:
                environment:
                  APP_REGION: eu
            """.trimIndent(),
        )

        val result = run("configContractCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":configContractCheck")?.outcome)
        assertTrue(result.output.contains("Configuration contract check passed: no findings"), result.output)
    }

    @Test
    fun `fails with a stable report for missing and unused configuration`() {
        writeProject(extension = """dotenvExampleFiles.from("deploy/.env.example")""")
        write("deploy/.env.example", "# example\nAPP_REGION=eu\nLEGACY_FLAG=true\n")

        val first = runAndFail("configContractCheck")
        val second = runAndFail("configContractCheck")

        assertEquals(TaskOutcome.FAILED, first.task(":configContractCheck")?.outcome)
        val expected =
            listOf(
                "Configuration contract check failed: 2 findings (1 missing, 1 unused)",
                "UNUSED  LEGACY_FLAG (deploy/.env.example:3)",
                "MISSING app.api-key (com.example.ApiClient.apiKey)",
            )
        assertEquals(expected, reportLines(first.output), first.output)
        assertEquals(reportLines(first.output), reportLines(second.output))
    }

    @Test
    fun `uses application configuration defaults and active profiles`() {
        writeProject(
            extension =
                """
                activeProfiles.set(listOf("prod"))
                dotenvExampleFiles.from(".env.example")
                """,
        )
        write(".env.example", "APP_REGION=eu\n")
        write("src/main/resources/application.yml", "app:\n  api-key: local\n")
        write("src/main/resources/application-prod.yml", "app:\n  api-key: \${API_KEY}\n")

        val result = runAndFail("configContractCheck")

        assertEquals(
            listOf(
                "Configuration contract check failed: 1 finding (1 missing)",
                "MISSING API_KEY (src/main/resources/application-prod.yml:2)",
            ),
            reportLines(result.output),
            result.output,
        )
    }

    @Test
    fun `reports malformed deployment input as a build failure`() {
        writeProject(extension = """dotenvExampleFiles.from(".env.example")""")
        write(".env.example", "APP_API_KEY\n")

        val result = runAndFail("configContractCheck")

        assertEquals(TaskOutcome.FAILED, result.task(":configContractCheck")?.outcome)
        assertTrue(result.output.contains("Deployment input could not be read: .env.example:"), result.output)
        assertTrue(result.output.contains("line 1:"), result.output)
    }

    @Test
    fun `reports a configured deployment file that does not exist`() {
        writeProject(extension = """composeFiles.from("compose.yml")""")

        val result = runAndFail("configContractCheck")

        assertTrue(result.output.contains("Configuration contract input does not exist: compose.yml"), result.output)
    }

    @Test
    fun `is compatible with the configuration cache`() {
        writeProject(extension = """dotenvExampleFiles.from(".env.example")""")
        write(".env.example", "APP_API_KEY=example\nAPP_REGION=eu\n")

        run("configContractCheck", "--configuration-cache")
        val reused = run("configContractCheck", "--configuration-cache")

        assertTrue(reused.output.contains("Reusing configuration cache."), reused.output)
        assertEquals(TaskOutcome.SUCCESS, reused.task(":configContractCheck")?.outcome)
    }

    private fun writeProject(extension: String) {
        val springJars =
            listOf(ConfigurationProperties::class.java, Value::class.java)
                .map {
                    File(
                        it.protectionDomain.codeSource.location
                            .toURI(),
                    ).invariantSeparatorsPath
                }.joinToString { "\"$it\"" }
        write("settings.gradle.kts", "rootProject.name = \"fixture\"\n")
        write(
            "build.gradle.kts",
            """
            plugins {
                java
                id("io.github.inryeok-office.config-contract")
            }

            dependencies {
                implementation(files($springJars))
            }

            configContract {
            ${extension.trimIndent()}
            }
            """.trimIndent(),
        )
        write(
            "src/main/java/com/example/ApiClient.java",
            """
            package com.example;

            import org.springframework.beans.factory.annotation.Value;

            public class ApiClient {
                @Value("${'$'}{app.api-key}")
                private String apiKey;

                @Value("${'$'}{app.region:us}")
                private String region;
            }
            """.trimIndent(),
        )
    }

    private fun write(
        path: String,
        content: String,
    ) {
        val file = projectDir.resolve(path)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun runner(vararg arguments: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*arguments, "--stacktrace")

    private fun run(vararg arguments: String): BuildResult = runner(*arguments).build()

    private fun runAndFail(vararg arguments: String): BuildResult = runner(*arguments).buildAndFail()

    /** The report lines inside Gradle's failure message, without Gradle's indentation. */
    private fun reportLines(output: String): List<String> =
        output
            .lines()
            .map { it.trim().removePrefix("> ") }
            .dropWhile { !it.startsWith("Configuration contract check") }
            .takeWhile { it.isNotEmpty() && !it.startsWith("*") }
            .distinct()
}
