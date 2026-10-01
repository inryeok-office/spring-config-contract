package io.github.inryeokoffice.configcontract.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import java.io.File

/**
 * Runs `configContractCheck` against each project under `samples/`.
 *
 * Every test copies one sample into a temporary directory, so samples are never built in place. The samples
 * declare no dependencies and no repositories; the Spring jars (and, for the Kotlin sample, its precompiled
 * classes) are supplied through a generated init script, and the build runs with `--offline`.
 */
class SampleProjectsFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @TempDir
    lateinit var scriptDir: File

    @Test
    fun `valid sample passes when deployment input satisfies every requirement`() {
        copySample("valid")

        val result = run(sampleInitScript())

        assertEquals(TaskOutcome.SUCCESS, result.task(TASK)?.outcome)
        assertTrue(result.output.contains("Configuration contract check passed: no findings"), result.output)
    }

    @Test
    fun `missing-required sample fails and names the missing key and its code location`() {
        copySample("missing-required")

        val result = runAndFail(sampleInitScript())

        assertFailedBuild(result)
        assertEquals(
            listOf(
                "Configuration contract check failed: 1 finding (1 missing)",
                "MISSING app.api-key (com.example.AppSettings.apiKey)",
            ),
            reportLines(result),
            result.output,
        )
    }

    @Test
    fun `unused-configuration sample fails and names each unused key and its deployment line`() {
        copySample("unused-configuration")

        val result = runAndFail(sampleInitScript())

        assertFailedBuild(result)
        assertEquals(
            listOf(
                "Configuration contract check failed: 2 findings (2 unused)",
                "UNUSED  LEGACY_FLAG (deploy/.env.example:3)",
                "UNUSED  OLD_ENDPOINT (deploy/compose.yml:4)",
            ),
            reportLines(result),
            result.output,
        )
    }

    @Test
    fun `optional-and-default sample passes without reporting optional keys as missing or unused`() {
        copySample("optional-and-default")

        val result = run(sampleInitScript())

        assertEquals(TaskOutcome.SUCCESS, result.task(TASK)?.outcome)
        assertTrue(result.output.contains("Configuration contract check passed: no findings"), result.output)
    }

    @Test
    fun `kotlin-binding sample reports only the Kotlin non-null parameter without a default`() {
        copySample("kotlin-binding")

        val result = runAndFail(sampleInitScript(kotlinSampleClassesDirs()))

        assertFailedBuild(result)
        assertEquals(
            listOf(
                "Configuration contract check failed: 1 finding (1 missing)",
                "MISSING app.mail.sender (com.example.MailProperties#sender)",
            ),
            reportLines(result),
            result.output,
        )
    }

    @Test
    fun `reports the same locations when fixtures are checked out with CRLF line endings`() {
        copySample("unused-configuration")
        listOf("deploy/.env.example", "deploy/compose.yml").forEach { path ->
            val file = projectDir.resolve(path)
            file.writeText(file.readText().replace("\r\n", "\n").replace("\n", "\r\n"))
        }

        val result = runAndFail(sampleInitScript())

        assertEquals(
            listOf(
                "Configuration contract check failed: 2 findings (2 unused)",
                "UNUSED  LEGACY_FLAG (deploy/.env.example:3)",
                "UNUSED  OLD_ENDPOINT (deploy/compose.yml:4)",
            ),
            reportLines(result),
            result.output,
        )
    }

    @Test
    fun `kotlin sample classes stay off the test runtime classpath`() {
        assertThrows(ClassNotFoundException::class.java) { Class.forName("com.example.MailProperties") }
        assertNull(javaClass.classLoader.getResource("com/example/MailProperties.class"))
    }

    private fun assertFailedBuild(result: BuildResult) {
        assertEquals(TaskOutcome.FAILED, result.task(TASK)?.outcome)
        assertTrue(result.output.contains("BUILD FAILED"), result.output)
        assertTrue(result.output.contains("Execution failed for task '$TASK'"), result.output)
    }

    private fun copySample(name: String) {
        val source = File(requiredProperty("configContract.samplesDir")).resolve(name)
        check(source.isDirectory) { "Missing sample project: $name" }
        source
            .walkTopDown()
            .onEnter { it.name !in GENERATED_DIRECTORIES }
            .filter { it.isFile }
            .forEach { file ->
                val target = projectDir.resolve(file.relativeTo(source).path)
                target.parentFile.mkdirs()
                file.copyTo(target)
            }
    }

    private fun kotlinSampleClassesDirs(): List<File> =
        requiredProperty("configContract.kotlinSampleClassesDirs")
            .split(File.pathSeparator)
            .filter { it.isNotEmpty() }
            .map(::File)
            // The Java output directory does not exist because the Kotlin sample has no Java sources.
            .filter { it.isDirectory }
            .also { check(it.isNotEmpty()) { "Kotlin sample classes were not compiled" } }

    private fun requiredProperty(name: String): String = checkNotNull(System.getProperty(name)) { "Missing system property $name" }

    /**
     * Writes an init script that supplies what the samples deliberately leave out: the Spring jars that are already
     * on this test's classpath and, optionally, precompiled application classes. No dependency is resolved remotely.
     */
    private fun sampleInitScript(applicationClassesDirs: List<File> = emptyList()): File {
        val springJars =
            listOf(ConfigurationProperties::class.java, Value::class.java)
                .map {
                    File(
                        it.protectionDomain.codeSource.location
                            .toURI(),
                    )
                }.distinct()
        val script =
            """
            allprojects { p ->
                p.plugins.withId('java') {
                    p.dependencies.add('implementation', p.files(${groovyPathList(springJars)}))
                }
                p.tasks.configureEach { task ->
                    if (task.name == 'configContractCheck') {
                        task.applicationClassesDirs.from(p.files(${groovyPathList(applicationClassesDirs)}))
                    }
                }
            }
            """.trimIndent()
        return scriptDir.resolve("sample-init.gradle").apply { writeText(script) }
    }

    private fun runner(initScript: File): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(TASK_NAME, "--init-script", initScript.path, "--offline")

    private fun run(initScript: File): BuildResult = runner(initScript).build()

    private fun runAndFail(initScript: File): BuildResult = runner(initScript).buildAndFail()

    /** The report lines inside Gradle's failure message, without Gradle's indentation or CRLF line endings. */
    private fun reportLines(result: BuildResult): List<String> =
        result.output
            .replace("\r\n", "\n")
            .lines()
            .map { it.trim().removePrefix("> ") }
            .dropWhile { !it.startsWith("Configuration contract check") }
            .takeWhile { it.isNotEmpty() && !it.startsWith("*") }

    private companion object {
        const val TASK_NAME = "configContractCheck"
        const val TASK = ":$TASK_NAME"
        val GENERATED_DIRECTORIES = setOf("build", ".gradle")
    }
}
