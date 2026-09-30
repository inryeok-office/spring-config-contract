package io.github.inryeokoffice.configcontract.gradle

import io.github.inryeokoffice.configcontract.deployment.DeploymentInputAdapter
import io.github.inryeokoffice.configcontract.deployment.DeploymentInputException
import io.github.inryeokoffice.configcontract.deployment.DeploymentSource
import io.github.inryeokoffice.configcontract.deployment.compose.ComposeAdapter
import io.github.inryeokoffice.configcontract.deployment.dotenv.DotenvExampleAdapter
import io.github.inryeokoffice.configcontract.spring.ApplicationConfigurationSource
import io.github.inryeokoffice.configcontract.spring.SpringDiscoveryException
import io.github.inryeokoffice.configcontract.spring.SpringDiscoveryInput
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.net.URLClassLoader

/**
 * Evaluates the configuration contract and fails when it reports findings.
 *
 * The task only reads files, loads application classes, and delegates to
 * [SpringDeploymentContract]; comparison rules stay in the other modules.
 * Every reported path is relative to [baseDirectory], so output is identical
 * across machines.
 */
@DisableCachingByDefault(because = "Verification task that produces no outputs")
abstract class ConfigContractCheckTask : DefaultTask() {
    /** Compiled application classes to inspect. */
    @get:Classpath
    abstract val applicationClassesDirs: ConfigurableFileCollection

    /** Classpath used to load [applicationClassesDirs]. */
    @get:Classpath
    abstract val applicationClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val applicationConfigurationFiles: ConfigurableFileCollection

    @get:Input
    abstract val activeProfiles: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dotenvExampleFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val composeFiles: ConfigurableFileCollection

    /** Directory that reported source names are relative to; files must be inside it. */
    @get:Internal
    abstract val baseDirectory: DirectoryProperty

    @TaskAction
    fun check() {
        val base = baseDirectory.get().asFile
        val applicationConfigurations =
            applicationConfigurationFiles.files
                .map { ApplicationConfigurationSource(relativeName(base, it), readInput(base, it)) }
                .sortedBy { it.name }
        val deploymentInputs =
            deploymentInputs(base, dotenvExampleFiles, DotenvExampleAdapter) +
                deploymentInputs(base, composeFiles, ComposeAdapter)

        val result =
            withApplicationClasses { classes ->
                try {
                    SpringDeploymentContract.evaluate(
                        SpringDeploymentContractInput(
                            SpringDiscoveryInput(classes, applicationConfigurations, activeProfiles.get()),
                            deploymentInputs,
                        ),
                    )
                } catch (exception: SpringDiscoveryException) {
                    throw GradleException(exception.message!!, exception)
                } catch (exception: DeploymentInputException) {
                    throw GradleException("Deployment input could not be read: ${exception.message}", exception)
                }
            }

        val report = ContractReport.format(result)
        if (result.findings.isNotEmpty()) {
            throw GradleException(report)
        }
        logger.lifecycle(report)
    }

    private fun deploymentInputs(
        base: File,
        files: ConfigurableFileCollection,
        adapter: DeploymentInputAdapter,
    ): List<DeploymentContractInput> =
        files.files.map { file ->
            DeploymentContractInput(adapter, DeploymentSource(relativeName(base, file), readInput(base, file)))
        }

    private fun <T> withApplicationClasses(action: (List<Class<*>>) -> T): T {
        val urls = (applicationClassesDirs.files + applicationClasspath.files).map { it.toURI().toURL() }
        // Parent-first delegation makes application classes see the Spring Boot annotations discovery was loaded with.
        return URLClassLoader(urls.toTypedArray(), javaClass.classLoader).use { loader ->
            val failures = sortedSetOf<String>()
            val classes =
                applicationClassNames().mapNotNull { name ->
                    try {
                        Class.forName(name, false, loader)
                    } catch (error: LinkageError) {
                        failures += "$name (${error.javaClass.simpleName}: ${error.message})"
                        null
                    } catch (exception: ClassNotFoundException) {
                        failures += "$name (${exception.message})"
                        null
                    }
                }
            if (failures.isNotEmpty()) {
                throw GradleException(
                    failures.joinToString(prefix = "Application classes could not be loaded:\n", separator = "\n") { "  $it" },
                )
            }
            action(classes)
        }
    }

    private fun applicationClassNames(): List<String> =
        applicationClassesDirs.files
            .filter { it.isDirectory }
            .flatMap { directory ->
                directory
                    .walk()
                    .filter { it.isFile && it.extension == "class" }
                    .map { it.relativeTo(directory).invariantSeparatorsPath.removeSuffix(".class") }
                    .filterNot { it.endsWith("module-info") || it.endsWith("package-info") }
                    .map { it.replace('/', '.') }
                    .toList()
            }.distinct()
            .sorted()

    private fun readInput(
        base: File,
        file: File,
    ): String {
        if (!file.isFile) {
            throw GradleException("Configuration contract input does not exist: ${relativeName(base, file)}")
        }
        return file.readText(Charsets.UTF_8)
    }

    private fun relativeName(
        base: File,
        file: File,
    ): String {
        val relative = file.relativeToOrNull(base)?.invariantSeparatorsPath
        if (relative == null || relative.split('/').contains("..")) {
            throw GradleException("Configuration contract input must be inside ${base.name}: ${file.name}")
        }
        return relative
    }
}
