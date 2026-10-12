import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import java.io.File

abstract class VerifyRepositoryRulesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val coreFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val deploymentFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val springFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val secretSensitiveFiles: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val checks = listOf(
            "config-contract-core" to (coreFiles to listOf("org.springframework", "org.gradle.api")),
            "config-contract-deployment" to (deploymentFiles to listOf("org.springframework")),
            "config-contract-spring" to (springFiles to listOf("org.gradle.api", "configcontract.deployment")),
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

/**
 * Creates an isolated Maven repository from the already resolved third-party runtime graph.
 *
 * The generated POMs retain the resolved graph without consulting a remote repository when the
 * external consumer fixture runs with `--offline`.
 */
@DisableCachingByDefault(because = "The disposable repository is rebuilt from the resolved runtime graph.")
abstract class SeedThirdPartyRuntimeRepositoryTask : DefaultTask() {
    init {
        outputs.upToDateWhen { false }
    }

    @get:OutputDirectory
    abstract val repositoryDirectory: DirectoryProperty

    /**
     * External artifacts required by the runtime graph. This is declared separately from the
     * serializable graph description so Gradle can track the files without retaining a
     * dependency configuration on the task.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceArtifacts: ConfigurableFileCollection

    /**
     * Resolved external module graph provided lazily by Gradle's resolution-result API. The task
     * action only consumes resolved result values and declared files; it never accesses the Gradle
     * project or dependency configuration at execution time.
     */
    @get:org.gradle.api.tasks.Internal
    abstract val rootComponent: Property<ResolvedComponentResult>

    /** Resolved artifacts paired with [sourceArtifacts] by Gradle's dependency result provider. */
    @get:org.gradle.api.tasks.Internal
    abstract val resolvedArtifacts: SetProperty<ResolvedArtifactResult>

    @get:Input
    abstract val projectGroup: Property<String>

    /** Plugin marker coordinates and their implementation targets needed by the offline consumer fixture. */
    @get:Input
    abstract val pluginMarkers: ListProperty<String>

    @TaskAction
    fun seed() {
        val repository = repositoryDirectory.get().asFile
        if (repository.exists() && !repository.deleteRecursively()) {
            throw GradleException("Could not clear third-party publication repository: $repository")
        }
        if (!repository.mkdirs() && !repository.isDirectory) {
            throw GradleException("Could not create third-party publication repository: $repository")
        }

        val artifactsByModule =
            resolvedArtifacts.get()
                .mapNotNull { artifact ->
                    val id = artifact.id.componentIdentifier as? ModuleComponentIdentifier ?: return@mapNotNull null
                    if (id.group == projectGroup.get()) null else id to artifact.file
                }.groupBy({ it.first }, { it.second })

        resolvedExternalModules(rootComponent.get(), projectGroup.get()).forEach { module ->
            val moduleDirectory =
                repository
                    .resolve(module.group.replace('.', File.separatorChar))
                    .resolve(module.name)
                    .resolve(module.version)
            moduleDirectory.mkdirs()
            val artifacts =
                artifactsByModule[module.id].orEmpty().distinct()
            artifacts.forEach { artifact ->
                artifact.copyTo(moduleDirectory.resolve(artifact.name), overwrite = true)
                if (artifact.extension == "jar" && artifact.name != "${module.name}-${module.version}.jar") {
                    artifact.copyTo(moduleDirectory.resolve("${module.name}-${module.version}.jar"), overwrite = true)
                }
            }
            moduleDirectory.resolve("${module.name}-${module.version}.pom").writeText(
                mavenPom(module, hasArtifact = artifacts.isNotEmpty()),
                Charsets.UTF_8,
            )
        }

        pluginMarkers.get().forEach { marker ->
            val parts = marker.split(':')
            require(parts.size == 6) { "Invalid plugin marker declaration: $marker" }
            val markerGroup = parts[0]
            val markerArtifact = parts[1]
            val markerVersion = parts[2]
            val targetGroup = parts[3]
            val targetArtifact = parts[4]
            val targetVersion = parts[5]
            val markerDirectory =
                repository
                    .resolve(markerGroup.replace('.', File.separatorChar))
                    .resolve(markerArtifact)
                    .resolve(markerVersion)
            markerDirectory.mkdirs()
            markerDirectory.resolve("$markerArtifact-$markerVersion.pom").writeText(
                markerPom(
                    markerGroup,
                    markerArtifact,
                    markerVersion,
                    targetGroup,
                    targetArtifact,
                    targetVersion,
                ),
                Charsets.UTF_8,
            )
        }
    }

    private fun mavenPom(module: ResolvedModule, hasArtifact: Boolean): String =
        buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">")
            appendLine("  <modelVersion>4.0.0</modelVersion>")
            appendLine("  <groupId>${xml(module.group)}</groupId>")
            appendLine("  <artifactId>${xml(module.name)}</artifactId>")
            appendLine("  <version>${xml(module.version)}</version>")
            if (!hasArtifact) {
                appendLine("  <packaging>pom</packaging>")
            }
            if (module.dependencies.isNotEmpty()) {
                appendLine("  <dependencies>")
                module.dependencies.forEach { dependency ->
                    appendLine("    <dependency>")
                    appendLine("      <groupId>${xml(dependency.group)}</groupId>")
                    appendLine("      <artifactId>${xml(dependency.name)}</artifactId>")
                    appendLine("      <version>${xml(dependency.version)}</version>")
                    appendLine("    </dependency>")
                }
                appendLine("  </dependencies>")
            }
            appendLine("</project>")
        }

    private fun markerPom(
        markerGroup: String,
        markerArtifact: String,
        markerVersion: String,
        targetGroup: String,
        targetArtifact: String,
        targetVersion: String,
    ): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>${xml(markerGroup)}</groupId>
          <artifactId>${xml(markerArtifact)}</artifactId>
          <version>${xml(markerVersion)}</version>
          <packaging>pom</packaging>
          <dependencies>
            <dependency>
              <groupId>${xml(targetGroup)}</groupId>
              <artifactId>${xml(targetArtifact)}</artifactId>
              <version>${xml(targetVersion)}</version>
            </dependency>
          </dependencies>
        </project>
        """.trimIndent() + "\n"

    private fun xml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun resolvedExternalModules(
        root: ResolvedComponentResult,
        projectGroup: String,
    ): List<ResolvedModule> {
        val components = linkedMapOf<ModuleComponentIdentifier, ResolvedComponentResult>()
        val visited = mutableSetOf<Any>()

        fun visit(component: ResolvedComponentResult) {
            if (!visited.add(component.id)) return
            val id = component.id as? ModuleComponentIdentifier
            if (id != null && id.group != projectGroup) {
                components[id] = component
            }
            component.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .forEach { visit(it.selected) }
        }

        visit(root)
        return components
            .toSortedMap(compareBy({ it.group }, { it.module }, { it.version }))
            .values
            .map { component ->
                val id = component.id as ModuleComponentIdentifier
                val dependencies =
                    component.dependencies
                        .filterIsInstance<ResolvedDependencyResult>()
                        .mapNotNull { it.selected.id as? ModuleComponentIdentifier }
                        .filter { it.group != projectGroup }
                        .distinct()
                        .sortedWith(compareBy({ it.group }, { it.module }, { it.version }))
                        .map { ModuleCoordinate(it.group, it.module, it.version) }
                ResolvedModule(id, dependencies)
            }
    }

    private data class ResolvedModule(
        val id: ModuleComponentIdentifier,
        val dependencies: List<ModuleCoordinate>,
    ) {
        val group: String = id.group
        val name: String = id.module
        val version: String = id.version
    }

    private data class ModuleCoordinate(
        val group: String,
        val name: String,
        val version: String,
    )

}

abstract class ValidateExternalPublicationReleaseTask : DefaultTask() {
    @get:Input
    abstract val explicitReleaseVersion: Property<String>

    @get:Input
    abstract val developmentVersion: Property<String>

    @TaskAction
    fun validate() {
        val version = explicitReleaseVersion.orNull?.trim()
        check(!version.isNullOrEmpty() && !version.endsWith("-SNAPSHOT")) {
            "External publication requires a non-blank, non-SNAPSHOT -PreleaseVersion; " +
                "the development default ${developmentVersion.get()} is not publishable."
        }
    }
}

abstract class ValidateRemotePublicationPreflightInputsTask : DefaultTask() {
    @get:Input
    abstract val remotePreflightEnabled: Property<Boolean>

    @get:Input
    abstract val requiredCredentialNames: ListProperty<String>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun validate() {
        check(remotePreflightEnabled.get()) {
            "Remote preflight is disabled by default; pass -PallowRemotePreflight=true from a protected environment."
        }
        val missing = requiredCredentialNames.get().filter { System.getenv(it).isNullOrBlank() }
        check(missing.isEmpty()) {
            "Remote Maven Central preflight requires protected CI secrets: ${missing.joinToString()}."
        }
        logger.lifecycle(
            "Remote inputs are valid. This task intentionally performs no network request; " +
                "a maintainer must run the documented user-managed Central deployment preflight separately.",
        )
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.dokka) apply false
}

val defaultReleaseVersion = "0.1.0-SNAPSHOT"
val releaseVersion = providers.gradleProperty("releaseVersion").orNull ?: defaultReleaseVersion

allprojects {
    group = "io.github.inryeok-office"
    version = releaseVersion
}

val publicationProjectUrl = "https://github.com/inryeok-office/spring-config-contract"
val centralLikeRepository =
    providers.gradleProperty("centralLikeRepository")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?.let(::file)
        ?: layout.buildDirectory.dir("publication-verification/central").get().asFile

val publicationDescriptions =
    mapOf(
        ":config-contract-core" to Pair(
            "Spring Config Contract Core",
            "Framework-independent configuration contract models and comparison rules.",
        ),
        ":config-contract-spring" to Pair(
            "Spring Config Contract Spring",
            "Spring Boot configuration requirement discovery for Spring Config Contract.",
        ),
        ":config-contract-deployment" to Pair(
            "Spring Config Contract Deployment",
            "Deployment configuration parsers for Spring Config Contract.",
        ),
    )

configure(publicationDescriptions.keys.map { project(it) }) {
    pluginManager.apply("java-library")
    pluginManager.apply("maven-publish")
    pluginManager.apply("signing")
    pluginManager.apply("org.jetbrains.dokka")

    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
    }

    val dokkaJavadocJar = tasks.register<Jar>("dokkaJavadocJar") {
        group = "documentation"
        description = "Packages Dokka HTML API documentation with the Maven Central javadoc classifier."
        dependsOn("dokkaGeneratePublicationHtml")
        from(layout.buildDirectory.dir("dokka/html"))
        archiveClassifier.set("javadoc")
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                artifact(dokkaJavadocJar)
                versionMapping {
                    usage("java-api") {
                        fromResolutionOf("runtimeClasspath")
                    }
                    usage("java-runtime") {
                        fromResolutionOf("runtimeClasspath")
                    }
                }
                pom {
                    val (pomName, pomDescription) = publicationDescriptions.getValue(path)
                    name.set(pomName)
                    description.set(pomDescription)
                    url.set(publicationProjectUrl)
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("inryeok-office")
                            name.set("inryeok-office")
                            url.set("https://github.com/inryeok-office")
                        }
                    }
                    scm {
                        connection.set("scm:git:$publicationProjectUrl.git")
                        developerConnection.set("scm:git:$publicationProjectUrl.git")
                        url.set(publicationProjectUrl)
                    }
                }
            }
        }
        repositories {
            maven {
                name = "centralLike"
                url = centralLikeRepository.toURI()
            }
        }
    }

    val signingKey = providers.environmentVariable("SIGNING_KEY").orNull
    if (!signingKey.isNullOrBlank()) {
        extensions.configure<SigningExtension> {
            useInMemoryPgpKeys(signingKey, providers.environmentVariable("SIGNING_PASSWORD").orNull)
            sign(extensions.getByType<PublishingExtension>().publications)
        }
    }
}

val validateExternalPublicationRelease = tasks.register<ValidateExternalPublicationReleaseTask>("validateExternalPublicationRelease") {
    group = "publishing"
    description = "Rejects blank and snapshot release versions before any external publication operation."
    explicitReleaseVersion.set(providers.gradleProperty("releaseVersion").orElse(""))
    developmentVersion.set(defaultReleaseVersion)
}

val validateRemotePublicationPreflightInputs =
    tasks.register<ValidateRemotePublicationPreflightInputsTask>("validateRemotePublicationPreflightInputs") {
        group = "publishing"
        description = "Validates explicit, protected inputs for a manually authorized remote publication preflight."
        dependsOn(validateExternalPublicationRelease)
        remotePreflightEnabled.set(providers.gradleProperty("allowRemotePreflight").map { it == "true" }.orElse(false))
        requiredCredentialNames.set(
            listOf("MAVEN_CENTRAL_USERNAME", "MAVEN_CENTRAL_PASSWORD", "SIGNING_KEY", "SIGNING_PASSWORD"),
        )
    }

val verifyRepositoryRules = tasks.register<VerifyRepositoryRulesTask>("verifyRepositoryRules") {
    group = "verification"
    description = "Checks repository rules that are inexpensive to enforce mechanically."

    coreFiles.from(fileTree("config-contract-core/src"), "config-contract-core/build.gradle.kts")
    deploymentFiles.from(fileTree("config-contract-deployment/src"), "config-contract-deployment/build.gradle.kts")
    springFiles.from(fileTree("config-contract-spring/src"), "config-contract-spring/build.gradle.kts")
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

val thirdPartyRuntimeRepository =
    providers.gradleProperty("thirdPartyRepository")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?.let(::file)
        ?: layout.buildDirectory.dir("publication-verification/third-party").get().asFile

val publicationVerificationRuntime = configurations.create("publicationVerificationRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
    description = "Resolved external runtime graph copied into the offline publication verification repository."
}

dependencies {
    add(publicationVerificationRuntime.name, project(":config-contract-gradle-plugin"))
    add(
        publicationVerificationRuntime.name,
        "org.springframework.boot:spring-boot-gradle-plugin:${libs.versions.spring.boot.get()}",
    )
    add(
        publicationVerificationRuntime.name,
        "org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}",
    )
    // Kotlin's supported Gradle plugin resolves these build tools lazily when a consumer compiles Kotlin.
    // Resolve them here so the offline coexistence fixture mirrors the complete supported plugin runtime graph.
    add(
        publicationVerificationRuntime.name,
        "org.jetbrains.kotlin:kotlin-build-tools-compat:${libs.versions.kotlin.get()}",
    )
    add(
        publicationVerificationRuntime.name,
        "org.jetbrains.kotlin:kotlin-build-tools-impl:${libs.versions.kotlin.get()}",
    )
    add(
        publicationVerificationRuntime.name,
        "org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:${libs.versions.kotlin.get()}",
    )
}

val seedThirdPartyRuntimeRepository = tasks.register<SeedThirdPartyRuntimeRepositoryTask>("seedThirdPartyRuntimeRepository") {
    group = "verification"
    description = "Copies the resolved third-party plugin runtime graph into a disposable Maven repository."
    repositoryDirectory.fileValue(thirdPartyRuntimeRepository)
    pluginMarkers.set(
        listOf(
            "org.springframework.boot:org.springframework.boot.gradle.plugin:${libs.versions.spring.boot.get()}:" +
                "org.springframework.boot:spring-boot-gradle-plugin:${libs.versions.spring.boot.get()}",
            "org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:${libs.versions.kotlin.get()}:" +
                "org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}",
        ),
    )

    val publicationProjectGroup = project.group.toString()
    val resolvedRootComponent = publicationVerificationRuntime.incoming.resolutionResult.rootComponent
    val artifacts = publicationVerificationRuntime.incoming.artifacts.resolvedArtifacts
    projectGroup.set(publicationProjectGroup)
    rootComponent.set(resolvedRootComponent)
    resolvedArtifacts.set(artifacts)
    sourceArtifacts.from(publicationVerificationRuntime.incoming.artifacts.artifactFiles)
}

val preparePublicationVerification = tasks.register("preparePublicationVerification") {
    group = "verification"
    description = "Publishes disposable Central-like and Portal-like repositories for offline consumer verification."
    dependsOn(seedThirdPartyRuntimeRepository)
    dependsOn(
        publicationDescriptions.keys.map { publicationProject ->
            project(publicationProject).tasks.named("publishAllPublicationsToCentralLikeRepository")
        },
    )
    dependsOn(project(":config-contract-gradle-plugin").tasks.named("publishAllPublicationsToPortalLikeRepository"))
}

tasks.register("check") {
    group = "verification"
    description = "Runs repository and all subproject checks."
    dependsOn(verifyRepositoryRules)
    dependsOn(subprojects.map { it.tasks.named("check") })
}
