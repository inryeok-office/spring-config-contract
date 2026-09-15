package io.github.inryeokoffice.configcontract.spring.spike

import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource

/**
 * Starts a real Spring Boot application with isolated inputs.
 *
 * The OS environment and JVM system properties are replaced with explicit maps
 * so results never depend on the developer machine.
 */
internal object SpikeApplication {
    private const val NO_CONFIG_LOCATION = "optional:classpath:/spike/absent/"

    fun run(
        vararg sources: Class<*>,
        environmentVariables: Map<String, String> = emptyMap(),
        systemProperties: Map<String, String> = emptyMap(),
        configLocation: String = NO_CONFIG_LOCATION,
    ): ConfigurableApplicationContext {
        val environment = StandardEnvironment()
        environment.propertySources.replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environmentVariables),
        )
        environment.propertySources.replace(
            StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
            MapPropertySource(
                StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
                systemProperties + ("spring.config.location" to configLocation),
            ),
        )

        val application = SpringApplication(*sources)
        application.webApplicationType = WebApplicationType.NONE
        application.setBannerMode(Banner.Mode.OFF)
        application.setLogStartupInfo(false)
        application.setRegisterShutdownHook(false)
        application.setEnvironment(environment)
        return application.run()
    }

    fun rootCauseMessage(error: Throwable): String {
        var current = error
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current.message.orEmpty()
    }
}
