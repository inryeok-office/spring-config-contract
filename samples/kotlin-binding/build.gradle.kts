// Not standalone: the Kotlin sources under src/main/kotlin are compiled by the config-contract-gradle-plugin build,
// and the functional test points configContractCheck at the result. See samples/README.md.
plugins {
    java
    id("io.github.inryeok-office.config-contract")
}

configContract {
    dotenvExampleFiles.from("deploy/.env.example")
}
