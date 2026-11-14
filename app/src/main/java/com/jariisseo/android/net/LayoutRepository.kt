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

    /** 저장할 배치 전체를 만듦. resetsStatus면 저장 전에 사용자 확인 */
    suspend fun prepare(floor: Layout): PendingLayout = withFloor(fetch(), floor)

    /** 저장 후 서버가 정리한 같은 층 (재보정 때 그대로 이어서 쓸 값) */
    suspend fun save(pending: PendingLayout, floorId: String): Layout {
        val saved = apiCall { api.saveLayout(pending.layout) }
        return (saved.floors.firstOrNull { it.id == floorId } ?: saved.currentFloor()!!).toLayout()
    }

    private suspend fun fetch(): LayoutDto? =
        try {
            apiCall { api.getLayout() }
        } catch (e: ApiException) {
            if (e.code == "LAYOUT_NOT_FOUND") null else throw e
        }
}
