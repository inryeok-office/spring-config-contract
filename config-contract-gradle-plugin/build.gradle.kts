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
