package com.jariisseo.android.net

import com.jariisseo.android.ChairItem
import com.jariisseo.android.Layout
import com.jariisseo.android.TABLE_SIZE_M
import com.jariisseo.android.TableItem
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// #15: 서버 배치 형식 (API 정의서 2장, server `layout/dto/LayoutData.java`·`LayoutResponse.java`와 같은 모양).
// GET·PUT 응답에는 version이 있고, PUT 요청의 version은 서버가 무시함

@Serializable
data class LayoutDto(val version: Int = 0, val unit: String = "m", val floors: List<FloorDto>)

@Serializable
data class FloorDto(
    val id: String,
    val name: String,
    val order: Int,
    val floor: FloorShapeDto,
    val walls: List<WallDto> = emptyList(),
    val tables: List<TableDto> = emptyList(),
    val chairs: List<ChairDto> = emptyList(),
)

@Serializable
data class FloorShapeDto(val polygon: List<List<Double>>)

@Serializable
data class WallDto(val id: String, val from: List<Double>, val to: List<Double>)

@Serializable
data class TableDto(
    val id: String,
    val name: String,
    val x: Double,
    val z: Double,
    val width: Double = TABLE_SIZE_M.toDouble(),
    val depth: Double = TABLE_SIZE_M.toDouble(),
    val rotation: Double = 0.0,
    val shape: String = "RECT",
)

@Serializable
data class ChairDto(val id: String, val x: Double, val z: Double)

/** 서버 층 → 앱 배치 (벽 id는 앱에서 쓰지 않음) */
fun FloorDto.toLayout(): Layout = Layout(
    floorId = id,
    floor = floor.polygon.map { it[0].toFloat() to it[1].toFloat() },
    walls = walls.map { (it.from[0].toFloat() to it.from[1].toFloat()) to (it.to[0].toFloat() to it.to[1].toFloat()) },
    tables = tables.map {
        TableItem(it.id, it.name, it.x.toFloat(), it.z.toFloat(), it.width.toFloat(), it.depth.toFloat(), it.rotation.toFloat(), it.shape)
    },
    chairs = chairs.map { ChairItem(it.id, it.x.toFloat(), it.z.toFloat()) },
)

/**
 * 앱 배치 → 서버 층. 좌표·크기는 cm 반올림 (#7 내보내기와 같은 규칙),
 * 회전은 정수 도로 반올림 후 360으로 나눈 나머지 (359.6 → 360이 되면 서버가 0~359 규칙으로 거부)
 */
fun Layout.toFloorDto(name: String, order: Int): FloorDto = FloorDto(
    id = floorId,
    name = name,
    order = order,
    floor = FloorShapeDto(floor.map { listOf(cm(it.first), cm(it.second)) }),
    walls = walls.mapIndexed { i, (a, b) -> WallDto("w${i + 1}", listOf(cm(a.first), cm(a.second)), listOf(cm(b.first), cm(b.second))) },
    tables = tables.map {
        TableDto(it.id, it.name, cm(it.x), cm(it.z), cm(it.width), cm(it.depth), (it.rotation.roundToInt() % 360).toDouble(), it.shape)
    },
    chairs = chairs.map { ChairDto(it.id, cm(it.x), cm(it.z)) },
)

private fun cm(v: Float): Double = (v * 100.0).roundToLong() / 100.0 + 0.0   // + 0.0: -0.0 → 0.0

/** 저장할 배치 전체 + 이 저장으로 한 층의 테이블 상태가 초기화되는지 (재스캔) */
data class PendingLayout(val layout: LayoutDto, val resetsStatus: Boolean)

const val DEFAULT_FLOOR_NAME = "1층"

/**
 * 서버 배치(모든 층)에 지금 고친 층 하나만 끼워 넣음. 다른 층은 그대로 → 그 층 테이블 상태 유지.
 * - 서버 배치 없음 → 층 1개 ("1층", order 0)
 * - 같은 floorId 층이 있음 (다시 보정) → 그 층만 교체, 이름·순서 유지
 * - 없음 (다시 스캔해서 floorId가 새로 생김) → 첫 층(order 최소)을 교체, 이름·순서 유지.
 *   그 층에 테이블이 있었다면 상태가 초기화됨 (층 선택은 #25 전이라 앱은 첫 층만 다룸)
 */
fun withFloor(server: LayoutDto?, floor: Layout): PendingLayout {
    if (server == null || server.floors.isEmpty()) {
        return PendingLayout(LayoutDto(floors = listOf(floor.toFloorDto(DEFAULT_FLOOR_NAME, 0))), resetsStatus = false)
    }
    val target = server.floors.firstOrNull { it.id == floor.floorId } ?: server.floors.minBy { it.order }
    val replaced = server.floors.map { if (it === target) floor.toFloorDto(target.name, target.order) else it }
    val rescanned = target.id != floor.floorId
    return PendingLayout(server.copy(floors = replaced), resetsStatus = rescanned && target.tables.isNotEmpty())
}

/** 앱이 다루는 층 (#25 전: 첫 층) */
fun LayoutDto.currentFloor(): FloorDto? = floors.minByOrNull { it.order }
