package com.jariisseo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanGuideTest {
    private val table = MapItem("테이블", 1f, 1f, 5, confirmed = true)
    private val unconfirmedTable = MapItem("테이블", 2f, 2f, 1, confirmed = false)
    private val chair = MapItem("의자", 1f, 2f, 5, confirmed = true)

    private fun furnitureStep(vararg items: MapItem) = ArUiState(outlineClosed = true, mapItems = items.toList())

    @Test
    fun canFinish_needsOutlineAndConfirmedTable() {
        assertFalse(canFinish(ArUiState(outlineClosed = false, mapItems = listOf(table))))
        assertFalse(canFinish(furnitureStep()))
        assertFalse(canFinish(furnitureStep(unconfirmedTable, chair)))
        assertTrue(canFinish(furnitureStep(table)))
    }

    @Test
    fun needsSlowLap_after3MinutesWithoutTable() {
        assertFalse(needsSlowLap(SLOW_LAP_MS - 1000, furnitureStep()))
        assertTrue(needsSlowLap(SLOW_LAP_MS, furnitureStep(unconfirmedTable)))
        assertFalse(needsSlowLap(SLOW_LAP_MS, furnitureStep(table)))
        assertFalse(needsSlowLap(SLOW_LAP_MS, ArUiState(outlineClosed = false)))
    }

    @Test
    fun trackingGuide_onlyWhenNotTracking() {
        assertNull(trackingGuide(TrackingIssue.NONE))
        TrackingIssue.entries.filter { it != TrackingIssue.NONE }.forEach { assertNotNull(trackingGuide(it)) }
    }

    @Test
    fun formatElapsed_minutesAndSeconds() {
        assertEquals("0:00", formatElapsed(0))
        assertEquals("3:05", formatElapsed(185_000))
    }
}
