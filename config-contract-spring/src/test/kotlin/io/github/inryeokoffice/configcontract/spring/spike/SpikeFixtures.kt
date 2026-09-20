package io.github.inryeokoffice.configcontract.spring.spike

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer

@ConfigurationProperties("spike.kotlin")
data class KotlinConstructorProperties(
    val url: String?,
    val retries: Int = 3,
)

@ConfigurationProperties("spike.kotlin-required")
data class KotlinNonNullProperties(
    val url: String,
)

@ConfigurationProperties("spike.kotlin-mutable")
class KotlinMutableProperties {
    lateinit var url: String
    var retries: Int = 3

    fun hasUrl(): Boolean = ::url.isInitialized
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    JavaBeanProperties::class,
    JavaRecordProperties::class,
    KotlinConstructorProperties::class,
    KotlinMutableProperties::class,
)
class PropertiesConfiguration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KotlinNonNullProperties::class)
class KotlinNonNullConfiguration

/** Registers what Spring Boot's PropertyPlaceholderAutoConfiguration registers in a real application. */
@Configuration(proxyBeanMethods = false)
class PlaceholderConfiguration {
    companion object {
        @Bean
        @JvmStatic
        fun propertySourcesPlaceholderConfigurer() = PropertySourcesPlaceholderConfigurer()
    }
}

class KebabValueConsumer(
    @Value("\${spike.value.max-pool-size}") val value: String,
)

class CamelCaseValueConsumer(
    @Value("\${spike.value.maxPoolSize}") val value: String,
)

class DefaultValueConsumer(
    @Value("\${spike.value.missing:fallback}") val fallback: String,
    @Value("\${spike.value.missing-empty:}") val empty: String,
)

class MissingValueConsumer(
    @Value("\${spike.value.missing}") val value: String,
)

class FileValueConsumer(
    @Value("\${spike.value.required}") val required: String,
)

class FileDefaultValueConsumer(
    @Value("\${spike.value.with-default}") val withDefault: String,
)

@Import(PlaceholderConfiguration::class, KebabValueConsumer::class)
class KebabValueConfiguration

@Import(PlaceholderConfiguration::class, CamelCaseValueConsumer::class)
class CamelCaseValueConfiguration

@Import(PlaceholderConfiguration::class, DefaultValueConsumer::class)
class DefaultValueConfiguration

@Import(PlaceholderConfiguration::class, MissingValueConsumer::class)
class MissingValueConfiguration

@Import(MissingValueConsumer::class)
class MissingValueWithoutPlaceholderConfigurerConfiguration

@Import(PlaceholderConfiguration::class, FileValueConsumer::class)
class FileValueConfiguration

@Import(PlaceholderConfiguration::class, FileDefaultValueConsumer::class)
class FileDefaultValueConfiguration
