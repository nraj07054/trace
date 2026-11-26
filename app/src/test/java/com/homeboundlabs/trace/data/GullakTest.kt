package com.homeboundlabs.trace.data

import com.homeboundlabs.trace.ui.goals.waterLevelPct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GullakTest {
    @Test fun halfFullSitsMidBelly() {
        // Belly runs from 2% to 78% of the box height; half its volume is at its middle (40%).
        assertTrue(waterLevelPct(0.5f) in 39..41)
    }

    @Test fun sixtyTwoPercentIsJustPastTheMiddle() {
        val l = waterLevelPct(0.62f)
        assertTrue("level $l", l in 44..50)
    }

    @Test fun endsAreEmptyAndFull() {
        assertEquals(0, waterLevelPct(0f))
        assertEquals(100, waterLevelPct(1f))
        assertTrue(waterLevelPct(0.1f) < waterLevelPct(0.38f))
    }
}
