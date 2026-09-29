package io.github.inryeokoffice.configcontract.spring

import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.time.Duration
import java.time.Period
import java.util.Locale
import java.util.UUID

/**
 * Property types that v0.1 treats as a single configuration value.
 *
 * Anything else, including nested objects, arrays, collections, and maps, is
 * reported as unsupported instead of guessing how Spring would bind it.
 */
internal object ScalarTypes {
    private val SUPPORTED: Set<Class<*>> =
        setOf(
            String::class.java,
            CharSequence::class.java,
            java.lang.Boolean::class.java,
            java.lang.Character::class.java,
            java.lang.Byte::class.java,
            java.lang.Short::class.java,
            java.lang.Integer::class.java,
            java.lang.Long::class.java,
            java.lang.Float::class.java,
            java.lang.Double::class.java,
            BigDecimal::class.java,
            BigInteger::class.java,
            Duration::class.java,
            Period::class.java,
            Charset::class.java,
            Locale::class.java,
            UUID::class.java,
            URI::class.java,
            URL::class.java,
            org.springframework.util.unit.DataSize::class.java,
        )

    fun isScalar(type: Class<*>): Boolean = type.isPrimitive || type.isEnum || type in SUPPORTED

    /** Human-readable description of the supported types, for diagnostics and documentation. */
    const val DESCRIPTION =
        "String, primitives and their wrappers, enums, BigDecimal, BigInteger, Duration, Period, " +
            "Charset, Locale, UUID, URI, URL, and DataSize"
}
