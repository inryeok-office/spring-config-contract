pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "spring-config-contract"

include(
    ":config-contract-core",
    ":config-contract-spring",
    ":config-contract-deployment",
    ":config-contract-gradle-plugin",
    ":config-contract-test",
)
