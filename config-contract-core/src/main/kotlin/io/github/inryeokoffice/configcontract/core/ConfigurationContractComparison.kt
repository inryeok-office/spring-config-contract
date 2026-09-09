package io.github.inryeokoffice.configcontract.core

/** Compares framework-neutral requirements with deployment-provided configuration keys. */
object ConfigurationContractComparison {
    /**
     * Returns findings ordered by configuration key and then finding kind.
     *
     * A required requirement is missing only when it has no provided key and no
     * known default. Optional and default-backed requirements therefore produce
     * no missing finding. A provided key is unused only when no requirement
     * recognizes it. Duplicate keys in either input are rejected because they
     * make the contract ambiguous; callers must normalize them before compare.
     */
    @JvmStatic
    fun compare(
        requirements: Iterable<ConfigurationRequirement>,
        provided: Iterable<ProvidedConfiguration>,
    ): List<ContractFinding> {
        val requirementsByKey = requirements.toUniqueMap("requirement") { it.key }
        val providedByKey = provided.toUniqueMap("provided configuration") { it.key }
        val findings =
            buildList {
                requirementsByKey.values
                    .filter { it.presence == ConfigurationRequirement.Presence.REQUIRED }
                    .filter { it.defaultValue == ConfigurationRequirement.DefaultValue.Absent }
                    .filterNot { providedByKey.containsKey(it.key) }
                    .forEach {
                        add(ContractFinding(ContractFinding.Kind.MISSING, it.key, it.source))
                    }

                providedByKey.values
                    .filterNot { requirementsByKey.containsKey(it.key) }
                    .forEach {
                        add(ContractFinding(ContractFinding.Kind.UNUSED, it.key, it.source))
                    }
            }

        return findings.sortedWith(compareBy<ContractFinding> { it.key.value }.thenBy { it.kind.ordinal })
    }

    private fun <T> Iterable<T>.toUniqueMap(
        label: String,
        keyOf: (T) -> ConfigurationKey,
    ): Map<ConfigurationKey, T> {
        val result = LinkedHashMap<ConfigurationKey, T>()
        for (item in this) {
            val key = keyOf(item)
            require(result.put(key, item) == null) {
                "Duplicate $label for configuration key '$key'"
            }
        }
        return result
    }
}
