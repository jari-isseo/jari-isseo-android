package com.jariisseo.android.net

import com.jariisseo.android.Layout

/**
 * #15: 배치 불러오기·저장. 서버는 항상 모든 층을 한 번에 저장하므로,
 * 저장 직전에 최신 서버 배치를 받아 고친 층 하나만 끼워 넣어(withFloor) 전체를 PUT.
 * 실패는 ApiException (화면은 displayMessage)
 */
class LayoutRepository(private val api: JariApi) {
    /** 서버 배치의 현재 층 (저장한 적이 없으면 null) */
    suspend fun load(): Layout? = fetch()?.currentFloor()?.toLayout()

    /** 이 층을 저장하면 테이블 상태가 초기화되는지 (다시 스캔) → true면 저장 전에 사용자 확인 */
    suspend fun resetsStatus(floor: Layout): Boolean = withFloor(fetch(), floor).resetsStatus

    /**
     * 저장. 확인 창이 떠 있는 동안 다른 층이 바뀌었을 수 있어서, PUT 바로 전에 서버 배치를 다시 받아 이 층만 끼워 넣음.
     * 반환 = 서버가 정리한 같은 층 (재보정 때 그대로 이어서 쓸 값)
     */
    suspend fun save(floor: Layout): Layout {
        val saved = apiCall { api.saveLayout(withFloor(fetch(), floor).layout) }
        return (saved.floors.firstOrNull { it.id == floor.floorId } ?: saved.currentFloor()!!).toLayout()
    }

    private suspend fun fetch(): LayoutDto? =
        try {
            apiCall { api.getLayout() }
        } catch (e: ApiException) {
            if (e.code == "LAYOUT_NOT_FOUND") null else throw e
        }
}
