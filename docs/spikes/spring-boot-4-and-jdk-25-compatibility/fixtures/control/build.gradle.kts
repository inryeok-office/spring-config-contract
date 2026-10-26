plugins {
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
    id("io.github.inryeok-office.config-contract") version "0.1.0"
}

group = "com.example"
version = "0.0.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}

// -PdeployDir=deploy-ok / deploy-ok01 selects a negative-control deployment.
val deployDir = providers.gradleProperty("deployDir").getOrElse("deploy")

configContract {
    dotenvExampleFiles.from("$deployDir/.env.example")
    composeFiles.from("$deployDir/compose.yml")
}
