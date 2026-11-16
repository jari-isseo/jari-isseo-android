package com.jariisseo.android.net

import com.jariisseo.android.Layout

/**
 * #15: 배치 불러오기·저장. 서버는 항상 모든 층을 한 번에 저장하므로,
 * 저장 직전에 최신 서버 배치를 받아 고친 층 하나만 끼워 넣어(withFloor) 전체를 PUT.
 * #25: 층 단위 (FloorTarget). 층 이름·순서·삭제도 같은 방식 (최신 배치를 받아 바꾼 뒤 전체 PUT)
 * 실패는 ApiException (화면은 displayMessage)
 */
class LayoutRepository(private val api: JariApi) {
    /** #25: 층 목록 (order 순, 저장한 적이 없으면 빈 목록) */
    suspend fun floors(): List<FloorDto> = fetch()?.floors?.sortedBy { it.order }.orEmpty()

    /** #25: 그 층 (없으면 null) */
    suspend fun load(floorId: String): Layout? = fetch()?.floors?.firstOrNull { it.id == floorId }?.toLayout()

    /** 이 층을 저장하면 테이블 상태가 초기화되는지 (다시 스캔) → true면 저장 전에 사용자 확인 */
    suspend fun resetsStatus(floor: Layout, target: FloorTarget): Boolean = withFloor(fetch(), floor, target).resetsStatus

    /**
     * 저장. 확인 창이 떠 있는 동안 다른 층이 바뀌었을 수 있어서, PUT 바로 전에 서버 배치를 다시 받아 이 층만 끼워 넣음.
     * 반환 = 서버가 정리한 같은 층 (재보정 때 그대로 이어서 쓸 값. 새 층이면 이제 서버에 있는 그 층)
     */
    suspend fun save(floor: Layout, target: FloorTarget): Layout {
        val pending = withFloor(fetch(), floor, target)
        val saved = apiCall { api.saveLayout(pending.layout) }
        return (saved.floors.firstOrNull { it.id == pending.floorId } ?: pending.layout.floors.first { it.id == pending.floorId }).toLayout()
    }

    suspend fun rename(floorId: String, name: String) = update { it.renameFloor(floorId, name) }

    suspend fun move(floorId: String, up: Boolean) = update { it.moveFloor(floorId, up) }

    /** 그새 다른 층이 지워져 이 층이 마지막이면 저장하지 않고 ApiException */
    suspend fun remove(floorId: String) = update {
        try {
            it.removeFloor(floorId)
        } catch (e: IllegalArgumentException) {
            throw ApiException(0, "LAST_FLOOR", e.message ?: "마지막 층은 삭제할 수 없어요")
        }
    }

    /** 최신 배치를 받아 바꾼 뒤 저장 (배치가 없으면 바꿀 층도 없음) */
    private suspend fun update(change: (LayoutDto) -> LayoutDto) {
        val server = fetch() ?: return
        apiCall { api.saveLayout(change(server)) }
    }

    private suspend fun fetch(): LayoutDto? =
        try {
            apiCall { api.getLayout() }
        } catch (e: ApiException) {
            if (e.code == "LAYOUT_NOT_FOUND") null else throw e
        }
}
