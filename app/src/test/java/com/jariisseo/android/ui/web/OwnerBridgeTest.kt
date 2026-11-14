package com.jariisseo.android.ui.web

import com.jariisseo.android.net.ownerViewerUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/** #17: 사장님 뷰어 주소, 웹 → 앱 브리지 전달, 앱 → 웹 호출 문자열 */
class OwnerBridgeTest {
    @Test
    fun viewerUrl_ownerModeOrSample_handlesTrailingSlash() {
        assertEquals("http://10.0.2.2:8080/s/k3x9q2mf?mode=owner", ownerViewerUrl("http://10.0.2.2:8080/", "k3x9q2mf", sample = false))
        assertEquals("http://10.0.2.2:8080/s/k3x9q2mf?mode=owner", ownerViewerUrl("http://10.0.2.2:8080", "k3x9q2mf", sample = false))
        assertEquals(
            "http://10.0.2.2:8080/viewer/index.html?mode=owner&sample=snapshot",
            ownerViewerUrl("http://10.0.2.2:8080/", "k3x9q2mf", sample = true),
        )
    }

    @Test
    fun bridge_forwardsThroughPost() {
        val calls = mutableListOf<String>()
        var posted = 0
        val bridge = OwnerBridge(
            onReady = { calls += "ready" },
            onTableTap = { id, occupied -> calls += "tap $id $occupied" },
            onSummary = { free, total -> calls += "summary $free/$total" },
            post = { posted++; it() },
        )

        bridge.onReady()
        bridge.onTableTap("t-3f9a", true)
        bridge.onSummary(8, 11)
        bridge.onExport("iVBOR...")

        assertEquals(listOf("ready", "tap t-3f9a true", "summary 8/11"), calls)
        assertEquals(3, posted)
    }

    @Test
    fun revertStatusJs_escapesTableId() {
        assertEquals("window.revertStatus(\"t-3f9a\")", revertStatusJs("t-3f9a"))
        assertEquals("window.revertStatus(\"a'b\\\"c\\\\\")", revertStatusJs("a'b\"c\\"))
    }

    @Test
    fun applyStatusJs_escapesTableId() {
        assertEquals("window.applyStatus(\"t-3f9a\", true)", applyStatusJs("t-3f9a", true))
        assertEquals("window.applyStatus(\"a'b\\\"c\\\\\", false)", applyStatusJs("a'b\"c\\", false))
    }
}
