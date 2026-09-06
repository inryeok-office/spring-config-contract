package io.github.inryeokoffice.configcontract.core

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CorePlaceholderTest {
    @Test
    fun `core module loads without framework dependencies`() {
        assertNotNull(CorePlaceholder)
    }
}
