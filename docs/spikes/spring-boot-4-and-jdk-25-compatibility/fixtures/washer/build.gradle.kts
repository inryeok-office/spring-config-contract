plugins {
    java
    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("io.github.inryeok-office.config-contract") version "0.1.0"
}

group = "com.example"
version = "0.0.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
}

// -PcontractVariant=ok selects the negative-control deployment that supplies every required key.
val deployDir = if (providers.gradleProperty("contractVariant").orNull == "ok") "deploy-ok" else "deploy"

configContract {
    dotenvExampleFiles.from("$deployDir/.env.example")
    composeFiles.from("$deployDir/compose.yml")
}

tasks.register("printSpringBootRuntimeVersion") {
    val artifacts = configurations.runtimeClasspath.get().incoming.artifacts.resolvedArtifacts
    doLast {
        artifacts.get()
            .map { it.id.componentIdentifier.displayName }
            .filter { it.startsWith("org.springframework") }
            .sorted()
            .forEach { println("runtimeClasspath: $it") }
    }
}
