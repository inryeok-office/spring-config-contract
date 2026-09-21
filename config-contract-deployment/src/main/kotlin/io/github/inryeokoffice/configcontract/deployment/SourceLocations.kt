package io.github.inryeokoffice.configcontract.deployment

/** Formats the diagnostic `SourceMetadata` location shared by every deployment adapter. */
internal fun sourceLocation(
    sourceName: String,
    line: Int,
): String = "$sourceName:$line"
