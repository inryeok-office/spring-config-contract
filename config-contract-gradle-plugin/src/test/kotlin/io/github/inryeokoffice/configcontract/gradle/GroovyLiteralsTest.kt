package io.github.inryeokoffice.configcontract.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class GroovyLiteralsTest {
    @Test
    fun `renders a plain path unchanged inside single quotes`() {
        assertEquals("'/home/user/libs/spring-core.jar'", groovySingleQuoted("/home/user/libs/spring-core.jar"))
    }

    @Test
    fun `escapes an apostrophe in a path`() {
        assertEquals("""'/home/o\'brien/libs/a.jar'""", groovySingleQuoted("/home/o'brien/libs/a.jar"))
    }

    @Test
    fun `escapes backslashes in a Windows-style path`() {
        assertEquals("""'C:\\Users\\master\\libs\\a.jar'""", groovySingleQuoted("""C:\Users\master\libs\a.jar"""))
    }

    @Test
    fun `escapes the backslash before the apostrophe so neither escape is doubled`() {
        assertEquals("""'C:\\Users\\o\'brien\\a.jar'""", groovySingleQuoted("""C:\Users\o'brien\a.jar"""))
    }

    @Test
    fun `renders a path list with every element escaped`() {
        assertEquals(
            """['/tmp/o\'brien/a.jar', '/tmp/b.jar']""",
            groovyPathList(listOf(File("/tmp/o'brien/a.jar"), File("/tmp/b.jar"))),
        )
    }
}
