package io.github.inryeokoffice.configcontract.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet

/**
 * Registers the `configContract` extension and the `configContractCheck` task.
 *
 * The task is not attached to `check`; builds opt in explicitly. Application
 * classes and configuration files are taken from the `main` source set once
 * the `java` plugin is applied.
 */
class ConfigContractPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("configContract", ConfigContractExtension::class.java)

        val check =
            project.tasks.register("configContractCheck", ConfigContractCheckTask::class.java) { task ->
                task.group = "verification"
                task.description = "Checks Spring Boot configuration requirements against deployment-provided configuration."
                task.activeProfiles.set(extension.activeProfiles)
                task.dotenvExampleFiles.from(extension.dotenvExampleFiles)
                task.composeFiles.from(extension.composeFiles)
                task.applicationConfigurationFiles.from(extension.applicationConfigurationFiles)
                task.baseDirectory.set(project.rootProject.layout.projectDirectory)
            }

        project.plugins.withId("java") {
            val main =
                project.extensions
                    .getByType(JavaPluginExtension::class.java)
                    .sourceSets
                    .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
            extension.applicationConfigurationFiles.convention(
                // Patterns are relative to each resource directory, so only top-level files match.
                main.resources.sourceDirectories.asFileTree
                    .matching { it.include(APPLICATION_CONFIGURATION_PATTERNS) },
            )
            check.configure { task ->
                task.applicationClassesDirs.from(main.output.classesDirs)
                task.applicationClasspath.from(main.runtimeClasspath)
            }
        }
    }

    private companion object {
        /** Top-level Spring Boot configuration file names; nested `config/` locations are not included. */
        val APPLICATION_CONFIGURATION_PATTERNS =
            listOf("properties", "yml", "yaml").flatMap { extension ->
                listOf("application.$extension", "application-*.$extension")
            }
    }
}
