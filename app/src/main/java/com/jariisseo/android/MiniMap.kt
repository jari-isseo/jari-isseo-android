package com.jariisseo.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * 9교시: 위에서 내려다본 배치도 (탑뷰).
 * - 내 위치(카메라)가 항상 가운데, 지도는 고정 (월드 x = 오른쪽, 월드 z = 아래쪽)
 * - 격자 한 칸 = 1m
 * - 확정된 가구는 진하게, 아직 관측이 적은 가구는 흐리게
 * - #4: 바닥 외곽선(floor.polygon)을 노란 테두리로
 * - #5: 벽(walls)을 주황 굵은 선으로
 * - #38: 기록한 벽을 흰 짧은 선으로 (완료되면 외곽선이 바닥·벽으로 그려짐)
 */
@Composable
fun MiniMap(state: ArUiState, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color(0xCC101010))) {
        val camX = state.cameraX
        val camZ = state.cameraZ

        // 보여줄 범위(반지름, m): 최소 2.5m, 가구가 더 멀리 있으면 거기까지 넓힘
        var range = 2.5f
        state.mapItems.forEach { range = max(range, max(abs(it.x - camX), abs(it.z - camZ)) + 0.5f) }
        state.floorPolygon.forEach { (x, z) -> range = max(range, max(abs(x - camX), abs(z - camZ)) + 0.5f) }
        state.walls.forEach { (a, b) ->
            listOf(a, b).forEach { (x, z) -> range = max(range, max(abs(x - camX), abs(z - camZ)) + 0.5f) }
        }
        state.touchedWalls.forEach { (a, b) ->
            listOf(a, b).forEach { (x, z) -> range = max(range, max(abs(x - camX), abs(z - camZ)) + 0.5f) }
        }
        val pxPerMeter = size.minDimension / 2f / range
        val center = Offset(size.width / 2f, size.height / 2f)

        // 월드 좌표(m) → 미니맵 픽셀
        fun toMap(x: Float, z: Float) = Offset(
            center.x + (x - camX) * pxPerMeter,
            center.y + (z - camZ) * pxPerMeter,
        )

        // ① 1m 격자
        val gridColor = Color.White.copy(alpha = 0.12f)
        for (gx in floor(camX - range).toInt()..ceil(camX + range).toInt()) {
            val x = toMap(gx.toFloat(), 0f).x
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height))
        }
        for (gz in floor(camZ - range).toInt()..ceil(camZ + range).toInt()) {
            val y = toMap(0f, gz.toFloat()).y
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y))
        }

        // ② #4: 바닥 외곽선 (반투명 채움 + 노란 테두리)
        if (state.floorPolygon.size >= 3) {
            val path = Path().apply {
                state.floorPolygon.forEachIndexed { i, (x, z) ->
                    val p = toMap(x, z)
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(path, Color.White.copy(alpha = 0.1f))
            drawPath(path, Color.Yellow.copy(alpha = 0.8f), style = Stroke(width = 3f))
        }

        // ②-1 #5: 벽 선분 (주황 굵은 선)
        state.walls.forEach { (a, b) ->
            drawLine(Color(0xFFFF9800), toMap(a.first, a.second), toMap(b.first, b.second), strokeWidth = 7f)
        }

        // ②-2 #38: 기록한 벽 (흰 짧은 선)
        state.touchedWalls.forEach { (a, b) ->
            drawLine(Color.White, toMap(a.first, a.second), toMap(b.first, b.second), strokeWidth = 5f)
        }

        // ③ 가구: 테이블 = 초록 사각형, 의자 = 하늘색 원, 소파 = 보라 긴 사각형
        state.mapItems.forEach { item ->
            val p = toMap(item.x, item.z)
            val alpha = if (item.confirmed) 1f else 0.3f
            when (item.label) {
                "테이블" -> {
                    val s = 0.7f * pxPerMeter      // 대략 70cm 테이블 크기로 표시
                    drawRect(Color.Green.copy(alpha = alpha), Offset(p.x - s / 2, p.y - s / 2), Size(s, s))
                }
                "의자" -> drawCircle(Color.Cyan.copy(alpha = alpha), radius = 0.2f * pxPerMeter, center = p)
                else -> {
                    val w = 1.6f * pxPerMeter
                    val h = 0.7f * pxPerMeter
                    drawRect(Color.Magenta.copy(alpha = alpha), Offset(p.x - w / 2, p.y - h / 2), Size(w, h))
                }
            }
        }

        // ④ 내 위치 + 바라보는 방향
        val len = max(1e-3f, kotlin.math.sqrt(state.headingX * state.headingX + state.headingZ * state.headingZ))
        val tip = Offset(
            center.x + state.headingX / len * 0.6f * pxPerMeter,
            center.y + state.headingZ / len * 0.6f * pxPerMeter,
        )
        drawLine(Color.White, center, tip, strokeWidth = 4f)
        drawCircle(Color.White, radius = 8f, center = center)
    }
}
