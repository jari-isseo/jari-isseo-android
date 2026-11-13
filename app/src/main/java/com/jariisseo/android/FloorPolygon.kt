package com.jariisseo.android

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/**
 * #4: 바닥 조각(ARCore 수평 평면)들의 합집합 외곽선 → 배치 JSON floor.polygon
 * - 볼록 껍질·bounding box는 ㄱ자 매장의 빈 모서리까지 채우므로 쓰지 않고, 격자에 칠한 뒤 외곽선을 따라감
 * - 한 번 바닥으로 본 칸은 계속 누적 (ARCore가 평면을 합치거나 버려도 외곽선이 줄거나 점프하지 않음)
 * - 좌표는 바닥 평면 (x, z), 단위 m. ARCore에 의존하지 않아 단위 테스트 가능
 */
class FloorPolygon {
    private val seen = HashSet<Long>()       // 바닥으로 본 칸 (격자 ix, iz)
    private var shown = emptySet<Long>()     // 직전에 외곽선으로 그린 영역 (점프 방지)

    /** 지금 보이는 바닥 조각들을 누적 */
    fun add(pieces: List<List<Pair<Float, Float>>>) {
        for (piece in pieces.filter { it.size >= 3 }) {
            val c0 = floor(piece.minOf { it.first } / CELL_M).toInt()
            val c1 = floor(piece.maxOf { it.first } / CELL_M).toInt()
            val r0 = floor(piece.minOf { it.second } / CELL_M).toInt()
            val r1 = floor(piece.maxOf { it.second } / CELL_M).toInt()
            for (r in r0..r1) for (c in c0..c1) {
                if (contains(piece, (c + 0.5f) * CELL_M, (r + 0.5f) * CELL_M)) seen += key(c, r)
            }
        }
    }

    fun clear() {
        seen.clear()
        shown = emptySet()
    }

    /** 누적한 바닥 → 하나의 단순 다각형 (본 바닥이 없으면 빈 리스트) */
    fun polygon(): List<Pair<Float, Float>> {
        if (seen.isEmpty()) return emptyList()
        val pad = CLOSE_CELLS + 2   // 닫기 연산이 격자 경계에 닿지 않게
        val minC = seen.minOf { col(it) } - pad
        val minR = seen.minOf { row(it) } - pad
        val grid = Grid(seen.maxOf { col(it) } - minC + 1 + pad, seen.maxOf { row(it) } - minR + 1 + pad)
        seen.forEach { grid[col(it) - minC, row(it) - minR] = true }

        // 닫기(팽창 → 침식): 책상 줄 등으로 끊긴 바닥(최대 2 × CLOSE_CELLS 칸)을 이음
        var closed = grid
        repeat(CLOSE_CELLS) { closed = closed.dilate() }
        repeat(CLOSE_CELLS) { closed = closed.erode() }
        // 영역 하나만 고름: 직전에 그린 영역과 겹치는 것 우선 (떨어진 더 큰 조각으로 점프하지 않게), 없으면 가장 큰 것
        val regions = closed.regions()
        val cellKey = { i: Int -> key(i % closed.cols + minC, i / closed.cols + minR) }
        val chosen = regions.maxByOrNull { r -> r.count { cellKey(it) in shown } }?.takeIf { r -> r.any { cellKey(it) in shown } }
            ?: regions.maxByOrNull { it.size }
            ?: return emptyList()
        shown = chosen.mapTo(HashSet()) { cellKey(it) }
        // 구멍·대각선 접점 정리 → 외곽선이 고리 1개가 되도록
        val region = closed.only(chosen).tidy()
        val outline = region.trace().map { (ix, iz) -> (minC + ix) * CELL_M to (minR + iz) * CELL_M }
        val straight = removeCollinear(outline)
        val simplified = simplifyClosed(straight, SIMPLIFY_M)
        return if (simplified.size >= 3) simplified else straight
    }

    companion object {
        const val CELL_M = 0.1f        // 격자 한 칸
        const val CLOSE_CELLS = 5      // 닫기 반경 0.5m → 최대 1m 틈까지 이음 (책상 줄 0.6~0.8m)
        const val SIMPLIFY_M = 0.1f    // 단순화 허용 오차

        /** 조각들을 한 번에 넣어 다각형으로 (테스트·일회성 계산용) */
        fun build(pieces: List<List<Pair<Float, Float>>>) = FloorPolygon().apply { add(pieces) }.polygon()

        /** 점이 다각형 안에 있는지 (광선 교차) */
        fun contains(poly: List<Pair<Float, Float>>, x: Float, z: Float): Boolean {
            var inside = false
            var j = poly.lastIndex
            for (i in poly.indices) {
                val (xi, zi) = poly[i]
                val (xj, zj) = poly[j]
                if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) inside = !inside
                j = i
            }
            return inside
        }

        /** 다각형 면적 (m²) */
        fun area(poly: List<Pair<Float, Float>>): Float {
            var sum = 0f
            for (i in poly.indices) {
                val (x1, z1) = poly[i]
                val (x2, z2) = poly[(i + 1) % poly.size]
                sum += x1 * z2 - x2 * z1
            }
            return abs(sum) / 2f
        }

        private fun key(c: Int, r: Int) = (c.toLong() shl 32) or (r.toLong() and 0xffffffffL)
        private fun col(k: Long) = (k shr 32).toInt()
        private fun row(k: Long) = k.toInt()

        /** 일직선 위의 중간 점 제거 (격자 외곽선은 같은 방향 칸마다 점이 있음) */
        private fun removeCollinear(pts: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
            if (pts.size < 4) return pts
            return pts.filterIndexed { i, p ->
                val a = pts[(i - 1 + pts.size) % pts.size]
                val b = pts[(i + 1) % pts.size]
                abs((p.first - a.first) * (b.second - p.second) - (p.second - a.second) * (b.first - p.first)) > 1e-6f
            }
        }

        /** 닫힌 다각형 Douglas-Peucker: 첫 점과 가장 먼 점에서 둘로 나눠 각각 단순화 */
        private fun simplifyClosed(pts: List<Pair<Float, Float>>, eps: Float): List<Pair<Float, Float>> {
            if (pts.size <= 4) return pts
            val far = pts.indices.maxBy { hypot(pts[it].first - pts[0].first, pts[it].second - pts[0].second) }
            val a = douglasPeucker(pts.subList(0, far + 1), eps)
            val b = douglasPeucker(pts.subList(far, pts.size) + pts[0], eps)
            return a.dropLast(1) + b.dropLast(1)
        }

        private fun douglasPeucker(pts: List<Pair<Float, Float>>, eps: Float): List<Pair<Float, Float>> {
            if (pts.size < 3) return pts
            val (sx, sz) = pts.first()
            val (ex, ez) = pts.last()
            val len = hypot(ex - sx, ez - sz)
            var maxD = -1f
            var idx = 0
            for (i in 1 until pts.lastIndex) {
                val (px, pz) = pts[i]
                val d = if (len < 1e-6f) hypot(px - sx, pz - sz) else abs((ex - sx) * (sz - pz) - (sx - px) * (ez - sz)) / len
                if (d > maxD) { maxD = d; idx = i }
            }
            if (maxD <= eps) return listOf(pts.first(), pts.last())
            return douglasPeucker(pts.subList(0, idx + 1), eps).dropLast(1) + douglasPeucker(pts.subList(idx, pts.size), eps)
        }
    }

    private class Grid(val cols: Int, val rows: Int, val cells: BooleanArray = BooleanArray(cols * rows)) {
        operator fun get(c: Int, r: Int) = c in 0 until cols && r in 0 until rows && cells[r * cols + c]
        operator fun set(c: Int, r: Int, v: Boolean) { cells[r * cols + c] = v }

        /** 3×3 이웃 중 하나라도(any) / 모두(all) 칠해져 있으면 칠함 */
        private fun morph(any: Boolean) = Grid(cols, rows, BooleanArray(cols * rows) { i ->
            val c = i % cols; val r = i / cols
            var hit = !any
            for (dr in -1..1) for (dc in -1..1) {
                val v = this[c + dc, r + dr]
                if (any && v) hit = true
                if (!any && !v) hit = false
            }
            hit
        })

        fun dilate() = morph(any = true)
        fun erode() = morph(any = false)

        /** 같은 값으로 상하좌우 이어진 칸 묶음 */
        private fun flood(startC: Int, startR: Int, seen: BooleanArray): List<Int> {
            val value = this[startC, startR]
            val out = mutableListOf<Int>()
            val queue = ArrayDeque(listOf(startR * cols + startC))
            seen[startR * cols + startC] = true
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst()
                out += i
                val c = i % cols; val r = i / cols
                for ((nc, nr) in listOf(c - 1 to r, c + 1 to r, c to r - 1, c to r + 1)) {
                    if (nc !in 0 until cols || nr !in 0 until rows) continue
                    val n = nr * cols + nc
                    if (!seen[n] && cells[n] == value) { seen[n] = true; queue += n }
                }
            }
            return out
        }

        /** 칠한 칸의 연결 영역들 (칸 인덱스 목록) */
        fun regions(): List<List<Int>> {
            val seen = BooleanArray(cells.size)
            return cells.indices.filter { cells[it] && !seen[it] }.mapNotNull { i ->
                if (seen[i]) null else flood(i % cols, i / cols, seen)
            }
        }

        fun only(region: List<Int>) = Grid(cols, rows).also { g -> region.forEach { g.cells[it] = true } }

        /** 안쪽 구멍을 메우고, 대각선으로만 닿는 칸(외곽선이 한 점에서 만나는 곳)을 채움. 바뀌는 게 없을 때까지 */
        fun tidy(): Grid {
            var g = this
            while (true) {
                val outside = g.flood(0, 0, BooleanArray(cells.size)).toSet()   // 격자 모서리 (0,0)은 여백이라 항상 빈 칸
                val filled = Grid(cols, rows, BooleanArray(cells.size) { it !in outside })
                var changed = !filled.cells.contentEquals(g.cells)
                for (r in 0 until rows - 1) for (c in 0 until cols - 1) {
                    val a = filled[c, r]; val b = filled[c + 1, r]; val d = filled[c, r + 1]; val e = filled[c + 1, r + 1]
                    if (a == e && b == d && a != b) {
                        filled[c, r] = true; filled[c + 1, r] = true; filled[c, r + 1] = true; filled[c + 1, r + 1] = true
                        changed = true
                    }
                }
                if (!changed) return filled
                g = filled
            }
        }

        /** 칠한 영역의 외곽선을 격자 꼭짓점 (ix, iz) 순서로. 영역이 항상 진행 방향 왼쪽 */
        fun trace(): List<Pair<Int, Int>> {
            val w = cols + 1
            val next = IntArray(w * (rows + 1)) { -1 }
            fun edge(c1: Int, r1: Int, c2: Int, r2: Int) { next[r1 * w + c1] = r2 * w + c2 }
            for (r in 0 until rows) for (c in 0 until cols) {
                if (!this[c, r]) continue
                if (!this[c - 1, r]) edge(c, r, c, r + 1)
                if (!this[c, r + 1]) edge(c, r + 1, c + 1, r + 1)
                if (!this[c + 1, r]) edge(c + 1, r + 1, c + 1, r)
                if (!this[c, r - 1]) edge(c + 1, r, c, r)
            }
            val start = next.indexOfFirst { it >= 0 }
            if (start < 0) return emptyList()
            val out = mutableListOf<Pair<Int, Int>>()
            var v = start
            do {
                out += (v % w) to (v / w)
                v = next[v]
            } while (v != start && v >= 0 && out.size <= next.size)
            return out
        }
    }
}
