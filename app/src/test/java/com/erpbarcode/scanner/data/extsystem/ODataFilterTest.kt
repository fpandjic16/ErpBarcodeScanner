package com.erpbarcode.scanner.data.extsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class ODataFilterTest {

    @Test
    fun `plain value is quoted`() {
        assertEquals("'MP01'", odataLiteral("MP01"))
    }

    @Test
    fun `apostrophes are doubled`() {
        assertEquals("'O''BRIEN'", odataLiteral("O'BRIEN"))
        assertEquals("''''''", odataLiteral("''"))
    }
}
