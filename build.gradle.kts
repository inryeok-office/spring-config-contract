import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

abstract class VerifyRepositoryRulesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val coreFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val deploymentFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val secretSensitiveFiles: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val checks = listOf(
            "config-contract-core" to (coreFiles to listOf("org.springframework", "org.gradle.api")),
            "config-contract-deployment" to (deploymentFiles to listOf("org.springframework")),
        )
        val violations = checks.flatMap { (module, check) ->
            check.first.files.flatMap { source ->
                check.second.filter { reference -> source.readText().contains(reference) }
                    .map { reference -> "$module contains forbidden reference '$reference' in ${source.path}" }
            }
        } + secretSensitiveFiles.files.map { "secret-sensitive file must not be present: ${it.path}" }

        if (violations.isNotEmpty()) {
            throw org.gradle.api.GradleException(
                "Repository rule verification failed:\n" + violations.joinToString("\n"),
            )
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
}

allprojects {
    group = "io.github.inryeok-office"
    version = "0.1.0-SNAPSHOT"
}

val verifyRepositoryRules = tasks.register<VerifyRepositoryRulesTask>("verifyRepositoryRules") {
    group = "verification"
    description = "Checks repository rules that are inexpensive to enforce mechanically."

    coreFiles.from(fileTree("config-contract-core/src"), "config-contract-core/build.gradle.kts")
    deploymentFiles.from(fileTree("config-contract-deployment/src"), "config-contract-deployment/build.gradle.kts")
    secretSensitiveFiles.from(fileTree(rootDir) {
        exclude(".git/**", ".gradle/**", ".gradle-user/**", "**/build/**", "**/.env.example")
        include("**/.env", "**/.env.*", "**/*.pem", "**/*.key")
    })
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    dependencies {
        "testImplementation"(platform(rootProject.libs.junit.bom))
        "testImplementation"(rootProject.libs.junit.jupiter)
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

tasks.register("check") {
    group = "verification"
    description = "Runs repository and all subproject checks."
    dependsOn(verifyRepositoryRules)
    dependsOn(subprojects.map { it.tasks.named("check") })
}
