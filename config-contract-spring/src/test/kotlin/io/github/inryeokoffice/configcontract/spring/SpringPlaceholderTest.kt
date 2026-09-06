package io.github.inryeokoffice.configcontract.spring

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SpringPlaceholderTest {
    @Test
    fun `spring module scaffold is loadable`() {
        assertNotNull(SpringPlaceholder)
    }
}
