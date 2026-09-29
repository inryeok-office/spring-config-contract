package io.github.inryeokoffice.configcontract.spring

import io.github.inryeokoffice.configcontract.core.ConfigurationKey
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.DefaultValue
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.Presence
import io.github.inryeokoffice.configcontract.core.ProvidedConfiguration
import io.github.inryeokoffice.configcontract.spring.fixtures.DefaultValueAnnotatedConfiguration
import io.github.inryeokoffice.configcontract.spring.fixtures.DefaultValueAnnotatedProperties
import io.github.inryeokoffice.configcontract.spring.fixtures.DefaultedConfiguration
import io.github.inryeokoffice.configcontract.spring.fixtures.DefaultedProperties
import io.github.inryeokoffice.configcontract.spring.fixtures.EscapedValueConsumer
import io.github.inryeokoffice.configcontract.spring.fixtures.JavaBeanConfiguration
import io.github.inryeokoffice.configcontract.spring.fixtures.JavaBeanFixture
import io.github.inryeokoffice.configcontract.spring.fixtures.JavaRecordConfiguration
import io.github.inryeokoffice.configcontract.spring.fixtures.JavaRecordFixture
import io.github.inryeokoffice.configcontract.spring.fixtures.KotlinMutableConfiguration
import io.github.inryeokoffice.configcontract.spring.fixtures.KotlinMutableFixture
import io.github.inryeokoffice.configcontract.spring.fixtures.NullableConfiguration
import io.github.inryeokoffice.configcontract.spring.fixtures.NullableProperties
import io.github.inryeokoffice.configcontract.spring.fixtures.RequiredIntConfiguration
import io.github.inryeokoffice.configcontract.spring.fixtures.RequiredIntProperties
import io.github.inryeokoffice.configcontract.spring.fixtures.RequiredStringConfiguration
import io.github.inryeokoffice.configcontract.spring.fixtures.RequiredStringProperties
import io.github.inryeokoffice.configcontract.spring.spike.PlaceholderConfiguration
import io.github.inryeokoffice.configcontract.spring.spike.SpikeApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.context.annotation.Import

/**
 * Checks discovery rules against a real Spring Boot application started with isolated inputs.
 *
 * Each case asks Spring for the observable behavior and asserts that discovery
 * predicts it, so a rule that diverges from Spring fails here.
 */
class SpringBehaviorCrossCheckTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("requirednessCases")
    fun `discovered requiredness predicts whether Spring starts without configuration`(
        properties: Class<*>,
        configuration: Class<*>,
    ) {
        val discoveredRequired = discover(properties).requirements.any { it.presence == Presence.REQUIRED }

        val springFailed = runCatching { SpikeApplication.run(configuration).close() }.isFailure

        assertEquals(springFailed, discoveredRequired)
    }

    @ParameterizedTest(name = "profiles={0}")
    @ValueSource(strings = ["", "prod", "prod,extra", "extra,prod", "other", "default"])
    fun `effective configured values match the Spring environment`(profiles: String) {
        val activeProfiles = profiles.split(',').filter { it.isNotEmpty() }
        val discovered =
            discover(configurations = configurationSources("discovery/precedence"), profiles = activeProfiles)
                .requirements
                .associate { it.key.value to (it.defaultValue as? DefaultValue.Present)?.value }

        val springValues =
            SpikeApplication
                .run(
                    NullableConfiguration::class.java,
                    environmentVariables = if (profiles.isEmpty()) emptyMap() else mapOf("SPRING_PROFILES_ACTIVE" to profiles),
                    configLocation = "classpath:/discovery/precedence/",
                ).use { context -> PRECEDENCE_KEYS.associateWith { context.environment.getProperty(it) } }

        assertEquals(springValues, PRECEDENCE_KEYS.associateWith { discovered[it] })
    }

    @Test
    fun `escaped placeholder is injected literally, so discovery finds no key`() {
        val value =
            SpikeApplication
                .run(EscapedValueConfiguration::class.java)
                .use { it.getBean(EscapedValueConsumer::class.java).value }

        assertEquals("\${crosscheck.escaped}", value)
        assertEquals(emptyList<ConfigurationRequirement>(), discover(EscapedValueConsumer::class.java).requirements)
    }

    @ParameterizedTest(name = "{0} <- {1}")
    @MethodSource("environmentKeyCases")
    fun `aligned keys match what the Spring environment resolves`(
        requirementKey: String,
        variable: String,
    ) {
        val springResolved =
            SpikeApplication
                .run(NullableConfiguration::class.java, environmentVariables = mapOf(variable to "value"))
                .use { it.environment.containsProperty(requirementKey) }

        val aligned =
            SpringEnvironmentKeys.align(
                listOf(ConfigurationRequirement(ConfigurationKey.of(requirementKey))),
                listOf(ProvidedConfiguration(ConfigurationKey.of(variable))),
            )

        assertEquals(springResolved, aligned.single().key.value == requirementKey)
    }

    @Import(PlaceholderConfiguration::class, EscapedValueConsumer::class)
    class EscapedValueConfiguration

    companion object {
        private val PRECEDENCE_KEYS =
            listOf(
                "shared.key",
                "shared.yml-only",
                "properties.only",
                "layered.key",
                "layered.document-only",
                "layered.prod-file-only",
                "layered.list-activated",
                "default.document-only",
                "default.file-only",
            )

        @JvmStatic
        fun requirednessCases(): List<Arguments> =
            listOf(
                Arguments.of(RequiredStringProperties::class.java, RequiredStringConfiguration::class.java),
                Arguments.of(RequiredIntProperties::class.java, RequiredIntConfiguration::class.java),
                Arguments.of(NullableProperties::class.java, NullableConfiguration::class.java),
                Arguments.of(DefaultedProperties::class.java, DefaultedConfiguration::class.java),
                Arguments.of(DefaultValueAnnotatedProperties::class.java, DefaultValueAnnotatedConfiguration::class.java),
                Arguments.of(JavaRecordFixture::class.java, JavaRecordConfiguration::class.java),
                Arguments.of(JavaBeanFixture::class.java, JavaBeanConfiguration::class.java),
                Arguments.of(KotlinMutableFixture::class.java, KotlinMutableConfiguration::class.java),
            )

        @JvmStatic
        fun environmentKeyCases(): List<Arguments> =
            listOf(
                Arguments.of("spring.datasource.url", "SPRING_DATASOURCE_URL"),
                Arguments.of("spike.java-bean.max-pool-size", "SPIKE_JAVABEAN_MAXPOOLSIZE"),
                Arguments.of("spike.java-bean.max-pool-size", "SPIKE_JAVA_BEAN_MAX_POOL_SIZE"),
                Arguments.of("spike.java-bean.max-pool-size", "SPIKE_JAVABEAN_MAXPOOL"),
                Arguments.of("SPIKE_DATABASE_NAME", "SPIKE_DATABASE_NAME"),
                Arguments.of("SPIKE_DATABASE_NAME", "spike_database_name"),
                Arguments.of("spike.value.maxPoolSize", "SPIKE_VALUE_MAXPOOLSIZE"),
                Arguments.of("spike.value.maxPoolSize", "SPIKE_VALUE_MAX_POOL_SIZE"),
                Arguments.of("my.list[0]", "MY_LIST_0"),
            )
    }
}
