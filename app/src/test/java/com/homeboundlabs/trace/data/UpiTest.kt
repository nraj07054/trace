package com.homeboundlabs.trace.data

import com.homeboundlabs.trace.share.QrArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiTest {
    @Test fun acceptsRealLookingIds() {
        listOf("rahul@okaxis", "rahul.k-99@ybl", "9876543210@paytm", "a_b@oksbi").forEach { assertTrue(it, Upi.isValidId(it)) }
    }

    @Test fun rejectsBrokenIds() {
        listOf("", "rahul", "@okaxis", "rahul@", "ra hul@ybl", "rahul@@ybl", "rahul@1bank").forEach { assertFalse(it, Upi.isValidId(it)) }
    }

    @Test fun buildsAStandardPayLink() {
        assertEquals(
            "upi://pay?pa=rahul@okaxis&pn=Nikhil%20R&am=1500.00&cu=INR&tn=Movie%20tickets",
            Upi.payLink("rahul@okaxis", "Nikhil R", 1500_00, "Movie tickets"),
        )
    }

    @Test fun keepsPaiseAndSkipsEmptyParts() {
        assertEquals("upi://pay?pa=a@ybl&am=12.05&cu=INR", Upi.payLink("a@ybl", " ", 1205, ""))
    }

    @Test fun shortensLongNotes() {
        val link = Upi.payLink("a@ybl", "", null, "x".repeat(80))
        assertTrue(link.endsWith("&tn=" + "x".repeat(Upi.MAX_NOTE)))
    }

    @Test fun qrUsesHighErrorCorrectionAndStaysSmall() {
        // A typical request link fits in a small code, so each dot stays big enough to scan.
        val grid = QrArt.modules(Upi.payLink("rahul.kumar@okaxis", "Nikhil Raj", 1500_00, "Movie tickets"))
        assertTrue("size ${grid.size}", grid.size in 21..57)
    }
}
