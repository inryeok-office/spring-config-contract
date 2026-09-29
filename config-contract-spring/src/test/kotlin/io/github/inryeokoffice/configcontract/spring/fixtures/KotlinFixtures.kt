package io.github.inryeokoffice.configcontract.spring.fixtures

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.annotation.Validated

@ConfigurationProperties("fixture.kotlin")
data class KotlinConstructorFixture(
    val nullableUrl: String?,
    val retries: Int = 3,
    val requiredUrl: String,
    val requiredPort: Int,
    @DefaultValue("fixture-name") val name: String,
    val nested: Nested,
    val tags: List<String>,
) {
    data class Nested(
        val enabled: Boolean,
    )
}

@ConfigurationProperties("fixture.kotlin-mutable")
class KotlinMutableFixture {
    lateinit var url: String
    var retries: Int = 3
    var timeout: java.time.Duration? = null
}

class KotlinValueFixture(
    @Value("\${fixture.value.required}") val required: String,
    @Value("\${fixture.value.fallback:fallback}") val fallback: String,
    @Value("\${fixture.value.empty:}") val empty: String,
    @Value("\${fixture.value.maxPoolSize}") val camelCase: String,
    @Value("#{'\${fixture.value.spel}'.toUpperCase()}") val spel: String,
    @Value("jdbc:\${fixture.db.host}:\${fixture.db.port:5432}/app") val url: String,
    @Value("\${fixture.value.nested:\${FIXTURE_NESTED}}") val nested: String,
    @Value("literal") val literal: String,
)

@Validated
@ConfigurationProperties("fixture.validated")
class ValidatedFixture {
    var url: String? = null
}

@ConfigurationProperties("fixture.invalidPrefix")
class InvalidPrefixFixture {
    var url: String? = null
}

@Configuration(proxyBeanMethods = false)
class BeanMethodFixture {
    @Bean
    @ConfigurationProperties("fixture.bean-method")
    fun beanMethodProperties(): KotlinMutableFixture = KotlinMutableFixture()
}

/** Consumers of one key in several ways, used to verify how occurrences merge. */
class SharedKeyFixture(
    @Value("\${fixture.shared.optional:first}") val first: String,
    @Value("\${fixture.shared.optional:second}") val second: String,
    @Value("\${fixture.shared.agreed:same}") val agreedFirst: String,
    @Value("\${fixture.shared.agreed:same}") val agreedSecond: String,
    @Value("\${fixture.shared.strict:lenient}") val lenient: String,
    @Value("\${fixture.shared.strict}") val strict: String,
)

// One class per requiredness case, so each can be started in a real Spring application in isolation.

@ConfigurationProperties("crosscheck.required-string")
data class RequiredStringProperties(
    val value: String,
)

@ConfigurationProperties("crosscheck.required-int")
data class RequiredIntProperties(
    val value: Int,
)

@ConfigurationProperties("crosscheck.nullable")
data class NullableProperties(
    val value: String?,
)

@ConfigurationProperties("crosscheck.defaulted")
data class DefaultedProperties(
    val value: Int = 3,
)

@ConfigurationProperties("crosscheck.default-value")
data class DefaultValueAnnotatedProperties(
    @DefaultValue("7") val value: Int,
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RequiredStringProperties::class)
class RequiredStringConfiguration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RequiredIntProperties::class)
class RequiredIntConfiguration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NullableProperties::class)
class NullableConfiguration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DefaultedProperties::class)
class DefaultedConfiguration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DefaultValueAnnotatedProperties::class)
class DefaultValueAnnotatedConfiguration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JavaRecordFixture::class)
class JavaRecordConfiguration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JavaBeanFixture::class)
class JavaBeanConfiguration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KotlinMutableFixture::class)
class KotlinMutableConfiguration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SetterVisibilityProperties::class)
class SetterVisibilityConfiguration

/** Holds the escaped placeholder, which Spring must inject literally. */
class EscapedValueConsumer(
    @Value("\\\${crosscheck.escaped}") val value: String,
)
