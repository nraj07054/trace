package com.homeboundlabs.trace.quickadd

import com.homeboundlabs.trace.data.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTest {
    private val cats = Category.defaults

    private fun check(said: String, rupees: Long, note: String, category: Category?) {
        val heard = Voice.understand(said, cats)!!
        assertEquals(said, rupees * 100, heard.amountMinor)
        assertEquals(said, note, heard.note)
        assertEquals(said, category?.id, heard.category?.id)
    }

    @Test fun english() {
        check("for vada pav I paid rupees 50", 50, "Vada pav", Category.Food)
        check("auto to station 80", 80, "Auto to station", Category.Travel)
    }

    @Test fun hindi() {
        check("35 रुपीस का आइसक्रीम खाया है", 35, "आइसक्रीम", Category.Food)
        check("मैंने 120 रुपये का ऑटो लिया", 120, "ऑटो", Category.Travel)
        check("200 का रिचार्ज किया", 200, "रिचार्ज", Category.Bills)
        check("50 रुपए की चाय पी", 50, "चाय", Category.Food)
    }

    @Test fun hinglish() {
        check("35 rupees ka ice cream khaya hai", 35, "Ice cream", Category.Food)
        check("maine 60 ka samosa liya", 60, "Samosa", Category.Food)
    }
}
