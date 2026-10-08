package io.github.inryeokoffice.configcontract.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.w3c.dom.Element
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import javax.xml.parsers.DocumentBuilderFactory

class PublicationPipelineFunctionalTest {
    @TempDir
    lateinit var workspace: File

    @Test
    fun `publishes signed artifacts and resolves an offline external consumer`() {
        val central = workspace.resolve("central")
        val portal = workspace.resolve("portal")
        val thirdParty = workspace.resolve("third-party")
        val producer = workspace.resolve("producer")
        copyProducer(producer)
        val signing = ephemeralSigningKey(workspace.toPath())

        try {
            val publication =
                runProducer(
                    producer,
                    "preparePublicationVerification",
                    "-PreleaseVersion=$TEST_VERSION",
                    "-PcentralLikeRepository=${central.absolutePath}",
                    "-PportalLikeRepository=${portal.absolutePath}",
                    "-PthirdPartyRepository=${thirdParty.absolutePath}",
                    environment = signing.environment,
                )

            assertTrue(publication.output.contains("BUILD SUCCESSFUL"), publication.output)
            assertPublishedRuntimeMetadata(central)
            assertPluginMetadata(portal)
            assertSignedCentralArtifacts(central, signing)

            val passing = workspace.resolve("consumer-passing")
            writeConsumer(passing, central, portal, thirdParty, ConsumerKind.MINIMAL, "")
            val passingResult = runConsumer(passing, "configContractCheck")
            assertTrue(passingResult.output.contains("BUILD SUCCESSFUL"), passingResult.output)
            assertTrue(passingResult.output.contains("Configuration contract check passed: no findings"), passingResult.output)

            val failing = workspace.resolve("consumer-failing")
            writeConsumer(
                failing,
                central,
                portal,
                thirdParty,
                ConsumerKind.MINIMAL,
                "dotenvExampleFiles.from(\".env.example\")",
            )
            failing.resolve(".env.example").writeText("LEGACY_SETTING=true\n", Charsets.UTF_8)
            val failingResult = runConsumerAndFail(failing, "configContractCheck")
            assertTrue(failingResult.output.contains("UNUSED  LEGACY_SETTING (.env.example:1)"), failingResult.output)

            val coexistence = workspace.resolve("consumer-framework-plugins")
            writeConsumer(coexistence, central, portal, thirdParty, ConsumerKind.SPRING_BOOT_AND_KOTLIN, "")
            val coexistenceResult = runConsumer(coexistence, "configContractCheck")
            assertTrue(coexistenceResult.output.contains("BUILD SUCCESSFUL"), coexistenceResult.output)
        } finally {
            signing.destroy()
            workspace.deleteRecursively()
        }
    }

    private fun assertPublishedRuntimeMetadata(central: File) {
        RUNTIME_ARTIFACTS.forEach { artifact ->
            val moduleDirectory = central.resolve("io/github/inryeok-office/$artifact/$TEST_VERSION")
            val pom = moduleDirectory.resolve("$artifact-$TEST_VERSION.pom")
            val module = moduleDirectory.resolve("$artifact-$TEST_VERSION.module")
            val sources = moduleDirectory.resolve("$artifact-$TEST_VERSION-sources.jar")
            val javadoc = moduleDirectory.resolve("$artifact-$TEST_VERSION-javadoc.jar")

            assertTrue(pom.isFile, "Missing POM for $artifact")
            assertTrue(module.isFile, "Missing Gradle Module Metadata for $artifact")
            assertTrue(sources.length() > 0, "Missing or empty sources JAR for $artifact")
            assertTrue(javadoc.length() > 0, "Missing or empty Dokka Javadoc JAR for $artifact")
            assertTrue(jarEntries(javadoc).any { it.endsWith("index.html") }, "Dokka API HTML is missing from $artifact")

            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom)
            assertEquals("io.github.inryeok-office", text(document.documentElement, "groupId"))
            assertEquals(artifact, text(document.documentElement, "artifactId"))
            assertEquals(TEST_VERSION, text(document.documentElement, "version"))
            assertEquals("The Apache License, Version 2.0", text(document.documentElement, "name", "licenses", "license"))
            assertEquals("inryeok-office", text(document.documentElement, "id", "developers", "developer"))
            assertEquals("scm:git:$PROJECT_URL.git", text(document.documentElement, "connection", "scm"))
            assertTrue(module.readText(Charsets.UTF_8).contains("\"version\": \"$TEST_VERSION\""))
            assertFalse(module.readText(Charsets.UTF_8).contains(":config-contract-"))
        }

        assertPomDependency(
            central.resolve("io/github/inryeok-office/config-contract-spring/$TEST_VERSION/config-contract-spring-$TEST_VERSION.pom"),
            "io.github.inryeok-office",
            "config-contract-core",
            "compile",
        )
        assertPomDependency(
            central.resolve(
                "io/github/inryeok-office/config-contract-deployment/$TEST_VERSION/config-contract-deployment-$TEST_VERSION.pom",
            ),
            "io.github.inryeok-office",
            "config-contract-core",
            "compile",
        )
    }

    private fun assertPluginMetadata(portal: File) {
        val marker =
            portal.resolve(
                "io/github/inryeok-office/config-contract/" +
                    "io.github.inryeok-office.config-contract.gradle.plugin/$TEST_VERSION/" +
                    "io.github.inryeok-office.config-contract.gradle.plugin-$TEST_VERSION.pom",
            )
        val implementation =
            portal.resolve(
                "io/github/inryeok-office/config-contract-gradle-plugin/$TEST_VERSION/" +
                    "config-contract-gradle-plugin-$TEST_VERSION.pom",
            )
        assertPomDependency(marker, "io.github.inryeok-office", "config-contract-gradle-plugin", "compile")
        RUNTIME_ARTIFACTS.forEach { artifact ->
            assertPomDependency(implementation, "io.github.inryeok-office", artifact, "runtime")
        }
        assertTrue(
            portal
                .resolve(
                    "io/github/inryeok-office/config-contract-gradle-plugin/$TEST_VERSION/" +
                        "config-contract-gradle-plugin-$TEST_VERSION.module",
                ).isFile,
        )
    }

    private fun assertSignedCentralArtifacts(
        central: File,
        signing: EphemeralSigningKey,
    ) {
        val signedArtifacts =
            central
                .walkTopDown()
                .filter { file -> file.isFile && (file.extension == "jar" || file.extension == "pom" || file.extension == "module") }
                .toList()
        assertTrue(signedArtifacts.isNotEmpty())
        signedArtifacts.forEach { artifact ->
            val signature = File(artifact.parentFile, "${artifact.name}.asc")
            assertTrue(signature.isFile, "Missing signature for ${artifact.name}")
            signing.verify(signature, artifact)
        }
    }

    private fun assertPomDependency(
        pom: File,
        group: String,
        artifact: String,
        expectedScope: String,
    ) {
        assertTrue(pom.isFile, "Missing POM: $pom")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom)
        val dependencies = document.getElementsByTagName("dependency")
        val dependency =
            (0 until dependencies.length)
                .map { dependencies.item(it) as Element }
                .firstOrNull {
                    text(it, "groupId") == group && text(it, "artifactId") == artifact
                }
        assertNotNull(dependency, "Missing $group:$artifact in ${pom.name}")
        val scope = text(dependency!!, "scope").ifBlank { "compile" }
        assertEquals(expectedScope, scope, "Unexpected scope for $group:$artifact in ${pom.name}")
        assertEquals(TEST_VERSION, text(dependency, "version"))
    }

    private fun text(
        element: Element,
        leaf: String,
        vararg parents: String,
    ): String {
        var current = element
        parents.forEach { parent ->
            current = current.getElementsByTagName(parent).item(0) as Element
        }
        return (current.getElementsByTagName(leaf).item(0) as? Element)?.textContent.orEmpty()
    }

    private fun jarEntries(jar: File): List<String> =
        java.util.jar
            .JarFile(jar)
            .use { archive ->
                archive
                    .entries()
                    .asSequence()
                    .map { it.name }
                    .toList()
            }

    private fun writeConsumer(
        project: File,
        central: File,
        portal: File,
        thirdParty: File,
        kind: ConsumerKind,
        extension: String,
    ) {
        project.mkdirs()
        val repositories =
            listOf(portal, central, thirdParty).joinToString("\n") { repository ->
                "        maven { url = uri('${repository.toURI()}') }"
            }
        project.resolve("settings.gradle").writeText(
            """
            pluginManagement {
                repositories {
$repositories
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    maven { url = uri('${thirdParty.toURI()}') }
                }
            }
            rootProject.name = 'publication-consumer'
            """.trimIndent() + "\n",
            Charsets.UTF_8,
        )
        project.resolve("build.gradle").writeText(
            when (kind) {
                ConsumerKind.MINIMAL ->
                    """
                    plugins {
                        id 'java'
                        id '$PLUGIN_ID' version '$TEST_VERSION'
                    }

                    configContract {
                        $extension
                    }

                    // This minimal fixture establishes standalone plugin resolution and task execution without
                    // relying on an application classpath.
                    tasks.named('configContractCheck') {
                        applicationClassesDirs.setFrom(files())
                        applicationClasspath.setFrom(files())
                    }
                    """
                ConsumerKind.SPRING_BOOT_AND_KOTLIN ->
                    """
                    plugins {
                        id 'java'
                        id 'org.springframework.boot' version '${System.getProperty("configContract.springBootVersion")}'
                        id 'org.jetbrains.kotlin.jvm' version '${System.getProperty("configContract.kotlinVersion")}'
                        id '$PLUGIN_ID' version '$TEST_VERSION'
                    }

                    dependencies {
                        implementation 'org.springframework.boot:spring-boot:${System.getProperty("configContract.springBootVersion")}'
                    }

                    configContract {
                        $extension
                    }
                    """
            }.trimIndent() + "\n",
            Charsets.UTF_8,
        )
    }

    private fun runProducer(
        producer: File,
        vararg arguments: String,
        environment: Map<String, String>,
    ): BuildExecution = executeGradle(producer, producer, environment, *arguments, "--stacktrace", "--no-configuration-cache")

    private fun runConsumer(
        project: File,
        vararg arguments: String,
    ): BuildExecution {
        val execution = executeConsumer(project, *arguments)
        assertTrue(execution.exitCode == 0, execution.output)
        return execution
    }

    private fun runConsumerAndFail(
        project: File,
        vararg arguments: String,
    ): BuildExecution {
        val execution = executeConsumer(project, *arguments)
        assertTrue(execution.exitCode != 0, "Expected the consumer build to fail.\n${execution.output}")
        return execution
    }

    private fun executeConsumer(
        project: File,
        vararg arguments: String,
    ): BuildExecution {
        val gradleUserHome = Files.createTempDirectory("scc-gradle-user-home-${project.name}-").toFile()
        return try {
            executeGradle(
                rootProject,
                project,
                System.getenv(),
                "--offline",
                "--no-daemon",
                "--gradle-user-home",
                gradleUserHome.absolutePath,
                "--stacktrace",
                "--no-configuration-cache",
                *arguments,
            )
        } finally {
            gradleUserHome.deleteRecursively()
        }
    }

    private fun executeGradle(
        wrapperProject: File,
        workingDirectory: File,
        environment: Map<String, String>,
        vararg arguments: String,
    ): BuildExecution {
        val wrapper = wrapperProject.resolve(if (isWindows()) "gradlew.bat" else "gradlew")
        check(wrapper.isFile) { "Missing Gradle wrapper: $wrapper" }
        val process =
            ProcessBuilder(
                listOf(
                    wrapper.absolutePath,
                    "-Dorg.gradle.java.home=${System.getProperty("java.home").replace(File.separatorChar, '/')}",
                ) + arguments,
            ).directory(workingDirectory)
                .redirectErrorStream(true)
                .also { builder ->
                    builder.environment().clear()
                    builder.environment().putAll(environment)
                }.start()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
        return BuildExecution(process.waitFor(), output)
    }

    private fun copyProducer(destination: File) {
        val sourceRoot = rootProject.toPath()
        Files.walkFileTree(
            sourceRoot,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    directory: Path,
                    attributes: BasicFileAttributes,
                ): FileVisitResult =
                    if (directory != sourceRoot && directory.fileName.toString() in EXCLUDED_PRODUCER_DIRECTORIES) {
                        FileVisitResult.SKIP_SUBTREE
                    } else {
                        FileVisitResult.CONTINUE
                    }

                override fun visitFile(
                    source: Path,
                    attributes: BasicFileAttributes,
                ): FileVisitResult {
                    val target = destination.toPath().resolve(sourceRoot.relativize(source).toString())
                    Files.createDirectories(target.parent)
                    Files.copy(source, target)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private val rootProject: File
        get() = File(requireNotNull(System.getProperty("configContract.rootProjectDir")))

    private fun ephemeralSigningKey(workspace: Path): EphemeralSigningKey {
        val home = createGpgHome(workspace)
        val passphrase = "publication-verification-passphrase"
        startGpgAgent(home)
        runGpg(home, passphrase, "--quick-generate-key", "Publication Verification <publication@example.invalid>", "rsa2048", "sign", "1d")
        val armoredKey = runGpg(home, passphrase, "--armor", "--export-secret-keys", "publication@example.invalid")
        check(armoredKey.contains("BEGIN PGP PRIVATE KEY BLOCK")) { "Could not generate an ephemeral signing key." }
        return EphemeralSigningKey(home, passphrase, armoredKey)
    }

    private fun runGpg(
        home: Path,
        passphrase: String,
        vararg arguments: String,
    ): String {
        val process =
            gpgProcess(home, "--batch", "--yes", "--pinentry-mode", "loopback", "--passphrase", passphrase, *arguments)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
        check(process.waitFor() == 0) { "Ephemeral signing command failed: ${output.lineSequence().firstOrNull().orEmpty()}" }
        return output
    }

    private fun createGpgHome(workspace: Path): Path {
        if (!System.getProperty("os.name").startsWith("Windows")) {
            return Files.createDirectory(workspace.resolve("ephemeral-gpg-home"))
        }
        val systemDrive = System.getenv("SystemDrive") ?: "C:"
        val parent = Path.of(systemDrive, "tmp")
        Files.createDirectories(parent)
        return Files.createTempDirectory(parent, "scc-gpg-")
    }

    private fun startGpgAgent(home: Path) {
        if (!System.getProperty("os.name").startsWith("Windows")) {
            return
        }
        val process =
            ProcessBuilder(gitBash(), "-lc", "gpg-agent --homedir ${shellQuote(posixPath(home))} --daemon")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        check(process.waitFor() == 0) { "Could not start the ephemeral signing agent." }
    }

    private fun gpgProcess(
        home: Path,
        vararg arguments: String,
    ): ProcessBuilder {
        val common = listOf("gpg", "--homedir", if (isWindows()) posixPath(home) else home.toString()) + arguments
        return if (isWindows()) {
            ProcessBuilder(gitBash(), "-lc", common.joinToString(" ", transform = ::shellQuote))
        } else {
            ProcessBuilder(common)
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows")

    private fun gitBash(): String = File(System.getenv("ProgramFiles") ?: "C:/Program Files", "Git/bin/bash.exe").absolutePath

    private fun posixPath(path: Path): String =
        path.toString().replace(Regex("^([A-Za-z]):")) { "/${it.groupValues[1].lowercase()}" }.replace('\\', '/')

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\\"'\\\"'")}'"

    private enum class ConsumerKind {
        MINIMAL,
        SPRING_BOOT_AND_KOTLIN,
    }

    private inner class EphemeralSigningKey(
        private val home: Path,
        private val passphrase: String,
        armoredKey: String,
    ) {
        val environment: Map<String, String> =
            System.getenv() + mapOf("SIGNING_KEY" to armoredKey, "SIGNING_PASSWORD" to passphrase)

        fun verify(
            signature: File,
            artifact: File,
        ) {
            val process =
                gpgProcess(home, "--batch", "--verify", signature.absolutePath, artifact.absolutePath)
                    .redirectErrorStream(true)
                    .start()
            val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
            assertEquals(0, process.waitFor(), "Signature verification failed for ${artifact.name}: $output")
        }

        fun destroy() {
            home.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val TEST_VERSION = "0.1.0-publication-test"
        const val PLUGIN_ID = "io.github.inryeok-office.config-contract"
        const val PROJECT_URL = "https://github.com/inryeok-office/spring-config-contract"
        val RUNTIME_ARTIFACTS = listOf("config-contract-core", "config-contract-spring", "config-contract-deployment")
        val EXCLUDED_PRODUCER_DIRECTORIES = setOf(".git", ".gradle", ".gradle-user", ".idea", ".kotlin", "build")
    }

    private data class BuildExecution(
        val exitCode: Int,
        val output: String,
    )
}
