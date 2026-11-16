package com.jariisseo.android.net

import com.jariisseo.android.ChairItem
import com.jariisseo.android.Layout
import com.jariisseo.android.TABLE_SIZE_M
import com.jariisseo.android.TableItem
import com.jariisseo.android.newId
import kotlinx.serialization.Serializable
import kotlin.random.Random
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

/**
 * 저장할 배치 전체 + 이 저장으로 한 층의 테이블 상태가 초기화되는지 (재스캔) + 저장될 그 층의 id
 */
data class PendingLayout(val layout: LayoutDto, val resetsStatus: Boolean, val floorId: String)

/** #25: 서버 검증 규칙 (층 1~10개, 이름 1~20자) */
const val MAX_FLOORS = 10
private const val MAX_FLOOR_NAME = 20

/**
 * #25: 지금 작업하는(스캔·보정) 층.
 * Existing = 서버에 있는 층 (보정·다시 스캔), New = 새로 추가할 층 (스캔 후 첫 저장 때 서버에 생김. 빈 층은 저장할 수 없음)
 */
sealed interface FloorTarget {
    val name: String

    data class Existing(val id: String, override val name: String) : FloorTarget
    data class New(override val name: String) : FloorTarget
}

/**
 * 서버 배치(모든 층)에 지금 고친 층 하나만 끼워 넣음. 다른 층은 그대로 → 그 층 테이블 상태 유지.
 * - 서버 배치 없음 → 그 층 하나 (order 0)
 * - Existing → 그 id의 층만 교체, id·이름·순서는 서버 값 유지.
 *   다시 스캔(앱 배치의 floorId가 새로 생김)이고 그 층에 테이블이 있었다면 상태가 초기화됨.
 *   그새 그 층이 삭제됐으면 맨 뒤에 추가
 * - New → 맨 뒤(order 최대 + 1)에 추가
 * 서버는 테이블 id를 모든 층에서 유일하게 요구 → 다른 층과 겹치는 테이블·의자 id는 새로 만듦
 */
fun withFloor(server: LayoutDto?, floor: Layout, target: FloorTarget, random: Random = Random.Default): PendingLayout {
    // 새 층 저장이 서버엔 반영됐는데 응답만 못 받고 다시 저장하는 경우 → 이미 서버에 있는 그 층을 교체 (또 추가하지 않음).
    //   층 id는 앱이 만든 랜덤 id라, 층 id와 테이블 id가 모두 같으면 같은 저장으로 봄 (id만 같은 다른 층은 덮어쓰지 않음)
    val retried = (target as? FloorTarget.New)?.let { server?.floors?.firstOrNull { it.isSameSave(floor) } }
    if (retried != null) return withFloor(server, floor, FloorTarget.Existing(retried.id, retried.name), random)

    val others = server?.floors.orEmpty().filterNot { target is FloorTarget.Existing && it.id == target.id }
    val replacing = (target as? FloorTarget.Existing)?.let { t -> server?.floors?.firstOrNull { it.id == t.id } }
    val floorId = when {
        target is FloorTarget.Existing -> target.id
        others.any { it.id == floor.floorId } -> newId("f-", others.map { it.id }.toSet(), random)
        else -> floor.floorId
    }
    val fixed = withUniqueItemIds(floor.copy(floorId = floorId), others, random)
    val dto = fixed.toFloorDto(
        name = replacing?.name ?: target.name.trim(),
        order = replacing?.order ?: ((others.maxOfOrNull { it.order } ?: -1) + 1),
    )
    val floors = if (replacing != null) server!!.floors.map { if (it.id == floorId) dto else it } else others + dto
    val rescanned = replacing != null && floor.floorId != replacing.id
    return PendingLayout(
        (server ?: LayoutDto(floors = emptyList())).copy(floors = floors),
        resetsStatus = rescanned && replacing!!.tables.isNotEmpty(),
        floorId = floorId,
    )
}

/** 서버 층이 이 앱 배치를 저장한 결과인지 (층 id와 테이블 id 집합이 같음) */
private fun FloorDto.isSameSave(floor: Layout): Boolean =
    id == floor.floorId && tables.map { it.id }.toSet() == floor.tables.map { it.id }.toSet()

/** 다른 층의 테이블·의자 id와 겹치는 것만 새 id로 (겹치지 않으면 그대로 → 상태 유지) */
private fun withUniqueItemIds(floor: Layout, others: List<FloorDto>, random: Random): Layout {
    val used = others.flatMap { f -> f.tables.map { it.id } + f.chairs.map { it.id } }.toMutableSet()
    val taken = used.toSet()
    if ((floor.tables.map { it.id } + floor.chairs.map { it.id }).none { it in taken }) return floor
    used += floor.tables.map { it.id } + floor.chairs.map { it.id }
    fun fresh(id: String, prefix: String) = if (id in taken) newId(prefix, used, random).also { used += it } else id
    return floor.copy(
        tables = floor.tables.map { it.copy(id = fresh(it.id, "t-")) },
        chairs = floor.chairs.map { it.copy(id = fresh(it.id, "c-")) },
    )
}

/** #25: 층 이름만 바꿈 (상태 유지) */
fun LayoutDto.renameFloor(id: String, name: String): LayoutDto =
    copy(floors = floors.map { if (it.id == id) it.copy(name = name.trim()) else it })

/** #25: 순서상 위(up)·아래 이웃 층과 order 값을 맞바꿈. 맨 끝이면 그대로 */
fun LayoutDto.moveFloor(id: String, up: Boolean): LayoutDto {
    val sorted = floors.sortedBy { it.order }
    val i = sorted.indexOfFirst { it.id == id }
    val j = if (up) i - 1 else i + 1
    if (i < 0 || j !in sorted.indices) return this
    val a = sorted[i]
    val b = sorted[j]
    return copy(floors = floors.map { if (it.id == a.id) it.copy(order = b.order) else if (it.id == b.id) it.copy(order = a.order) else it })
}

/** #25: 층 삭제 (그 층 테이블 상태도 서버에서 지워짐). 서버는 층 0개를 거부 → 마지막 층은 못 지움 */
fun LayoutDto.removeFloor(id: String): LayoutDto {
    val rest = floors.filterNot { it.id == id }
    require(rest.isNotEmpty()) { "마지막 층은 삭제할 수 없어요" }
    return copy(floors = rest)
}

/** #25: 서버 검증과 같은 기준 (앞뒤 공백 제거 후 1~20자). 문제없으면 null */
fun floorNameError(name: String): String? = when {
    name.isBlank() -> "층 이름을 입력해 주세요"
    name.trim().length > MAX_FLOOR_NAME -> "층 이름은 ${MAX_FLOOR_NAME}자까지예요"
    else -> null
}

/** #25: 새 층 기본 이름 = 아직 안 쓴 "n층" 중 가장 작은 n */
fun nextFloorName(names: List<String>): String {
    val used = names.map { it.trim() }.toSet()
    return generateSequence(1) { it + 1 }.map { "${it}층" }.first { it !in used }
}
