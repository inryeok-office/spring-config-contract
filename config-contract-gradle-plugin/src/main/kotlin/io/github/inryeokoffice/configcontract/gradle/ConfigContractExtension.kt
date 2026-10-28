package io.github.inryeokoffice.configcontract.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty

/**
 * The `configContract { }` build script extension.
 *
 * Deployment files are read in declaration order, `.env.example` files first.
 * [applicationConfigurationFiles] defaults to the top-level
 * `application*.properties|yml|yaml` files of the `main` resource directories.
 */
abstract class ConfigContractExtension {
    /** Spring profiles to analyze, in `spring.profiles.active` order. Empty analyzes the `default` profile. */
    abstract val activeProfiles: ListProperty<String>

    /** `.env.example` files that provide configuration. */
    abstract val dotenvExampleFiles: ConfigurableFileCollection

    /** Docker Compose files whose `services.<name>.environment` entries provide configuration. */
    abstract val composeFiles: ConfigurableFileCollection

    /**
     * Docker Compose services, by exact name, whose `environment` entries provide configuration.
     *
     * Empty (the default) reads every service in [composeFiles]. Otherwise only the named services
     * are read, as a union across every file in [composeFiles]; files are not merged as Compose
     * overlays. Each name must be declared by at least one of [composeFiles]; an unknown, blank, or
     * interpolated name, or a non-empty selection without [composeFiles], fails the check.
     */
    abstract val composeServices: SetProperty<String>

    /** Spring Boot application configuration files to analyze. */
    abstract val applicationConfigurationFiles: ConfigurableFileCollection
}
