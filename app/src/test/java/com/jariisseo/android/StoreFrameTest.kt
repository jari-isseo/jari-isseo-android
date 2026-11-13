package com.jariisseo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class StoreFrameTest {
    private val eps = 1e-3f

    /** (cx, cz)를 중심으로 deg만큼 돌린 w×d 직사각형 꼭짓점 */
    private fun rect(w: Float, d: Float, deg: Double, cx: Float = -2f, cz: Float = 5f): List<Pair<Float, Float>> {
        val r = Math.toRadians(deg)
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        return listOf(0f to 0f, w to 0f, w to d, 0f to d).map { (x, z) -> (cx + x * c - z * s) to (cz + x * s + z * c) }
    }

    private fun wallsOf(poly: List<Pair<Float, Float>>): List<WallLine> =
        poly.indices.map { poly[it] to poly[(it + 1) % poly.size] }

    @Test
    fun rotatedRoom_becomesAxisAlignedAndNonNegative() {
        val floor = rect(4f, 3f, 30.0)
        val walls = wallsOf(floor)
        val f = StoreFrame.of(floor, walls, emptyList())

        val nWalls = walls.map(f::wall)
        nWalls.forEach { (a, b) ->
            assertTrue(abs(b.first - a.first) < eps || abs(b.second - a.second) < eps)
        }
        val pts = floor.map(f::point)
        pts.forEach { (x, z) -> assertTrue(x >= -1e-4f && z >= -1e-4f) }
        assertEquals(0f, pts.minOf { it.first }, eps)
        assertEquals(0f, pts.minOf { it.second }, eps)
        assertEquals(4f, pts.maxOf { it.first }, eps)   // 가장 긴 벽(4m)이 x축
        assertEquals(3f, pts.maxOf { it.second }, eps)
    }

    @Test
    fun itemOutsideFloor_staysNonNegative() {
        val floor = rect(4f, 3f, 0.0, 0f, 0f)
        val f = StoreFrame.of(floor, wallsOf(floor), listOf(-0.3f to 1f))
        val (x, _) = f.point(-0.3f to 1f)
        assertEquals(0f, x, eps)
    }

    @Test
    fun noWalls_translateOnly() {
        val f = StoreFrame.of(listOf(-1f to -2f, 3f to -2f, 3f to 1f), emptyList(), emptyList())
        val (x, z) = f.point(3f to 1f)
        assertEquals(4f, x, eps)
        assertEquals(3f, z, eps)
    }

    @Test
    fun empty_isIdentity() {
        val f = StoreFrame.of(emptyList(), emptyList(), emptyList())
        assertEquals(1.5f to -2f, f.point(1.5f to -2f))
    }

    @Test
    fun direction_rotatesWithoutTranslation() {
        val floor = rect(4f, 3f, 90.0)   // 가장 긴 벽이 +z 방향 → -90° 회전
        val f = StoreFrame.of(floor, wallsOf(floor), emptyList())
        val (x, z) = f.direction(0f, 1f)
        assertEquals(1f, x, eps)
        assertEquals(0f, z, eps)
    }
}
