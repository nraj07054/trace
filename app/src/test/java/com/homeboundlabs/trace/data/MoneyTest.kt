package com.homeboundlabs.trace.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test fun `indian grouping for rupees`() {
        assertEquals("0", Money.group(0, "₹"))
        assertEquals("999", Money.group(999, "₹"))
        assertEquals("1,000", Money.group(1000, "₹"))
        assertEquals("12,345", Money.group(12345, "₹"))
        assertEquals("1,23,456", Money.group(123456, "₹"))
        assertEquals("12,34,56,789", Money.group(123456789, "₹"))
        assertEquals("-1,23,456", Money.group(-123456, "₹"))
    }

    @Test fun `western grouping for other currencies`() {
        assertEquals("999", Money.group(999, "$"))
        assertEquals("123,456", Money.group(123456, "$"))
        assertEquals("1,234,567", Money.group(1234567, "€"))
    }

    @Test fun whole() {
        assertEquals("₹4,500", Money.whole(4500_00L, "₹"))
        assertEquals("₹481", Money.whole(481_25L, "₹"))
    }
}
