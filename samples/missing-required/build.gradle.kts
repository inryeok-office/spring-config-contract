plugins {
    java
    id("io.github.inryeok-office.config-contract")
}

configContract {
    dotenvExampleFiles.from("deploy/.env.example")
}
