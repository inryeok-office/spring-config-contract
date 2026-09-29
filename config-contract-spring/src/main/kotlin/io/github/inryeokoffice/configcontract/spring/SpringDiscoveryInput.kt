package io.github.inryeokoffice.configcontract.spring

/**
 * Everything [SpringConfigurationDiscovery] inspects.
 *
 * [classes] are already-loaded application classes. Discovery only reads their
 * reflective structure and annotations; it never instantiates them or runs
 * their static initializers itself. The Spring Boot annotations they use must
 * be the same classes this module was loaded with.
 *
 * [activeProfiles] is the explicit, ordered list of profiles to analyze; later
 * profiles take precedence, as with `spring.profiles.active`. An empty list
 * analyzes the application as Spring Boot does with no active profile, which
 * activates the `default` profile. Profiles are never inferred from
 * deployment input or from the application configuration itself.
 */
class SpringDiscoveryInput
    @JvmOverloads
    constructor(
        classes: Iterable<Class<*>>,
        applicationConfigurations: Iterable<ApplicationConfigurationSource> = emptyList(),
        activeProfiles: Iterable<String> = emptyList(),
    ) {
        val classes: List<Class<*>> = classes.toList()
        val applicationConfigurations: List<ApplicationConfigurationSource> = applicationConfigurations.toList()
        val activeProfiles: List<String> = activeProfiles.toList()

        override fun toString(): String =
            "SpringDiscoveryInput(classes=${classes.map { it.name }}, " +
                "applicationConfigurations=${applicationConfigurations.map { it.name }}, " +
                "activeProfiles=$activeProfiles)"
    }
