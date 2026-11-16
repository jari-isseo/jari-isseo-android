package com.jariisseo.android.net

/** #63: 매장 전체(모든 층) 빈자리 수 / 테이블 수 */
data class SeatCount(val empty: Int, val total: Int)

/**
 * 배치가 없으면 null. 상태 기록이 없는 테이블은 빈자리 (서버는 처음엔 상태를 만들지 않음).
 * 배치에 없는 테이블의 상태는 무시
 */
fun seatCountOf(snapshot: PublicSnapshot): SeatCount? {
    val layout = snapshot.layout ?: return null
    val tableIds = layout.floors.flatMap { f -> f.tables.map { it.id } }
    val occupied = snapshot.statuses.filter { it.occupied }.map { it.tableId }.toSet()
    return SeatCount(empty = tableIds.count { it !in occupied }, total = tableIds.size)
}
