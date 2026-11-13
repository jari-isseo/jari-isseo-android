package com.jariisseo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FloorPolygonTest {

    private fun rect(x0: Float, z0: Float, x1: Float, z1: Float) =
        listOf(x0 to z0, x1 to z0, x1 to z1, x0 to z1)

    @Test
    fun `사각형 1개는 꼭짓점 4개`() {
        val poly = FloorPolygon.build(listOf(rect(0f, 0f, 3f, 2f)))
        assertEquals(4, poly.size)
        assertEquals(6f, FloorPolygon.area(poly), 0.05f)
        assertSimple(poly)
    }

    @Test
    fun `ㄱ자는 빈 모서리를 바닥에 넣지 않는다`() {
        // 4x2 가로 조각 + 2x4 세로 조각 (겹침 2x2) → ㄱ자, 면적 12
        val poly = FloorPolygon.build(listOf(rect(0f, 0f, 4f, 2f), rect(0f, 0f, 2f, 4f)))
        assertEquals(6, poly.size)
        assertEquals(12f, FloorPolygon.area(poly), 0.1f)
        assertTrue(FloorPolygon.contains(poly, 1f, 1f))
        assertTrue(FloorPolygon.contains(poly, 3.5f, 1f))
        assertTrue(FloorPolygon.contains(poly, 1f, 3.5f))
        assertFalse(FloorPolygon.contains(poly, 3f, 3f))   // 빈 모서리
        assertSimple(poly)
    }

    @Test
    fun `10cm 틈으로 끊긴 조각은 하나로 이어진다`() {
        val poly = FloorPolygon.build(listOf(rect(0f, 0f, 2f, 2f), rect(2.1f, 0f, 4f, 2f)))
        assertTrue(FloorPolygon.contains(poly, 1f, 1f))
        assertTrue(FloorPolygon.contains(poly, 3f, 1f))
        assertSimple(poly)
    }

    @Test
    fun `책상 줄 폭(0_8m)으로 끊긴 통로는 하나로 이어진다`() {
        val poly = FloorPolygon.build(listOf(rect(0f, 0f, 4f, 1f), rect(0f, 1.8f, 4f, 2.8f)))
        assertTrue(FloorPolygon.contains(poly, 2f, 0.5f))
        assertTrue(FloorPolygon.contains(poly, 2f, 2.3f))
        assertSimple(poly)
    }

    @Test
    fun `멀리 떨어진 작은 조각은 버리고 큰 조각만 남긴다`() {
        val poly = FloorPolygon.build(listOf(rect(0f, 0f, 2f, 2f), rect(3.5f, 0f, 4.5f, 1f)))
        assertTrue(FloorPolygon.contains(poly, 1f, 1f))
        assertFalse(FloorPolygon.contains(poly, 4f, 0.5f))
        assertSimple(poly)
    }

    @Test
    fun `한 번 본 바닥은 다음 계산에서 빠져도 남는다`() {
        val floor = FloorPolygon()
        floor.add(listOf(rect(0f, 0f, 2f, 2f)))
        floor.add(listOf(rect(1.5f, 0f, 4f, 2f)))   // 두 번째엔 처음 조각이 안 보임
        val poly = floor.polygon()
        assertEquals(8f, FloorPolygon.area(poly), 0.1f)
        assertTrue(FloorPolygon.contains(poly, 0.5f, 1f))

        floor.clear()
        assertTrue(floor.polygon().isEmpty())
    }

    @Test
    fun `기울어진 바닥도 자기 교차 없이 단순화된다`() {
        // 45도 돌린 정사각형 (중심 (2, 2), 꼭짓점까지 1.5m) → 면적 4.5
        val diamond = (0 until 4).map { i ->
            val a = Math.PI / 2 * i
            (2f + 1.5f * cos(a).toFloat()) to (2f + 1.5f * sin(a).toFloat())
        }
        val poly = FloorPolygon.build(listOf(diamond))
        assertEquals(4.5f, FloorPolygon.area(poly), 0.45f)
        assertTrue(poly.size < 20)   // 계단 모양이 단순화됨
        assertSimple(poly)
    }

    @Test
    fun `조각이 없으면 빈 다각형`() {
        assertTrue(FloorPolygon.build(emptyList()).isEmpty())
    }

    /** 이웃하지 않은 두 변이 교차하지 않음 (API 정의서: 자기 교차 금지) */
    private fun assertSimple(poly: List<Pair<Float, Float>>) {
        assertTrue("꼭짓점 3개 이상", poly.size >= 3)
        val n = poly.size
        for (i in 0 until n) for (j in i + 1 until n) {
            if (j == i + 1 || (i == 0 && j == n - 1)) continue
            assertFalse("변 $i 와 $j 교차", intersects(poly[i], poly[(i + 1) % n], poly[j], poly[(j + 1) % n]))
        }
    }

    private fun intersects(a: Pair<Float, Float>, b: Pair<Float, Float>, c: Pair<Float, Float>, d: Pair<Float, Float>): Boolean {
        fun cross(o: Pair<Float, Float>, p: Pair<Float, Float>, q: Pair<Float, Float>) =
            (p.first - o.first) * (q.second - o.second) - (p.second - o.second) * (q.first - o.first)
        val d1 = cross(c, d, a); val d2 = cross(c, d, b)
        val d3 = cross(a, b, c); val d4 = cross(a, b, d)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }
}
