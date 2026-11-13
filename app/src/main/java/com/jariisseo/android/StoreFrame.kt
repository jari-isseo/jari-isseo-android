package com.jariisseo.android

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * #6: 월드 (x, z) → 매장 좌표 (m, 0 이상).
 * 가장 긴 벽이 x축과 평행하도록 회전 (벽 없으면 생략) → 바닥·벽·가구의 최소점이 (0, 0)이 되도록 이동.
 * 높이(y)는 입력부터 쓰지 않음. ARCore에 의존하지 않아 단위 테스트 가능
 */
class StoreFrame private constructor(
    private val cos: Float,
    private val sin: Float,
    private val ox: Float,
    private val oz: Float,
) {
    fun point(p: Pair<Float, Float>): Pair<Float, Float> {
        val (x, z) = direction(p.first, p.second)
        return (x - ox) to (z - oz)
    }

    fun wall(w: WallLine): WallLine = point(w.first) to point(w.second)

    /** 방향 벡터는 회전만 (카메라 heading용) */
    fun direction(x: Float, z: Float): Pair<Float, Float> = (x * cos - z * sin) to (x * sin + z * cos)

    companion object {
        fun of(
            floor: List<Pair<Float, Float>>,
            walls: List<WallLine>,
            items: List<Pair<Float, Float>>,
        ): StoreFrame {
            val longest = walls.maxByOrNull { (a, b) ->
                val dx = b.first - a.first
                val dz = b.second - a.second
                dx * dx + dz * dz
            }
            val angle = longest?.let { (a, b) -> -atan2(b.second - a.second, b.first - a.first) } ?: 0f
            val rotateOnly = StoreFrame(cos(angle), sin(angle), 0f, 0f)
            val points = (floor + walls.flatMap { listOf(it.first, it.second) } + items).map(rotateOnly::point)
            if (points.isEmpty()) return rotateOnly
            return StoreFrame(rotateOnly.cos, rotateOnly.sin, points.minOf { it.first }, points.minOf { it.second })
        }
    }
}
