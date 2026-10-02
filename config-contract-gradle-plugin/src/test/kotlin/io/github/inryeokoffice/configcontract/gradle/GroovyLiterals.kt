package io.github.inryeokoffice.configcontract.gradle

import java.io.File

/**
 * Renders [value] as a Groovy single-quoted string literal. The backslash is escaped first so the backslashes added
 * for apostrophes are not escaped a second time. Single-quoted Groovy strings do not interpolate `$`.
 */
internal fun groovySingleQuoted(value: String): String = "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"

/** Renders [files] as a Groovy list literal of single-quoted, forward-slash paths. */
internal fun groovyPathList(files: List<File>): String =
    files.joinToString(prefix = "[", postfix = "]") { groovySingleQuoted(it.invariantSeparatorsPath) }
