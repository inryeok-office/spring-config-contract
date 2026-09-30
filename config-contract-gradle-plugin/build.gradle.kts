dependencies {
    implementation(project(":config-contract-core"))
    implementation(project(":config-contract-spring"))
    implementation(project(":config-contract-deployment"))

    testImplementation(libs.spring.boot.core)
}
