package com.jariisseo.android

import com.jariisseo.android.ui.TopViewTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopViewTransformTest {
    private val eps = 1e-3f

    @Test
    fun fit_putsAllPointsInsidePaddedArea() {
        val pts = listOf(0f to 0f, 8f to 0f, 8f to 6f, 0f to 6f)
        val t = TopViewTransform.fit(pts, 1000f, 600f, 20f)

        pts.forEach { (x, z) ->
            val p = t.toScreen(x, z)
            assertTrue("$p", p.x >= 20f - eps && p.x <= 980f + eps && p.y >= 20f - eps && p.y <= 580f + eps)
        }
        // 세로가 더 빡빡함: (600 - 40) / 6
        assertEquals(560f / 6f, t.scale, eps)
    }

    @Test
    fun toStore_isInverseOfToScreen() {
        val t = TopViewTransform(57f, 13f, -4f)
        val p = t.toScreen(2.5f, 1.25f)
        val (x, z) = t.toStore(p.x, p.y)

        assertEquals(2.5f, x, eps)
        assertEquals(1.25f, z, eps)
    }

    @Test
    fun zoomed_keepsPointUnderFingerAndClamps() {
        val t = TopViewTransform(100f, 0f, 0f)
        val before = t.toStore(300f, 200f)
        val z = t.zoomed(2f, 300f, 200f, 50f, 400f)

        assertEquals(200f, z.scale, eps)
        val after = z.toStore(300f, 200f)
        assertEquals(before.first, after.first, eps)
        assertEquals(before.second, after.second, eps)
        assertEquals(400f, t.zoomed(10f, 0f, 0f, 50f, 400f).scale, eps)
    }
}
