package com.jariisseo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class WallSegmentsTest {

    private fun line(x0: Float, z0: Float, x1: Float, z1: Float): WallLine = (x0 to z0) to (x1 to z1)

    private fun WallLine.length() = hypot(second.first - first.first, second.second - first.second)

    /** 선분마다 다른 평면(key)으로 넣고 벽 목록 */
    private fun walls(vararg lines: WallLine, center: Pair<Float, Float>? = null, floor: List<Pair<Float, Float>> = emptyList()) =
        WallSegments(minSeen = 1).apply { lines.forEachIndexed { i, l -> put(i, l) } }.walls(center, floor)

    @Test
    fun `같은 벽의 겹치는 두 조각은 하나로 합친다`() {
        val result = walls(line(0f, 0f, 2f, 0f), line(1.5f, 0.05f, 4f, 0.05f))
        assertEquals(1, result.size)
        assertEquals(4f, result[0].length(), 0.05f)
    }

    @Test
    fun `같은 직선의 짧은 틈은 잇고 긴 틈은 나눈다`() {
        assertEquals(1, walls(line(0f, 0f, 2f, 0f), line(2.4f, 0f, 4f, 0f)).size)
        assertEquals(2, walls(line(0f, 0f, 2f, 0f), line(3.5f, 0f, 5f, 0f)).size)
    }

    @Test
    fun `직각인 두 벽은 따로 남는다`() {
        assertEquals(2, walls(line(0f, 0f, 3f, 0f), line(0f, 0f, 0f, 3f)).size)
    }

    @Test
    fun `통로 양쪽처럼 1m 넘게 떨어진 평행 벽은 따로 남는다`() {
        assertEquals(2, walls(line(0f, 0f, 3f, 0f), line(0f, 1.5f, 3f, 1.5f)).size)
    }

    @Test
    fun `창틀·커튼·벽면처럼 나란히 겹친 벽은 방 중심에서 먼 바깥쪽만 남긴다`() {
        val result = walls(
            line(0f, 0f, 3f, 0f), line(0f, 0.4f, 3f, 0.4f), line(0.5f, 0.8f, 2.5f, 0.8f),
            center = 1.5f to 3f,
        )
        assertEquals(1, result.size)
        assertEquals(0f, result[0].first.second, 0.01f)   // z = 0 (중심에서 가장 먼 벽)
    }

    @Test
    fun `같은 평면을 여러 번 넣어도 마지막 선분만 쓰고 번지지 않는다`() {
        val w = WallSegments(minSeen = 1)
        val a = Math.toRadians(8.0)
        repeat(50) { i ->
            // 매번 ±8도, ±0.2m 흔들리는 같은 평면
            val s = if (i % 2 == 0) 1f else -1f
            w.put("plane", line(0f, 0.2f * s, (3 * cos(a)).toFloat(), 0.2f * s + (3 * sin(a) * s).toFloat()))
        }
        w.put("plane", line(0f, 0f, 3f, 0f))
        val result = w.walls()
        assertEquals(1, result.size)
        assertEquals(line(0f, 0f, 3f, 0f), result[0])
    }

    private fun angled(x: Float, z: Float, deg: Double, len: Double): WallLine {
        val a = Math.toRadians(deg)
        return line(x, z, x + (len * cos(a)).toFloat(), z + (len * sin(a)).toFloat())
    }

    @Test
    fun `주 방향에서 조금 틀어진 벽은 반듯하게 맞춘다`() {
        // 0°·94°·176° 벽 → 서로 정확히 평행·수직
        val result = walls(angled(0f, 0f, 0.0, 6.0), angled(0f, 0f, 94.0, 4.0), angled(0f, 4f, 176.0, 3.0))
        assertEquals(3, result.size)
        val axis = WallSegments.mainAxis(result)!!
        result.forEach { assertEquals(0f, WallSegments.offAxisDeg(it, axis), 0.01f) }
    }

    @Test
    fun `주 방향에서 벗어난 짧은 조각은 빼고 긴 사선 벽은 남긴다`() {
        val result = walls(
            angled(0f, 0f, 0.0, 6.0), angled(6f, 0f, 90.0, 5.0), angled(0f, 5f, 0.0, 6.0),
            angled(2f, 2f, 45.0, 1.5),    // 짧은 사선 조각 → 뺌
            angled(10f, 0f, 45.0, 3.0),   // 긴 사선 벽 → 남김
        )
        assertEquals(4, result.size)
        assertTrue(result.none { it.length() < 2f && abs(WallSegments.offAxisDeg(it, WallSegments.mainAxis(result)!!)) > 1f })
    }

    @Test
    fun `3번 미만으로 잡힌 평면(지나가는 사람 등)은 벽이 아니다`() {
        val w = WallSegments()   // 기본 minSeen = 3
        w.put("wall", line(0f, 0f, 3f, 0f))
        w.put("person", line(0f, 2f, 0f, 4f))
        assertTrue(w.walls().isEmpty())
        w.put("wall", line(0f, 0f, 3f, 0f))
        w.put("wall", line(0f, 0f, 3f, 0f))
        assertEquals(listOf(line(0f, 0f, 3f, 0f)), w.walls())
    }

    @Test
    fun `양쪽에 바닥이 있는 방 한가운데 면은 벽이 아니다`() {
        val floor = listOf(0f to 0f, 6f to 0f, 6f to 6f, 0f to 6f)
        val result = walls(
            line(0f, 0f, 6f, 0f),       // 바닥 가장자리 = 진짜 벽
            line(2f, 3f, 4f, 3f),       // 방 한가운데
            center = 3f to 3f, floor = floor,
        )
        assertEquals(listOf(line(0f, 0f, 6f, 0f)), result)
    }

    @Test
    fun `바닥을 못 잡은 곳의 면은 판단하지 않고 남긴다`() {
        val floor = listOf(0f to 0f, 2f to 0f, 2f to 2f, 0f to 2f)
        assertEquals(1, walls(line(4f, 3f, 6f, 3f), floor = floor).size)
    }

    @Test
    fun `다른 평면에 합쳐진 평면을 지우면 그 벽도 빠진다`() {
        val w = WallSegments(minSeen = 1)
        w.put(1, line(0f, 0f, 3f, 0f))
        w.put(2, line(0f, 2f, 0f, 5f))
        w.remove(2)
        assertEquals(listOf(line(0f, 0f, 3f, 0f)), w.walls())
    }

    @Test
    fun `방향이 5도 다른 같은 벽은 합친다`() {
        val a = Math.toRadians(5.0)
        val tilted = line(0f, 0f, (2 * cos(a)).toFloat(), (2 * sin(a)).toFloat())
        assertEquals(1, walls(line(0f, 0f, 2f, 0f), tilted).size)
    }

    @Test
    fun `반대 방향으로 그려진 같은 벽도 합친다`() {
        val result = walls(line(0f, 0f, 2f, 0f), line(3f, 0f, 1f, 0f))
        assertEquals(1, result.size)
        assertEquals(3f, result[0].length(), 0.05f)
    }

    @Test
    fun `짧은 조각(모니터 등)은 벽으로 내보내지 않는다`() {
        assertTrue(walls(line(0f, 0f, 0.6f, 0f)).isEmpty())
    }

    @Test
    fun `여러 평면으로 나눠 본 벽은 이어지고 지우면 비어 있다`() {
        val w = WallSegments(minSeen = 1)
        w.put(1, line(0f, 0f, 0.8f, 0f))
        assertTrue(w.walls().isEmpty())          // 아직 짧음
        w.put(2, line(0.6f, 0f, 1.6f, 0f))
        w.put(3, line(1.5f, 0f, 2.5f, 0f))
        assertEquals(1, w.walls().size)
        assertEquals(2.5f, w.walls()[0].length(), 0.05f)

        w.clear()
        assertTrue(w.walls().isEmpty())
    }

    private fun near(p: Pair<Float, Float>, x: Float, z: Float) = hypot(p.first - x, p.second - z) < 0.01f

    /** 두 벽이 (x, z) 한 점을 끝점으로 공유하는지 */
    private fun meetAt(a: WallLine, b: WallLine, x: Float, z: Float) =
        (near(a.first, x, z) || near(a.second, x, z)) && (near(b.first, x, z) || near(b.second, x, z))

    @Test
    fun `모서리 앞에서 끊긴 두 벽은 교점까지 늘려 맞물린다`() {
        val result = WallSegments.joinCorners(listOf(line(0f, 0f, 3f, 0f), line(3.5f, 0.5f, 3.5f, 3f)))
        assertTrue(meetAt(result[0], result[1], 3.5f, 0f))
    }

    @Test
    fun `모서리를 지나 삐져나온 벽은 교점에서 자른다`() {
        val result = WallSegments.joinCorners(listOf(line(0f, 0f, 3.4f, 0f), line(3f, -0.3f, 3f, 3f)))
        assertTrue(meetAt(result[0], result[1], 3f, 0f))
        assertEquals(3f, result[0].length(), 0.01f)
    }

    @Test
    fun `T자로 만나면 가운데 벽은 그대로 두고 다른 벽만 붙인다`() {
        val result = WallSegments.joinCorners(listOf(line(0f, 0f, 4f, 0f), line(2f, 0.5f, 2f, 3f)))
        assertEquals(line(0f, 0f, 4f, 0f), result[0])
        assertTrue(near(result[1].first, 2f, 0f))
    }

    @Test
    fun `이미 붙어 있는 T자는 관통하는 벽 끝 근처라도 자르지 않는다`() {
        val input = listOf(line(0f, 0f, 2f, 0f), line(0.5f, 0f, 0.5f, 3f))
        assertEquals(input, WallSegments.joinCorners(input))
    }

    @Test
    fun `긴 바깥 벽이 안쪽 짧은 조각에 가려지지 않는다`() {
        val floor = listOf(0f to 0f, 10f to 0f, 10f to 6f, 0f to 6f)
        val result = walls(line(0f, 0f, 10f, 0f), line(0f, 0.4f, 2f, 0.4f), center = 5f to 3f, floor = floor)
        assertEquals(listOf(line(0f, 0f, 10f, 0f)), result)
    }

    @Test
    fun `교점이 멀면(문·열린 공간) 잇지 않는다`() {
        val input = listOf(line(0f, 0f, 3f, 0f), line(5f, 1f, 5f, 4f))
        assertEquals(input, WallSegments.joinCorners(input))
    }

    @Test
    fun `곡선 벽은 꺾인 선분들이 끝점끼리 이어진다`() {
        // 30도씩 꺾이는 1.2m 조각 3개, 이음매마다 0.2m 틈
        val segs = mutableListOf<WallLine>()
        var x = 0f; var z = 0f
        for (k in 0 until 3) {
            val a = Math.toRadians(30.0 * k)
            val ex = x + (1.2 * cos(a)).toFloat(); val ez = z + (1.2 * sin(a)).toFloat()
            segs += line(x, z, ex, ez)
            x = ex + (0.2 * cos(a)).toFloat(); z = ez + (0.2 * sin(a)).toFloat()
        }
        val result = walls(*segs.toTypedArray())
        assertEquals(3, result.size)
        // 이음매 2곳: 끝점을 공유하는 벽 쌍이 2개
        val joints = (0 until 3).sumOf { i ->
            (i + 1 until 3).count { j ->
                listOf(result[i].first, result[i].second).any { p ->
                    listOf(result[j].first, result[j].second).any { q -> hypot(p.first - q.first, p.second - q.second) < 0.01f }
                }
            }
        }
        assertEquals(2, joints)
    }

    @Test
    fun `두 조각을 잇는 조각이 들어오면 셋이 하나가 된다`() {
        val w = WallSegments(minSeen = 1)
        w.put(1, line(0f, 0f, 1.5f, 0f))
        w.put(2, line(3f, 0f, 4.5f, 0f))
        assertEquals(2, w.walls().size)
        w.put(3, line(1.4f, 0f, 3.1f, 0f))
        assertEquals(1, w.walls().size)
        assertEquals(4.5f, w.walls()[0].length(), 0.05f)
    }
}
