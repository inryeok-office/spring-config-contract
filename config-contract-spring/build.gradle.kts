dependencies {
    implementation(project(":config-contract-core"))

    // Issue #17 spike evidence only. Production Spring dependencies are decided
    // by the extractor implementation, not by these behavior probes.
    testImplementation(libs.spring.boot.core)
    testRuntimeOnly(libs.snakeyaml)
    testRuntimeOnly(libs.kotlin.reflect)
    testAnnotationProcessor(libs.spring.boot.configuration.processor)
}

tasks.named<JavaCompile>("compileTestJava") {
    // Mirrors the Spring Boot Gradle plugin, which Java constructor binding relies on.
    options.compilerArgs.add("-parameters")
}
