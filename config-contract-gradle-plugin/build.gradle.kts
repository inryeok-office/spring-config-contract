import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    `java-gradle-plugin`
}

dependencies {
    implementation(project(":config-contract-core"))
    implementation(project(":config-contract-spring"))
    implementation(project(":config-contract-deployment"))

    testImplementation(libs.spring.boot.core)
}

gradlePlugin {
    plugins {
        create("configContract") {
            id = "io.github.inryeok-office.config-contract"
            implementationClass = "io.github.inryeokoffice.configcontract.gradle.ConfigContractPlugin"
            displayName = "Spring Config Contract"
            description = "Checks Spring Boot configuration requirements against deployment-provided configuration."
        }
    }
}

// The Kotlin sample cannot be compiled inside a TestKit build: that would need the Kotlin Gradle plugin from the
// Plugin Portal, which offline tests must not reach. It is compiled here instead, in a source set that is
// deliberately not part of `main` or `test`, so it is never packaged and never on a runtime classpath.
val kotlinSample: SourceSet = sourceSets.create("kotlinSample")

extensions.configure<KotlinJvmProjectExtension> {
    sourceSets
        .getByName(kotlinSample.name)
        .kotlin
        .srcDir(rootProject.layout.projectDirectory.dir("samples/kotlin-binding/src/main/kotlin"))
}

dependencies {
    // Compile-only: the sample needs the Spring Boot annotations to compile and nothing at run time.
    add(kotlinSample.compileOnlyConfigurationName, libs.spring.boot.core)
}

/** Passes the sample locations to tests as system properties without making absolute paths part of the cache key. */
class SampleLocations(
    @get:Internal val samples: Provider<Directory>,
    @get:Internal val kotlinSampleClasses: FileCollection,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        listOf(
            "-DconfigContract.samplesDir=${samples.get().asFile.absolutePath}",
            "-DconfigContract.kotlinSampleClassesDirs=" +
                kotlinSampleClasses.files.joinToString(File.pathSeparator) { it.absolutePath },
        )
}

tasks.test {
    val samples = rootProject.layout.projectDirectory.dir("samples")
    dependsOn(kotlinSample.classesTaskName)
    inputs
        .dir(samples)
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("samples")
    inputs
        .files(kotlinSample.output.classesDirs)
        .withNormalizer(ClasspathNormalizer::class)
        .withPropertyName("kotlinSampleClasses")
    jvmArgumentProviders.add(SampleLocations(provider { samples }, kotlinSample.output.classesDirs))
}
