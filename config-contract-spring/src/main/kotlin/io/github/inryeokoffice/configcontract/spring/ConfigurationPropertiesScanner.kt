package io.github.inryeokoffice.configcontract.spring

import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.DefaultValue
import io.github.inryeokoffice.configcontract.core.ConfigurationRequirement.Presence
import io.github.inryeokoffice.configcontract.core.SourceMetadata
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.BindConstructorProvider
import org.springframework.boot.context.properties.bind.DataObjectPropertyName
import org.springframework.boot.context.properties.bind.Name
import org.springframework.boot.context.properties.source.ConfigurationPropertyName
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.KotlinDetector
import java.beans.Introspector
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.jvm.kotlinFunction
import org.springframework.boot.context.properties.bind.DefaultValue as SpringDefaultValue

/**
 * Discovers requirements from classes annotated directly with `@ConfigurationProperties`.
 *
 * Whether Spring Boot uses constructor or JavaBean binding is decided by Spring's own
 * [BindConstructorProvider.DEFAULT], and property names use Spring's
 * [DataObjectPropertyName] dashed form, so v0.1 does not re-implement either rule.
 */
internal class ConfigurationPropertiesScanner(
    private val collector: DiscoveryCollector,
) {
    private val parameterNames = DefaultParameterNameDiscoverer()

    fun scan(type: Class<*>) {
        type.declaredMethods
            .filter { it.isAnnotationPresent(ConfigurationProperties::class.java) }
            .sortedBy { it.name }
            .forEach {
                collector.unsupported(
                    "${type.name}.${it.name}()",
                    "@ConfigurationProperties on a @Bean method is not supported; its keys are not discovered",
                )
            }

        val annotation = type.getDeclaredAnnotation(ConfigurationProperties::class.java) ?: return
        val prefix = annotation.prefix.ifEmpty { annotation.value }
        if (!ConfigurationPropertyName.isValid(prefix)) {
            collector.problem(
                type.name,
                "@ConfigurationProperties prefix '$prefix' is not in canonical form; Spring Boot requires " +
                    "lowercase letters, digits, '-', and '.' separators",
            )
            return
        }
        if (type.annotations.any { it.annotationClass.qualifiedName == VALIDATED }) {
            collector.unsupported(
                type.name,
                "@Validated is not interpreted; Bean Validation constraints do not change requiredness in v0.1",
            )
        }

        val constructor =
            try {
                BindConstructorProvider.DEFAULT.getBindConstructor(type, false)
            } catch (exception: IllegalStateException) {
                collector.problem(type.name, exception.message ?: "Spring Boot cannot determine the bind constructor")
                return
            }
        if (constructor == null) {
            scanJavaBean(type, prefix)
        } else {
            scanConstructor(type, prefix, constructor)
        }
    }

    private fun scanConstructor(
        type: Class<*>,
        prefix: String,
        constructor: Constructor<*>,
    ) {
        val names = parameterNames.getParameterNames(constructor)
        if (names == null) {
            collector.unsupported(
                type.name,
                "Constructor parameter names are unavailable; compile Java with -parameters for constructor binding",
            )
            return
        }
        val kotlinType = KotlinDetector.isKotlinType(type)
        val kotlinParameters = if (kotlinType) constructor.kotlinFunction?.parameters else null
        if (kotlinType && kotlinParameters == null) {
            collector.unsupported(
                type.name,
                "Kotlin constructor metadata is unavailable; every property is treated as optional",
            )
        }

        constructor.parameters.forEachIndexed { index, parameter ->
            val propertyName = parameter.getAnnotation(Name::class.java)?.value ?: names[index]
            val location = "${type.name}#${names[index]}"
            if (!ScalarTypes.isScalar(parameter.type)) {
                unsupportedType(location, parameter.type)
                return@forEachIndexed
            }
            reportValidationConstraints(location, parameter)
            val defaultValue = defaultValueOf(location, parameter)
            // Spring passes null for a missing value; only a Kotlin non-null parameter
            // without a Kotlin or @DefaultValue default then fails binding.
            val kotlinParameter = kotlinParameters?.getOrNull(index)
            val required =
                kotlinParameter != null &&
                    !kotlinParameter.isOptional &&
                    !kotlinParameter.type.isMarkedNullable &&
                    defaultValue == DefaultValue.Absent
            record(prefix, propertyName, location, if (required) Presence.REQUIRED else Presence.OPTIONAL, defaultValue)
        }
    }

    private fun defaultValueOf(
        location: String,
        parameter: AnnotatedElement,
    ): DefaultValue {
        val values = parameter.getAnnotation(SpringDefaultValue::class.java)?.value ?: return DefaultValue.Absent
        if (values.size == 1) return DefaultValue.Present(values.single())
        collector.unsupported(
            location,
            "@DefaultValue with ${values.size} values is not supported for a single value; the default is treated as unknown",
        )
        return DefaultValue.Absent
    }

    /**
     * Mirrors the property discovery of Spring Boot's `JavaBeanBinder`.
     *
     * Field initializers are not evaluated, because that would run application
     * code, so JavaBean defaults are recorded as unknown ([DefaultValue.Absent]).
     */
    private fun scanJavaBean(
        type: Class<*>,
        prefix: String,
    ) {
        val properties = sortedMapOf<String, JavaBeanProperty>()
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            val methods = current.declaredMethods.filter(::isCandidate).sortedBy { it.name }
            for ((methodPrefix, parameterCount) in ACCESSOR_PREFIXES) {
                methods
                    .filter { it.parameterCount == parameterCount }
                    .filter { it.name.startsWith(methodPrefix) && it.name.length > methodPrefix.length }
                    .forEach {
                        val name = Introspector.decapitalize(it.name.substring(methodPrefix.length))
                        val property = properties.getOrPut(name) { JavaBeanProperty(name) }
                        if (parameterCount == 0) {
                            property.getter = property.getter ?: it
                        } else {
                            property.setter = property.setter ?: it
                        }
                    }
            }
            current.declaredFields.forEach { field -> properties[field.name]?.let { it.field = it.field ?: field } }
            current = current.superclass
        }

        for (property in properties.values) {
            val location = "${type.name}#${property.name}"
            val setter = property.setter
            val propertyType = setter?.parameterTypes?.single() ?: property.getter!!.returnType
            if (!ScalarTypes.isScalar(propertyType)) {
                unsupportedType(location, propertyType)
                continue
            }
            // A scalar without a setter cannot be bound, so it is not a configuration key.
            if (setter == null) continue
            property.field?.let { reportValidationConstraints(location, it) }
            record(prefix, property.name, location, Presence.OPTIONAL, DefaultValue.Absent)
        }
    }

    /**
     * Same filter as `JavaBeanBinder`, which is not `java.beans` introspection: package-private
     * accessors are bindable, and only private and protected ones are excluded.
     */
    private fun isCandidate(method: Method): Boolean {
        val modifiers = method.modifiers
        return !Modifier.isPrivate(modifiers) &&
            !Modifier.isProtected(modifiers) &&
            !Modifier.isAbstract(modifiers) &&
            !Modifier.isStatic(modifiers) &&
            !method.isBridge &&
            method.declaringClass != Any::class.java &&
            method.declaringClass != Class::class.java &&
            '$' !in method.name
    }

    private fun record(
        prefix: String,
        propertyName: String,
        location: String,
        presence: Presence,
        defaultValue: DefaultValue,
    ) {
        val dashed = DataObjectPropertyName.toDashedForm(propertyName)
        val key = if (prefix.isEmpty()) dashed else "$prefix.$dashed"
        if (!ConfigurationPropertyName.isValid(key)) {
            collector.unsupported(location, "Property name '$propertyName' does not map to a canonical key; it is not discovered")
            return
        }
        collector.occurrence(KeyOccurrence(key, presence, defaultValue, SourceMetadata(location), KeyOccurrence.Origin.CODE))
    }

    private fun unsupportedType(
        location: String,
        type: Class<*>,
    ) {
        collector.unsupported(
            location,
            "Property type ${type.typeName} is not a supported single value (${ScalarTypes.DESCRIPTION}); " +
                "nested, collection, and map binding are not discovered in v0.1",
        )
    }

    private fun reportValidationConstraints(
        location: String,
        element: AnnotatedElement,
    ) {
        val constraints =
            element.annotations
                .mapNotNull { it.annotationClass.qualifiedName }
                .filter { name -> VALIDATION_PACKAGES.any { name.startsWith(it) } }
                .sorted()
        if (constraints.isNotEmpty()) {
            collector.unsupported(
                location,
                "Bean Validation constraints ${constraints.joinToString()} are not interpreted; requiredness ignores them",
            )
        }
    }

    private class JavaBeanProperty(
        val name: String,
    ) {
        var getter: Method? = null
        var setter: Method? = null
        var field: Field? = null
    }

    private companion object {
        const val VALIDATED = "org.springframework.validation.annotation.Validated"
        val VALIDATION_PACKAGES = listOf("jakarta.validation.constraints.", "javax.validation.constraints.")
        val ACCESSOR_PREFIXES = listOf("is" to 0, "get" to 0, "set" to 1)
    }
}
