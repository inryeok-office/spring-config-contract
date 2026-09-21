package io.github.inryeokoffice.configcontract.deployment.compose

import io.github.inryeokoffice.configcontract.deployment.DeploymentInputException
import io.github.inryeokoffice.configcontract.deployment.InputProblem
import io.github.inryeokoffice.configcontract.deployment.KeySyntax
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.lowlevel.Compose
import org.snakeyaml.engine.v2.common.ScalarStyle
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.NodeTuple
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode
import org.snakeyaml.engine.v2.nodes.Tag

/**
 * Parses `services.<name>.environment` out of Docker Compose YAML content.
 *
 * Deterministic and pure: no file I/O, host environment access, shell
 * execution, network access, or `${VAR}` / `$VAR` expansion. Uses the
 * snakeyaml-engine Node API (compose, not construct) so no Java object is
 * ever built from YAML content. Parser-internal; it must never appear in
 * core or in any public signature.
 */
internal object ComposeParser {
    /** Compose files are expected to be small; this guards against oversized/adversarial input. */
    private const val CODE_POINT_LIMIT = 1024 * 1024

    /** Matches the snakeyaml-engine default; set explicitly to document the intent. */
    private const val MAX_ALIASES_FOR_COLLECTIONS = 50

    private const val MERGE_KEY = "<<"
    private val NULL_LITERALS = setOf("~", "null", "Null", "NULL")
    private val BOOL_LITERALS = setOf("true", "True", "TRUE", "false", "False", "FALSE")
    private val UNSUPPORTED_SERVICE_KEYS = listOf("env_file", "extends")

    /** @throws DeploymentInputException if [content] contains any problem, reported in line order. */
    fun parse(
        sourceName: String,
        content: String,
    ): List<ComposeEntry> {
        val problems = mutableListOf<InputProblem>()
        val documents =
            try {
                composeAll(sourceName, content)
            } catch (e: MarkedYamlEngineException) {
                throw DeploymentInputException(sourceName, listOf(InputProblem(e.line(), "Invalid YAML syntax")))
            } catch (e: YamlEngineException) {
                throw DeploymentInputException(sourceName, listOf(InputProblem(1, "Invalid YAML syntax")))
            }

        if (documents.isEmpty()) {
            return emptyList()
        }
        if (documents.size > 1) {
            problems += InputProblem(documents[1].line(), "Multiple YAML documents are not supported")
            throw DeploymentInputException(sourceName, problems)
        }

        val root = documents.single()
        if (root !is MappingNode) {
            problems += InputProblem(root.line(), "Root must be a YAML mapping")
            throw DeploymentInputException(sourceName, problems)
        }

        val rootEntries = mappingEntries(root, problems)
        rootEntries[TOP_LEVEL_INCLUDE]?.let { tuple ->
            problems += InputProblem(tuple.keyNode.line(), unsupportedConstructMessage(TOP_LEVEL_INCLUDE))
        }

        val servicesTuple = rootEntries[SERVICES_KEY]
        if (servicesTuple == null) {
            problems += InputProblem(root.line(), "Missing '$SERVICES_KEY' mapping")
            throw DeploymentInputException(sourceName, problems)
        }
        val servicesNode = servicesTuple.valueNode
        if (servicesNode !is MappingNode) {
            problems += InputProblem(servicesNode.line(), "'$SERVICES_KEY' must be a mapping")
            throw DeploymentInputException(sourceName, problems)
        }

        val entries = mutableListOf<ComposeEntry>()
        for ((serviceName, serviceTuple) in mappingEntries(servicesNode, problems)) {
            if (serviceName.contains('$')) {
                problems +=
                    InputProblem(
                        serviceTuple.keyNode.line(),
                        "Service name '$serviceName' must not contain interpolation",
                    )
                continue
            }

            val serviceNode = serviceTuple.valueNode
            if (serviceNode !is MappingNode) {
                // Only a null placeholder body (`web:` or `web: ~`) means "no configuration".
                if (!serviceNode.isNullScalar()) {
                    problems += InputProblem(serviceNode.line(), "Service '$serviceName' must be a mapping")
                }
                continue
            }

            val serviceEntries = mappingEntries(serviceNode, problems)
            for (unsupportedKey in UNSUPPORTED_SERVICE_KEYS) {
                serviceEntries[unsupportedKey]?.let { tuple ->
                    problems += InputProblem(tuple.keyNode.line(), unsupportedConstructMessage(unsupportedKey))
                }
            }

            val environmentTuple = serviceEntries[ENVIRONMENT_KEY] ?: continue
            entries += environmentEntries(environmentTuple.valueNode, problems)
        }

        if (problems.isNotEmpty()) {
            throw DeploymentInputException(sourceName, problems.sortedBy { it.line })
        }
        return entries
    }

    private fun composeAll(
        sourceName: String,
        content: String,
    ): List<Node> {
        val settings =
            LoadSettings
                .builder()
                .setLabel(sourceName)
                .setCodePointLimit(CODE_POINT_LIMIT)
                // Duplicates are detected and reported by this parser (in line order) instead of
                // letting the engine abort composing at the first one it finds.
                .setAllowDuplicateKeys(true)
                .setAllowRecursiveKeys(false)
                .setMaxAliasesForCollections(MAX_ALIASES_FOR_COLLECTIONS)
                .build()
        return Compose(settings).composeAllFromString(content).toList()
    }

    /**
     * Resolves a mapping's entries in document order, flagging non-scalar keys, the YAML merge
     * key (`<<`), and duplicate keys as [InputProblem]s instead of throwing, so every problem in
     * [node] is collected in one pass rather than aborting at the first one.
     */
    private fun mappingEntries(
        node: MappingNode,
        problems: MutableList<InputProblem>,
    ): LinkedHashMap<String, NodeTuple> {
        val result = LinkedHashMap<String, NodeTuple>()
        for (tuple in node.value) {
            val keyNode = tuple.keyNode
            if (keyNode !is ScalarNode) {
                problems += InputProblem(keyNode.line(), "Mapping key must be a string")
                continue
            }
            val key = keyNode.value
            if (key == MERGE_KEY) {
                problems += InputProblem(keyNode.line(), unsupportedConstructMessage(MERGE_KEY))
                continue
            }
            if (!keyNode.isStringScalar()) {
                // Unquoted `true`, `123`, `null` etc. resolve to non-string tags.
                problems += InputProblem(keyNode.line(), "Mapping key must be a string")
                continue
            }
            val firstOccurrence = result[key]
            if (firstOccurrence != null) {
                problems += duplicateKeyProblem(key, keyNode.line(), firstOccurrence.keyNode.line())
                continue
            }
            result[key] = tuple
        }
        return result
    }

    private fun environmentEntries(
        environmentNode: Node,
        problems: MutableList<InputProblem>,
    ): List<ComposeEntry> =
        when {
            environmentNode.isNullScalar() -> emptyList()
            environmentNode is MappingNode -> mapFormEntries(environmentNode, problems)
            environmentNode is SequenceNode -> listFormEntries(environmentNode, problems)
            else -> {
                problems += InputProblem(environmentNode.line(), "'$ENVIRONMENT_KEY' must be a list or a mapping")
                emptyList()
            }
        }

    private fun mapFormEntries(
        node: MappingNode,
        problems: MutableList<InputProblem>,
    ): List<ComposeEntry> {
        val entries = mutableListOf<ComposeEntry>()
        for ((key, tuple) in mappingEntries(node, problems)) {
            val keyLine = tuple.keyNode.line()
            if (!validateKey(key, keyLine, problems)) continue

            val valueNode = tuple.valueNode
            if (valueNode is MappingNode || valueNode is SequenceNode) {
                problems += InputProblem(valueNode.line(), "Value for key '$key' must not be a nested map or list")
                continue
            }
            entries += ComposeEntry(key, keyLine)
        }
        return entries
    }

    private fun listFormEntries(
        node: SequenceNode,
        problems: MutableList<InputProblem>,
    ): List<ComposeEntry> {
        val entries = mutableListOf<ComposeEntry>()
        val firstLineByKey = mutableMapOf<String, Int>()
        for (item in node.value) {
            if (item !is ScalarNode || !item.isStringScalar()) {
                problems += InputProblem(item.line(), "Environment list item must be a string")
                continue
            }
            val itemLine = item.line()
            val text = item.value
            val equalsIndex = text.indexOf('=')
            val key = if (equalsIndex < 0) text.trim() else text.substring(0, equalsIndex).trim()
            if (!validateKey(key, itemLine, problems)) continue

            val firstLine = firstLineByKey[key]
            if (firstLine != null) {
                problems += duplicateKeyProblem(key, itemLine, firstLine)
                continue
            }
            firstLineByKey[key] = itemLine
            entries += ComposeEntry(key, itemLine)
        }
        return entries
    }

    /** Interpolation is checked before syntax so an interpolated key reports exactly one problem. */
    private fun validateKey(
        key: String,
        line: Int,
        problems: MutableList<InputProblem>,
    ): Boolean =
        when {
            key.contains('$') -> {
                problems += InputProblem(line, "Key '$key' must not contain interpolation")
                false
            }
            !KeySyntax.isValid(key) -> {
                problems += InputProblem(line, "Invalid key '$key'; keys must match ${KeySyntax.DESCRIPTION}")
                false
            }
            else -> true
        }

    private fun unsupportedConstructMessage(construct: String): String = "Docker Compose construct '$construct' is not supported"

    private fun duplicateKeyProblem(
        key: String,
        line: Int,
        firstLine: Int,
    ): InputProblem = InputProblem(line, "Duplicate key '$key' (first defined at line $firstLine)")

    /**
     * 1-based line for [this] node. An aliased node (`*name`) is the same object as the node its
     * anchor (`&name`) was attached to, so this reports the anchor's definition line, not the
     * alias usage site.
     */
    private fun Node.line(): Int = startMark.map { it.line + 1 }.orElse(1)

    private fun MarkedYamlEngineException.line(): Int = problemMark.map { it.line + 1 }.orElse(1)

    /**
     * The default JSON schema resolves only lowercase `null`/`true`/`false` (and empty) to non-string
     * tags, so the remaining YAML 1.2 core-schema null and bool literals are recognised here when
     * plain. The core schema itself is not used: it would flatten `<<` merge keys away before this
     * parser could report them. Quoted forms keep their string tag and stay strings.
     */
    private fun Node.isNullScalar(): Boolean = this is ScalarNode && (tag == Tag.NULL || (isPlain() && value in NULL_LITERALS))

    private fun ScalarNode.isStringScalar(): Boolean = tag == Tag.STR && !(isPlain() && (value in NULL_LITERALS || value in BOOL_LITERALS))

    private fun ScalarNode.isPlain(): Boolean = scalarStyle == ScalarStyle.PLAIN

    private const val SERVICES_KEY = "services"
    private const val ENVIRONMENT_KEY = "environment"
    private const val TOP_LEVEL_INCLUDE = "include"
}
