package com.jariisseo.android

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 바닥 위 벽 선분 (from (x, z) → to (x, z)), 단위 m */
typealias WallLine = Pair<Pair<Float, Float>, Pair<Float, Float>>

/**
 * #5: 수직 평면(벽 후보)마다 마지막 선분 1개만 기억하고, 벽 목록을 낼 때마다 처음부터 다시 묶음.
 * - 같은 평면을 매초 다시 합치지 않으므로 방향·위치가 조금씩 틀어지며 번지지 않음
 * - 묶은 벽의 직선은 평균 내지 않고 가장 긴 조각의 직선을 그대로 씀
 * - 나란히 겹친 벽(창틀·커튼·벽면)은 방 중심에서 가장 먼 바깥쪽 하나만
 * - 잠깐만 잡힌 평면(지나가는 사람·반사면)과, 양쪽에 바닥이 있는 방 한가운데 면은 벽이 아님
 * ARCore에 의존하지 않아 단위 테스트 가능 (key = 평면 등 아무 식별자)
 *
 * @param minSeen 벽으로 쓰려면 이 횟수 이상 put 돼야 함 (1초마다 put → 기본 3초)
 */
class WallSegments(private val minSeen: Int = MIN_SEEN) {
    private val byKey = LinkedHashMap<Any, WallLine>()
    private val seen = HashMap<Any, Int>()

    /** 이 평면의 최신 선분 (같은 key면 교체, 잡힌 횟수 +1) */
    fun put(key: Any, line: WallLine) {
        byKey[key] = line
        seen[key] = (seen[key] ?: 0) + 1
    }

    /** 다른 평면에 합쳐졌거나 더 이상 벽 후보가 아닌 평면 */
    fun remove(key: Any) {
        byKey.remove(key)
        seen.remove(key)
    }

    fun clear() {
        byKey.clear()
        seen.clear()
    }

    /**
     * 벽 목록: 충분히 잡힌 평면만 → 같은 벽 조각 묶기 → 최소 길이 → 양쪽에 바닥 있는 면 제외
     * → 겹친 평행 벽 중 바깥쪽 → 모서리 맞물리기. 벽이 없으면 빈 리스트
     * @param center 방 중심 (바닥 외곽선 중심). 없으면 겹친 벽 중 가장 긴 것을 남김
     * @param floor 바닥 외곽선. 없으면 양쪽 바닥 검사를 하지 않음
     */
    fun walls(center: Pair<Float, Float>? = null, floor: List<Pair<Float, Float>> = emptyList()): List<WallLine> {
        val raw = byKey.filterKeys { (seen[it] ?: 0) >= minSeen }.values.filter { length(it) > 1e-3f }
        // 주 방향(서로 수직인 두 축)에 가까운 조각은 반듯하게 돌린 뒤 묶음 → 위치 추정 오차로 조금 틀어진 벽 정리
        val axis = mainAxis(raw)
        val lines = if (axis == null) raw else raw.map { snapToAxis(it, axis) }
        val merged = groups(lines) { a, b -> sameWall(a, b) }.map { fit(it) }
            .filter { length(it) >= MIN_LENGTH_M }
            // 주 방향에서 벗어난 짧은 조각은 모서리 근처 노이즈로 봄 (긴 사선 벽은 실제일 수 있어 유지)
            .filterNot { axis != null && abs(offAxisDeg(it, axis)) > SNAP_DEG && length(it) < OFF_AXIS_MIN_M }
            .filterNot { floorOnBothSides(it, floor) }
        return joinCorners(outermost(merged, center))
    }

    companion object {
        const val SNAP_DEG = 15f            // 주 방향에서 이 안이면 반듯하게 맞춤
        const val OFF_AXIS_MIN_M = 2.0f     // 주 방향에서 벗어난 벽은 이 길이 이상만 (실제 사선 벽)
        private const val AXIS_MIN_STRENGTH = 0.3f   // 방향들이 이만큼은 모여 있어야 주 방향으로 인정

        /**
         * 길이 가중 주 방향 (0~90°, 라디안). 직각 구조면 모든 벽이 이 방향이나 +90° 근처.
         * 각도를 4배 해서 평균 → 0°·90°·180°·270°가 같은 방향으로 취급됨.
         * 방향이 고르게 흩어져 있으면(곡선 벽 등) null
         */
        fun mainAxis(lines: List<WallLine>): Float? {
            var sx = 0.0
            var sz = 0.0
            var total = 0.0
            for (l in lines) {
                val (dx, dz) = dir(l)
                val a4 = 4 * kotlin.math.atan2(dz.toDouble(), dx.toDouble())
                val w = length(l).toDouble()
                sx += w * kotlin.math.cos(a4)
                sz += w * kotlin.math.sin(a4)
                total += w
            }
            if (total <= 0.0 || sqrt(sx * sx + sz * sz) < AXIS_MIN_STRENGTH * total) return null
            return (kotlin.math.atan2(sz, sx) / 4).toFloat()
        }

        /** 주 방향 축과의 각도 차이 (-45° ~ 45°) */
        fun offAxisDeg(line: WallLine, axis: Float): Float {
            val (dx, dz) = dir(line)
            val deg = Math.toDegrees(kotlin.math.atan2(dz.toDouble(), dx.toDouble()) - axis).toFloat()
            return ((deg % 90f) + 135f) % 90f - 45f
        }

        /** 주 방향에서 SNAP_DEG 안이면 가운데를 중심으로 돌려 축에 맞춤 */
        private fun snapToAxis(line: WallLine, axis: Float): WallLine {
            val off = offAxisDeg(line, axis)
            if (abs(off) > SNAP_DEG) return line
            val rad = Math.toRadians(-off.toDouble())
            val c = kotlin.math.cos(rad).toFloat()
            val s = kotlin.math.sin(rad).toFloat()
            val (mx, mz) = mid(line)
            fun rot(p: Pair<Float, Float>): Pair<Float, Float> {
                val x = p.first - mx
                val z = p.second - mz
                return (mx + x * c - z * s) to (mz + x * s + z * c)
            }
            return rot(line.first) to rot(line.second)
        }

        const val MIN_SEEN = 3            // 3초 이상 잡힌 평면만 벽
        const val SIDE_CHECK_M = 0.5f     // 벽에서 양옆으로 이만큼 떨어진 곳에 바닥이 있는지 봄

        /**
         * 진짜 벽은 한쪽(방 안쪽)에만 바닥이 있음. 벽을 따라 1/4·1/2·3/4 지점에서 양옆을 봐서
         * 과반이 양쪽 모두 바닥 안이면 방 한가운데 면 → 벽 아님. 바닥을 못 잡은 곳은 판단하지 않음(벽 유지)
         */
        fun floorOnBothSides(wall: WallLine, floor: List<Pair<Float, Float>>): Boolean {
            if (floor.size < 3) return false
            val (dx, dz) = dir(wall)
            val nx = -dz * SIDE_CHECK_M
            val nz = dx * SIDE_CHECK_M
            val both = listOf(0.25f, 0.5f, 0.75f).count { f ->
                val (px, pz) = pointAt(wall, length(wall) * f)
                FloorPolygon.contains(floor, px + nx, pz + nz) && FloorPolygon.contains(floor, px - nx, pz - nz)
            }
            return both >= 2
        }

        const val MAX_ANGLE_DEG = 10f    // 이보다 덜 꺾였으면 평행
        const val MAX_OFFSET_M = 0.3f    // 같은 벽으로 볼 수직 거리
        const val MAX_GAP_M = 0.5f       // 같은 직선 위 끊긴 틈
        const val MIN_LENGTH_M = 1.0f    // 출력할 최소 벽 길이
        const val LAYER_M = 1.0f         // 이 안에서 나란히 겹친 벽은 바깥쪽 하나만 (창틀·커튼·벽면)
        const val JOIN_M = 1.0f          // 모서리(두 벽 직선의 교점)가 끝점에서 이 안이면 맞물림
        const val CONNECTED_M = 0.05f    // 끝점이 교점에서 이 안이면 이미 붙어 있는 것으로 봄

        /** 짝 조건으로 이어진 선분 묶음 (union-find) */
        private fun groups(lines: List<WallLine>, linked: (WallLine, WallLine) -> Boolean): List<List<WallLine>> {
            val parent = IntArray(lines.size) { it }
            fun find(i: Int): Int = if (parent[i] == i) i else find(parent[i]).also { parent[i] = it }
            for (i in lines.indices) for (j in i + 1 until lines.size) {
                if (linked(lines[i], lines[j])) parent[find(i)] = find(j)
            }
            return lines.indices.groupBy { find(it) }.values.map { idx -> idx.map { lines[it] } }
        }

        /** 같은 벽의 조각: 평행 + 수직 거리 가까움 + 같은 직선 위 틈이 작음 */
        private fun sameWall(a: WallLine, b: WallLine): Boolean {
            if (!parallel(a, b)) return false
            val (long, short) = if (length(a) >= length(b)) a to b else b to a
            if (offset(long, mid(short)) > MAX_OFFSET_M) return false
            return gapAlong(long, short) <= MAX_GAP_M
        }

        /** 묶음 → 가장 긴 조각의 직선 위에 모든 끝점을 투영해 양 끝을 정함 */
        private fun fit(group: List<WallLine>): WallLine {
            val base = group.maxBy { length(it) }
            val ts = group.flatMap { listOf(it.first, it.second) }.map { project(base, it) }
            return pointAt(base, ts.min()) to pointAt(base, ts.max())
        }

        /** 나란히(LAYER_M 안) 겹친 벽 중 방 중심에서 가장 먼 것만 남김 */
        private fun outermost(walls: List<WallLine>, center: Pair<Float, Float>?): List<WallLine> {
            // 중심에서 벽 직선까지 수직 거리로 순위 (중간점 거리는 벽 방향 위치까지 섞여 안쪽 조각이 앞설 수 있음)
            val ordered = if (center != null) walls.sortedByDescending { offset(it, center) } else walls.sortedByDescending { length(it) }
            val kept = mutableListOf<WallLine>()
            for (w in ordered) {
                val hidden = kept.any { k -> parallel(k, w) && offset(k, mid(w)) <= LAYER_M && gapAlong(k, w) < 0f }
                if (!hidden) kept += w
            }
            return kept
        }

        /**
         * 꺾여 만나는 두 벽의 끝점을 두 직선의 교점으로 옮김 (모자라면 늘리고, 삐져나오면 자름).
         * 곡선 벽은 꺾인 선분들이 이어진 모양이 됨. 교점이 한쪽 벽 가운데면(T자) 다른 쪽만 맞춤.
         * 교점이 끝점에서 JOIN_M보다 멀면(문·열린 공간일 수 있음) 그대로 둠
         */
        fun joinCorners(lines: List<WallLine>): List<WallLine> {
            val ends = lines.map { (a, b) -> arrayOf(a, b) }
            val snapped = lines.map { booleanArrayOf(false, false) }
            data class Join(val i: Int, val j: Int, val p: Pair<Float, Float>, val ei: Int?, val ej: Int?, val cost: Float)

            // 교점 p에 대해 이 벽은: (옮길 끝점 번호, 거리) / (null, 0) = 교점이 벽 가운데(T자) / 함수 결과 null = 맞물리지 않음
            fun endFor(k: Int, p: Pair<Float, Float>): Pair<Int?, Float>? {
                val (a, b) = lines[k]
                val da = dist(a, p)
                val db = dist(b, p)
                val near = if (da <= db) 0 else 1
                val d = min(da, db)
                if (d <= JOIN_M) return near to d
                val len = dist(a, b)
                val t = ((p.first - a.first) * (b.first - a.first) + (p.second - a.second) * (b.second - a.second)) / (len * len)
                return if (t in 0f..1f) null to 0f else null   // 가운데(T자)면 이 벽은 그대로
            }

            // 교점이 이 벽의 끝이 아닌 안쪽에 있는지
            fun interior(k: Int, p: Pair<Float, Float>): Boolean {
                val (a, b) = lines[k]
                val len = dist(a, b)
                val t = ((p.first - a.first) * (b.first - a.first) + (p.second - a.second) * (b.second - a.second)) / (len * len)
                return t > CONNECTED_M / len && t < 1f - CONNECTED_M / len
            }

            val joins = mutableListOf<Join>()
            for (i in lines.indices) for (j in i + 1 until lines.size) {
                val p = intersect(lines[i], lines[j]) ?: continue
                var ei = endFor(i, p) ?: continue
                var ej = endFor(j, p) ?: continue
                // 한쪽 벽 끝이 이미 다른 벽 안쪽에 붙어 있으면(T자) 관통하는 벽은 자르지 않음
                if (ei.first != null && ei.second < CONNECTED_M && interior(j, p)) ej = null to 0f
                if (ej.first != null && ej.second < CONNECTED_M && interior(i, p)) ei = null to 0f
                if (ei.first == null && ej.first == null) continue   // 둘 다 가운데 = 이미 교차
                joins += Join(i, j, p, ei.first, ej.first, ei.second + ej.second)
            }
            // 가까운 모서리부터, 끝점 하나는 한 번만 옮김
            for (jn in joins.sortedBy { it.cost }) {
                if (jn.ei != null && snapped[jn.i][jn.ei]) continue
                if (jn.ej != null && snapped[jn.j][jn.ej]) continue
                jn.ei?.let { ends[jn.i][it] = jn.p; snapped[jn.i][it] = true }
                jn.ej?.let { ends[jn.j][it] = jn.p; snapped[jn.j][it] = true }
            }
            return ends.map { it[0] to it[1] }
        }

        /** 두 선분을 무한 직선으로 늘렸을 때의 교점 (평행에 가까우면 null) */
        private fun intersect(l1: WallLine, l2: WallLine): Pair<Float, Float>? {
            val (a, b) = l1
            val (c, d) = l2
            val r = (b.first - a.first) to (b.second - a.second)
            val s = (d.first - c.first) to (d.second - c.second)
            val denom = r.first * s.second - r.second * s.first
            val sinAngle = abs(denom) / (dist(a, b) * dist(c, d))
            if (sinAngle <= sin(MAX_ANGLE_DEG)) return null
            val t = ((c.first - a.first) * s.second - (c.second - a.second) * s.first) / denom
            return (a.first + r.first * t) to (a.second + r.second * t)
        }

        // ── 선분 계산 도우미 ──
        private fun length(l: WallLine) = dist(l.first, l.second)
        private fun mid(l: WallLine) = ((l.first.first + l.second.first) / 2f) to ((l.first.second + l.second.second) / 2f)

        /** 단위 방향 */
        private fun dir(l: WallLine): Pair<Float, Float> {
            val len = length(l)
            return ((l.second.first - l.first.first) / len) to ((l.second.second - l.first.second) / len)
        }

        private fun parallel(a: WallLine, b: WallLine): Boolean {
            val (ax, az) = dir(a)
            val (bx, bz) = dir(b)
            return abs(ax * bz - az * bx) <= sin(MAX_ANGLE_DEG)
        }

        /** l의 시작점 기준, 방향으로 투영한 거리 */
        private fun project(l: WallLine, p: Pair<Float, Float>): Float {
            val (dx, dz) = dir(l)
            return (p.first - l.first.first) * dx + (p.second - l.first.second) * dz
        }

        private fun pointAt(l: WallLine, t: Float): Pair<Float, Float> {
            val (dx, dz) = dir(l)
            return (l.first.first + dx * t) to (l.first.second + dz * t)
        }

        /** 점에서 l 직선까지 수직 거리 */
        private fun offset(l: WallLine, p: Pair<Float, Float>): Float {
            val (dx, dz) = dir(l)
            return abs((p.first - l.first.first) * dz - (p.second - l.first.second) * dx)
        }

        /** long 직선 위에서 short가 차지하는 구간과 long 구간 사이 틈 (음수면 겹침) */
        private fun gapAlong(long: WallLine, short: WallLine): Float {
            val s0 = project(long, short.first)
            val s1 = project(long, short.second)
            return max(min(s0, s1), 0f) - min(max(s0, s1), length(long))
        }

        private fun dist(a: Pair<Float, Float>, b: Pair<Float, Float>) =
            sqrt((a.first - b.first) * (a.first - b.first) + (a.second - b.second) * (a.second - b.second))

        private fun sin(deg: Float) = kotlin.math.sin(Math.toRadians(deg.toDouble())).toFloat()
    }
}
