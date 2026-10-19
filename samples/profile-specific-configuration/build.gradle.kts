plugins {
    java
    id("io.github.inryeok-office.config-contract")
}

configContract {
    activeProfiles.set(listOf("prod"))
    dotenvExampleFiles.from("deploy/.env.example")
}
