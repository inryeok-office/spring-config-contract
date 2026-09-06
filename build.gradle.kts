import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
}

allprojects {
    group = "io.github.inryeok-office"
    version = "0.1.0-SNAPSHOT"
}

val verifyRepositoryRules = tasks.register("verifyRepositoryRules") {
    group = "verification"
    description = "Checks repository rules that are inexpensive to enforce mechanically."

    doLast {
        fun sourceFiles(vararg paths: String): List<java.io.File> = paths.flatMap { path ->
            val target = file(path)
            if (target.isDirectory) fileTree(target).files.toList() else listOf(target)
        }

        val forbiddenReferences = mapOf(
            "config-contract-core" to listOf("org.springframework", "org.gradle.api"),
            "config-contract-deployment" to listOf("org.springframework"),
        )
        val violations = forbiddenReferences.flatMap { (module, references) ->
            sourceFiles("$module/src", "$module/build.gradle.kts").flatMap { source ->
                references.filter { reference -> source.readText().contains(reference) }
                    .map { reference -> "$module contains forbidden reference '$reference' in ${source.path}" }
            }
        }

        val secretSensitiveFiles = fileTree(rootDir) {
            exclude(".git/**", ".gradle/**", ".gradle-user/**", "**/build/**", "**/.env.example")
            include("**/.env", "**/.env.*", "**/*.pem", "**/*.key")
        }.files
        violations.plus(secretSensitiveFiles.map { "secret-sensitive file must not be present: ${it.path}" })
            .also { allViolations ->
                if (allViolations.isNotEmpty()) {
                    throw GradleException(
                        "Repository rule verification failed:\n" + allViolations.joinToString("\n"),
                    )
                }
            }
    }
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
