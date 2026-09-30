dependencies {
    implementation(project(":config-contract-core"))

    // Discovery delegates binding rules to Spring Boot's public APIs; see ADR 0002.
    implementation(libs.spring.boot.core)
    // Kotlin constructor nullability and default-argument metadata.
    implementation(libs.kotlin.reflect)
    // Required by Spring Boot's YamlPropertySourceLoader.
    runtimeOnly(libs.snakeyaml)

    testAnnotationProcessor(libs.spring.boot.configuration.processor)
}

tasks.named<JavaCompile>("compileTestJava") {
    // Mirrors the Spring Boot Gradle plugin, which Java constructor binding relies on.
    options.compilerArgs.add("-parameters")
    // spring-boot-configuration-processor loses field initializer defaults of unchanged
    // classes on incremental compilation, which breaks ConfigurationMetadataSpikeTest.
    options.isIncremental = false
}
