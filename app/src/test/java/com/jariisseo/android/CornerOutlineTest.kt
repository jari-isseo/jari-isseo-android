package com.jariisseo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class CornerOutlineTest {
    private fun rotate(pts: List<Pair<Float, Float>>, deg: Double) = pts.map { (x, z) ->
        val r = Math.toRadians(deg)
        (x * cos(r) - z * sin(r)).toFloat() to (x * sin(r) + z * cos(r)).toFloat()
    }

    /** 법선을 deg만큼 돌린 벽 기록 (폰을 살짝 비스듬히 댄 경우) */
    private fun touch(x: Float, z: Float, nx: Float, nz: Float, deg: Double = 0.0): WallTouch {
        val r = Math.toRadians(deg)
        return WallTouch(x, z, (nx * cos(r) - nz * sin(r)).toFloat(), (nx * sin(r) + nz * cos(r)).toFloat())
    }

    private fun assertNear(expected: Pair<Float, Float>, pts: List<Pair<Float, Float>>, tol: Float) {
        val d = pts.minOf { hypot(it.first - expected.first, it.second - expected.second) }
        assertTrue("$expected 근처 꼭짓점 없음: $pts", d <= tol)
    }

    /** 모든 변이 서로 직각이거나 나란한지 (첫 변 기준) */
    private fun assertRectilinear(pts: List<Pair<Float, Float>>) {
        val angles = CornerOutline.edges(pts).map { (a, b) ->
            Math.toDegrees(atan2((b.second - a.second).toDouble(), (b.first - a.first).toDouble()))
        }
        val base = angles.first()
        angles.forEach { d ->
            val m = ((d - base) % 90.0 + 90.0) % 90.0
            assertTrue("각도 $d", m < 0.01 || m > 89.99)
        }
    }

    @Test
    fun fourWalls_cornersAreIntersections() {
        // 4×3m 방, 벽의 아무 곳에 댐 (모서리 근처 아님). 법선 2~3°, 위치 1~2cm 어긋남
        val walls = listOf(
            touch(1.5f, 0.01f, 0f, 1f, 2.0),
            touch(3.98f, 2.0f, -1f, 0f, -3.0),
            touch(2.5f, 3.02f, 0f, -1f, 2.0),
            touch(0.01f, 1.0f, 1f, 0f, 3.0),
        )
        val out = CornerOutline.fromWalls(walls)

        assertEquals(4, out.size)
        assertRectilinear(out)
        // 법선이 2~3° 어긋나면 외곽선 전체가 살짝 돌아감 → 위치 대신 변 길이로 확인 (issue 목표 10cm 이내)
        val sides = CornerOutline.edges(out).map { (a, b) -> hypot(b.first - a.first, b.second - a.second) }
        listOf(3f, 3f, 4f, 4f).zip(sides.sorted()).forEach { (expected, actual) -> assertEquals(expected, actual, 0.1f) }
    }

    @Test
    fun parallelNeighbours_joinedByPerpendicular() {
        // 짧은 단차 벽을 건너뛰고 나란한 두 벽(z=4, z=3.5)을 연달아 기록
        val walls = listOf(
            touch(1f, 0f, 0f, 1f),
            touch(6f, 1f, -1f, 0f),
            touch(5f, 4f, 0f, -1f),
            touch(1f, 3.5f, 0f, -1f),
            touch(0f, 2f, 1f, 0f),
        )
        val out = CornerOutline.fromWalls(walls)

        assertEquals(6, out.size)
        listOf(0f to 0f, 6f to 0f, 6f to 4f, 1f to 4f, 1f to 3.5f, 0f to 3.5f).forEach { assertNear(it, out, 1e-3f) }
    }

    @Test
    fun fewerThanThreeWalls_isEmpty() {
        assertTrue(CornerOutline.fromWalls(listOf(touch(0f, 0f, 0f, 1f), touch(4f, 0f, -1f, 0f))).isEmpty())
    }

    @Test
    fun segment_liesAlongWall() {
        val (a, b) = CornerOutline.segment(touch(2f, 0f, 0f, 1f))

        assertEquals(0f, a.second, 1e-5f)
        assertEquals(0f, b.second, 1e-5f)
        assertEquals(1f, abs(b.first - a.first), 1e-5f)
    }

    @Test
    fun square_noisyRotatedRectangle() {
        val pts = rotate(listOf(0.03f to -0.02f, 4.02f to 0.05f, 3.97f to 3.04f, -0.04f to 2.98f), 30.0)
        val out = CornerOutline.square(pts)

        assertRectilinear(out)
    }

    @Test
    fun square_keepsDiagonalWall() {
        val out = CornerOutline.square(listOf(0f to 0f, 4f to 0f, 4f to 2f, 3f to 3f, 0f to 3f))

        val (a, b) = out[2] to out[3]
        val deg = Math.toDegrees(atan2(abs(b.second - a.second).toDouble(), abs(b.first - a.first).toDouble()))
        assertEquals(45.0, deg, 1.0)
    }
}
