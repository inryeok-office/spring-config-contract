package io.github.inryeokoffice.configcontract.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.abort
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files

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
    fun `analyzes the default profile when active profiles are not configured`() {
        writeProject(extension = """dotenvExampleFiles.from(".env.example")""")
        write(".env.example", "APP_REGION=eu\n")
        write("src/main/resources/application-default.yml", "app:\n  api-key: \${DEFAULT_KEY}\n")

        val result = runAndFail("configContractCheck")

        assertEquals(
            listOf(
                "Configuration contract check failed: 1 finding (1 missing)",
                "MISSING DEFAULT_KEY (src/main/resources/application-default.yml:2)",
            ),
            reportLines(result.output),
            result.output,
        )
    }

    @Test
    fun `rejects a symbolic link that resolves outside the root project`(
        @TempDir outside: File,
    ) {
        writeProject(extension = """dotenvExampleFiles.from("deploy/.env.example")""")
        val target = outside.resolve("outside-deployment.txt").apply { writeText("APP_API_KEY=example\n") }
        val link = projectDir.resolve("deploy/.env.example").toPath()
        Files.createDirectories(link.parent)
        try {
            Files.createSymbolicLink(link, target.toPath())
        } catch (exception: IOException) {
            abort("Symbolic links cannot be created here: ${exception.message}")
        } catch (exception: UnsupportedOperationException) {
            abort("Symbolic links are not supported here: ${exception.message}")
        }

        val result = runAndFail("configContractCheck")

        assertEquals(TaskOutcome.FAILED, result.task(":configContractCheck")?.outcome)
        assertTrue(
            result.output.contains("Configuration contract input must resolve inside ${projectDir.name}: deploy/.env.example"),
            result.output,
        )
    }

    @Test
    fun `fails explicitly when application bytecode is newer than the Gradle JVM`() {
        // Mirrors a Java 25 toolchain under a Java 21 Gradle daemon (issue #70) without needing a newer JDK.
        writeProject(extension = """dotenvExampleFiles.from(".env.example")""")
        write(".env.example", "APP_API_KEY=example\n")
        val newerMajorVersion = Runtime.version().feature() + 45
        projectDir.resolve("newer-classes/com/example/NewerBytecode.class").apply {
            parentFile.mkdirs()
            writeBytes(emptyClassFile("com/example/NewerBytecode", newerMajorVersion))
        }
        projectDir.resolve("build.gradle.kts").appendText(
            """

            tasks.named<io.github.inryeokoffice.configcontract.gradle.ConfigContractCheckTask>("configContractCheck") {
                applicationClassesDirs.setFrom(file("newer-classes"))
            }
            """.trimIndent(),
        )

        val result = runAndFail("configContractCheck")

        assertEquals(TaskOutcome.FAILED, result.task(":configContractCheck")?.outcome)
        assertTrue(result.output.contains("Application classes could not be loaded:"), result.output)
        assertTrue(result.output.contains("com.example.NewerBytecode (UnsupportedClassVersionError:"), result.output)
        assertTrue(result.output.contains("class file version $newerMajorVersion.0"), result.output)
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

    @Test
    fun `reads every Compose service when no services are selected`() {
        writeProject(extension = """composeFiles.from("compose.yml")""")
        write("compose.yml", APP_AND_DATABASE_COMPOSE)

        val result = runAndFail("configContractCheck")

        assertEquals(
            listOf(
                "Configuration contract check failed: 1 finding (1 unused)",
                "UNUSED  DATABASE_PASSWORD (compose.yml:7)",
            ),
            reportLines(result.output),
            result.output,
        )
    }

    @Test
    fun `reads only the selected Compose services`() {
        writeProject(
            extension =
                """
                composeFiles.from("compose.yml")
                composeServices.set(setOf("app"))
                """,
        )
        write("compose.yml", APP_AND_DATABASE_COMPOSE)

        val result = run("configContractCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":configContractCheck")?.outcome)
        assertTrue(result.output.contains("Configuration contract check passed: no findings"), result.output)
    }

    @Test
    fun `reuses the configuration cache with a service selection and re-evaluates a changed selection`() {
        writeProject(
            extension =
                """
                composeFiles.from("compose.yml")
                composeServices = setOf("app")
                """,
        )
        write("compose.yml", APP_AND_DATABASE_COMPOSE)

        run("configContractCheck", "--configuration-cache")
        // The unselected service gains a key; the cached selection must still exclude it.
        write("compose.yml", APP_AND_DATABASE_COMPOSE + "      DATABASE_USER: example\n")
        val reused = run("configContractCheck", "--configuration-cache")

        assertTrue(reused.output.contains("Reusing configuration cache."), reused.output)
        assertEquals(TaskOutcome.SUCCESS, reused.task(":configContractCheck")?.outcome)
        assertTrue(reused.output.contains("Configuration contract check passed: no findings"), reused.output)

        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(buildFile.readText().replace("setOf(\"app\")", "setOf(\"database\")"))
        val changed = runAndFail("configContractCheck", "--configuration-cache")

        assertFalse(changed.output.contains("Reusing configuration cache."), changed.output)
        assertEquals(
            listOf(
                "Configuration contract check failed: 3 findings (1 missing, 2 unused)",
                "UNUSED  DATABASE_PASSWORD (compose.yml:7)",
                "UNUSED  DATABASE_USER (compose.yml:8)",
                "MISSING app.api-key (com.example.ApiClient.apiKey)",
            ),
            reportLines(changed.output),
            changed.output,
        )
    }

    @Test
    fun `fails with an actionable message for an unknown Compose service`() {
        writeProject(
            extension =
                """
                composeFiles.from("compose.yml")
                composeServices.set(listOf("app", "ap"))
                """,
        )
        write("compose.yml", APP_AND_DATABASE_COMPOSE)

        val result = runAndFail("configContractCheck")

        assertEquals(TaskOutcome.FAILED, result.task(":configContractCheck")?.outcome)
        assertTrue(
            result.output.contains(
                "Invalid composeServices: Selected Docker Compose services are not declared in any configured " +
                    "Docker Compose file: 'ap' (declared services: 'app', 'database')",
            ),
            result.output,
        )
    }

    @Test
    fun `fails when Compose services are selected without Compose files`() {
        writeProject(extension = """composeServices.set(setOf("app"))""")

        val result = runAndFail("configContractCheck")

        assertEquals(TaskOutcome.FAILED, result.task(":configContractCheck")?.outcome)
        assertTrue(
            result.output.contains(
                "Invalid composeServices: Docker Compose services are selected ('app') but no Docker Compose files are configured",
            ),
            result.output,
        )
    }

    @Test
    fun `selects Compose services from the Groovy DSL`() {
        writeGroovyProject(
            extension =
                """
                composeFiles.from('compose.yml')
                composeServices = ['app']
                """,
        )
        write("compose.yml", APP_AND_DATABASE_COMPOSE)

        val result = run("configContractCheck", "--configuration-cache")

        assertEquals(TaskOutcome.SUCCESS, result.task(":configContractCheck")?.outcome)
        assertTrue(result.output.contains("Configuration contract check passed: no findings"), result.output)
    }

    private fun writeProject(extension: String) {
        val springJars = springJarFiles().joinToString { "\"${it.invariantSeparatorsPath}\"" }
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
        writeApplicationSource()
    }

    private fun writeGroovyProject(extension: String) {
        write("settings.gradle", "rootProject.name = 'fixture'\n")
        write(
            "build.gradle",
            """
            plugins {
                id 'java'
                id 'io.github.inryeok-office.config-contract'
            }

            dependencies {
                implementation files(${groovyPathList(springJarFiles())})
            }

            configContract {
            ${extension.trimIndent()}
            }
            """.trimIndent(),
        )
        writeApplicationSource()
    }

    private fun springJarFiles(): List<File> =
        listOf(ConfigurationProperties::class.java, Value::class.java).map {
            File(
                it.protectionDomain.codeSource.location
                    .toURI(),
            )
        }

    private fun writeApplicationSource() {
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

    /** A minimal class file declaring only [internalName] extending `java/lang/Object`, with no members. */
    private fun emptyClassFile(
        internalName: String,
        majorVersion: Int,
    ): ByteArray =
        ByteArrayOutputStream()
            .also { bytes ->
                DataOutputStream(bytes).use { out ->
                    out.writeInt(0xCAFEBABE.toInt())
                    out.writeShort(0)
                    out.writeShort(majorVersion)
                    out.writeShort(5)
                    out.writeByte(1)
                    out.writeUTF(internalName)
                    out.writeByte(7)
                    out.writeShort(1)
                    out.writeByte(1)
                    out.writeUTF("java/lang/Object")
                    out.writeByte(7)
                    out.writeShort(3)
                    out.writeShort(0x0021)
                    out.writeShort(2)
                    out.writeShort(4)
                    repeat(4) { out.writeShort(0) }
                }
            }.toByteArray()

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

    private companion object {
        /** An application service and an infrastructure service; `DATABASE_PASSWORD` is on line 7. */
        val APP_AND_DATABASE_COMPOSE =
            """
            services:
              app:
                environment:
                  APP_API_KEY: example
              database:
                environment:
                  DATABASE_PASSWORD: example
            """.trimIndent() + "\n"
    }
}
