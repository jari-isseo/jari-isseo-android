package com.jariisseo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
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
    fun `떨어진 더 큰 조각이 생겨도 그리던 영역에서 점프하지 않는다`() {
        val floor = FloorPolygon()
        floor.add(listOf(rect(0f, 0f, 2f, 2f)))
        floor.polygon()
        floor.add(listOf(rect(4f, 0f, 7f, 2f)))   // 2m 떨어진 더 큰 조각
        val poly = floor.polygon()
        assertTrue(FloorPolygon.contains(poly, 1f, 1f))
        assertFalse(FloorPolygon.contains(poly, 5.5f, 1f))

        // 처음부터 둘 다 보이면 큰 쪽
        val fresh = FloorPolygon.build(listOf(rect(0f, 0f, 2f, 2f), rect(4f, 0f, 7f, 2f)))
        assertTrue(FloorPolygon.contains(fresh, 5.5f, 1f))
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

    @Test
    fun `꼭짓점에서만 닿는 다각형도 단순하지 않다고 판정한다`() {
        val bowtie = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 0f, -1f to 1f, -1f to 0f)
        assertFalse(isSimple(bowtie))
        assertTrue(isSimple(rect(0f, 0f, 1f, 1f)))
    }

    /** 이웃하지 않은 두 변이 교차·접촉·겹침 없음 (API 정의서: 자기 교차 금지) */
    private fun assertSimple(poly: List<Pair<Float, Float>>) {
        assertTrue("꼭짓점 3개 이상", poly.size >= 3)
        assertTrue("자기 교차·접촉 없음: $poly", isSimple(poly))
    }

    private fun isSimple(poly: List<Pair<Float, Float>>): Boolean {
        val n = poly.size
        for (i in 0 until n) for (j in i + 1 until n) {
            if (j == i + 1 || (i == 0 && j == n - 1)) continue   // 이웃한 변은 끝점을 공유해도 됨
            if (touches(poly[i], poly[(i + 1) % n], poly[j], poly[(j + 1) % n])) return false
        }
        return true
    }

    /** 두 선분이 교차하거나 끝점·일직선으로 닿으면 true */
    private fun touches(a: Pair<Float, Float>, b: Pair<Float, Float>, c: Pair<Float, Float>, d: Pair<Float, Float>): Boolean {
        val eps = 1e-6f
        fun cross(o: Pair<Float, Float>, p: Pair<Float, Float>, q: Pair<Float, Float>) =
            (p.first - o.first) * (q.second - o.second) - (p.second - o.second) * (q.first - o.first)
        fun onSegment(p: Pair<Float, Float>, q: Pair<Float, Float>, r: Pair<Float, Float>) =
            r.first in minOf(p.first, q.first) - eps..maxOf(p.first, q.first) + eps &&
                r.second in minOf(p.second, q.second) - eps..maxOf(p.second, q.second) + eps
        val d1 = cross(c, d, a); val d2 = cross(c, d, b)
        val d3 = cross(a, b, c); val d4 = cross(a, b, d)
        if (((d1 > eps && d2 < -eps) || (d1 < -eps && d2 > eps)) && ((d3 > eps && d4 < -eps) || (d3 < -eps && d4 > eps))) return true
        return (abs(d1) <= eps && onSegment(c, d, a)) || (abs(d2) <= eps && onSegment(c, d, b)) ||
            (abs(d3) <= eps && onSegment(a, b, c)) || (abs(d4) <= eps && onSegment(a, b, d))
    }
}
