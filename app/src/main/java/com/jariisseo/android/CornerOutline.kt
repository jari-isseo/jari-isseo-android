package com.jariisseo.android

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** #38: 폰을 벽에 대고 기록한 1번. (x, z) = 벽 위 한 점, (nx, nz) = 벽 법선 (바닥 위, 길이 무관) */
data class WallTouch(val x: Float, val z: Float, val nx: Float, val nz: Float)

/**
 * #38: 벽에 대고 기록한 순서대로 이웃한 벽 선의 교점 → 방 외곽선.
 * 좌표는 바닥 위 (x, z) (m). ARCore에 의존하지 않아 단위 테스트 가능
 */
object CornerOutline {
    const val SQUARE_DEG = 10f      // 주 방향(0°/90°)에서 이 안의 변은 반듯하게
    const val PARALLEL_DEG = 20f    // 이웃한 두 벽이 이 안으로 나란하면 교점 대신 수선으로 이음
    const val NEAR_M = 0.3f         // #40: 외곽선 밖으로 이만큼까지는 가구로 인정 (인식 위치 오차)

    /**
     * 벽 3개 이상 → 외곽선 (직각 정리 포함). 모자라면 빈 목록.
     * 기록한 순서(방을 한 바퀴 돈 순서)를 먼저 쓰고, 순서가 섞였으면(마주 보는 벽을 연달아 기록 등) 꼬인 도형이 되므로
     * 벽 점들의 중심 기준 각도 순으로 다시 정렬한 결과와 비교해 꼬이지 않고 넓은 쪽을 고름
     */
    fun fromWalls(walls: List<WallTouch>): List<Pair<Float, Float>> {
        if (walls.size < 3) return emptyList()
        val cx = walls.sumOf { it.x.toDouble() } / walls.size
        val cz = walls.sumOf { it.z.toDouble() } / walls.size
        val byAngle = walls.sortedBy { atan2(it.z - cz, it.x - cx) }
        return listOf(build(walls), build(byAngle))
            .filterNot(::selfIntersects)
            .maxByOrNull { FloorPolygon.area(it) }
            ?: build(walls)
    }

    /** 주어진 순서대로 이웃한 벽 선의 교점 = 모서리 */
    private fun build(walls: List<WallTouch>): List<Pair<Float, Float>> {
        val corners = mutableListOf<Pair<Float, Float>>()
        for (i in walls.indices) {
            val a = walls[i]
            val b = walls[(i + 1) % walls.size]
            val p = intersect(a, b)
            if (p != null) {
                corners += p
            } else {
                // 나란한 벽(단차): 다음 벽 점을 이 벽 선에 내린 수선의 발 → 다음 벽 점
                corners += foot(a, b.x to b.z)
                corners += b.x to b.z
            }
        }
        return square(corners)
    }

    /** 다각형의 이웃하지 않은 두 변이 서로 교차하는지 (꼬인 도형) */
    private fun selfIntersects(poly: List<Pair<Float, Float>>): Boolean {
        val n = poly.size
        if (n < 4) return false
        fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>) =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        for (i in 0 until n) {
            val a1 = poly[i]
            val a2 = poly[(i + 1) % n]
            for (j in i + 2 until n) {
                if (i == 0 && j == n - 1) continue   // 첫 변과 마지막 변은 이웃
                val b1 = poly[j]
                val b2 = poly[(j + 1) % n]
                val d1 = cross(b1, b2, a1)
                val d2 = cross(b1, b2, a2)
                val d3 = cross(a1, a2, b1)
                val d4 = cross(a1, a2, b2)
                if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true
            }
        }
        return false
    }

    /** 벽 선 위 ±half 길이의 선분 (미니맵 표시용) */
    fun segment(w: WallTouch, half: Float = 0.5f): WallLine {
        val (dx, dz) = direction(w)
        return (w.x - dx * half to w.z - dz * half) to (w.x + dx * half to w.z + dz * half)
    }

    /**
     * 가장 긴 변을 x축으로 보고, 주 방향에서 SQUARE_DEG 안의 변은 x축·z축과 정확히 나란하게.
     * 꼭짓점은 양옆 변이 정한 선(가로변 → z, 세로변 → x)으로 옮겨서 직각 모서리가 됨. 사선 변은 그대로
     */
    fun square(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val n = points.size
        if (n < 3) return points
        val i0 = (0 until n).maxBy { dist(points[it], points[(it + 1) % n]) }
        val (a, b) = points[i0] to points[(i0 + 1) % n]
        val angle = atan2(b.second - a.second, b.first - a.first)
        val r = points.map { rotate(it, -angle) }   // 가장 긴 변이 x축과 나란한 좌표

        val horizontal = arrayOfNulls<Float>(n)     // 변 i(점 i → i+1)가 가로면 그 z
        val vertical = arrayOfNulls<Float>(n)       // 세로면 그 x
        for (i in 0 until n) {
            val (p, q) = r[i] to r[(i + 1) % n]
            val deg = Math.toDegrees(atan2(abs(q.second - p.second), abs(q.first - p.first)).toDouble())
            if (deg <= SQUARE_DEG) horizontal[i] = (p.second + q.second) / 2
            else if (deg >= 90 - SQUARE_DEG) vertical[i] = (p.first + q.first) / 2
        }
        return r.mapIndexed { i, p ->
            val sides = listOf((i - 1 + n) % n, i)   // 이 점에서 끝나는 변, 시작하는 변
            val z = sides.mapNotNull { horizontal[it] }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: p.second
            val x = sides.mapNotNull { vertical[it] }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: p.first
            rotate(x to z, angle)
        }
    }

    /** 외곽선 각 변 = 벽 */
    fun edges(points: List<Pair<Float, Float>>): List<WallLine> =
        if (points.size < 3) emptyList() else points.indices.map { points[it] to points[(it + 1) % points.size] }

    /** #40: 점이 외곽선 안이거나 변에서 margin 이내인지 (외곽선 밖 가구 거르기, 벽에 붙은 가구는 살림) */
    fun near(outline: List<Pair<Float, Float>>, p: Pair<Float, Float>, margin: Float = NEAR_M): Boolean {
        if (outline.size < 3) return false
        if (FloorPolygon.contains(outline, p.first, p.second)) return true
        return edges(outline).any { (a, b) -> segmentDist(a, b, p) <= margin }
    }

    /** 점 p와 선분 ab 사이 거리 */
    private fun segmentDist(a: Pair<Float, Float>, b: Pair<Float, Float>, p: Pair<Float, Float>): Float {
        val dx = b.first - a.first
        val dz = b.second - a.second
        val len2 = dx * dx + dz * dz
        val t = if (len2 == 0f) 0f else (((p.first - a.first) * dx + (p.second - a.second) * dz) / len2).coerceIn(0f, 1f)
        return dist(p, a.first + dx * t to a.second + dz * t)
    }

    /** 벽 방향 단위벡터 = 법선에 수직 */
    private fun direction(w: WallTouch): Pair<Float, Float> {
        val len = hypot(w.nx, w.nz)
        return -w.nz / len to w.nx / len
    }

    /** 두 벽 선의 교점. 거의 나란하면 null */
    private fun intersect(a: WallTouch, b: WallTouch): Pair<Float, Float>? {
        val (ax, az) = direction(a)
        val (bx, bz) = direction(b)
        val cross = ax * bz - az * bx   // = sin(두 벽 사이 각)
        if (abs(cross) < sin(Math.toRadians(PARALLEL_DEG.toDouble())).toFloat()) return null
        val t = ((b.x - a.x) * bz - (b.z - a.z) * bx) / cross
        return a.x + ax * t to a.z + az * t
    }

    /** 점 p를 벽 a 선에 내린 수선의 발 */
    private fun foot(a: WallTouch, p: Pair<Float, Float>): Pair<Float, Float> {
        val (dx, dz) = direction(a)
        val t = (p.first - a.x) * dx + (p.second - a.z) * dz
        return a.x + dx * t to a.z + dz * t
    }

    private fun dist(a: Pair<Float, Float>, b: Pair<Float, Float>) = hypot(a.first - b.first, a.second - b.second)

    private fun rotate(p: Pair<Float, Float>, angle: Float): Pair<Float, Float> {
        val c = cos(angle)
        val s = sin(angle)
        return (p.first * c - p.second * s) to (p.first * s + p.second * c)
    }
}
