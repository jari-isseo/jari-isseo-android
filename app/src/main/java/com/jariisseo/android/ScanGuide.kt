package com.jariisseo.android

/** #9: 사장님용 스캔 안내 (추적 실패 이유·완료 조건·재촬영 안내) */

/** 추적 상태 (ARCore TrackingState + TrackingFailureReason을 화면용으로 줄인 것) */
enum class TrackingIssue { NONE, INITIALIZING, DARK, FAST, FEATURELESS, STOPPED, RESTART }

/** 이만큼 지났는데 확정 테이블이 없으면 '천천히 한 바퀴 더' 안내 */
const val SLOW_LAP_MS = 3 * 60_000L

/** 추적이 안 될 때 화면에 띄울 안내. 잘 되고 있으면 null */
fun trackingGuide(issue: TrackingIssue): String? = when (issue) {
    TrackingIssue.NONE -> null
    TrackingIssue.INITIALIZING -> "폰을 천천히 좌우로 움직여 주세요"
    TrackingIssue.DARK -> "너무 어두워요 · 불을 켜거나 밝은 곳을 비춰 주세요"
    TrackingIssue.FAST -> "조금 천천히 움직여 주세요"
    TrackingIssue.FEATURELESS -> "흰 벽·유리 말고 무늬 있는 곳을 비춰 주세요"
    TrackingIssue.STOPPED -> "위치를 놓쳤어요 · 처음 위치로 돌아가 천천히 비춰 주세요"
    TrackingIssue.RESTART -> "카메라에 문제가 있어요 · 스캔 화면을 나갔다 다시 들어와 주세요"
}

private fun confirmedTables(state: ArUiState) = state.mapItems.count { it.confirmed && it.label == "테이블" }

/** [스캔 완료] 가능: 바닥(= 벽 완료 외곽선)이 있고 확정 테이블이 1개 이상 */
fun canFinish(state: ArUiState): Boolean = state.outlineClosed && confirmedTables(state) > 0

/** 가구 단계에서 3분이 지났는데 확정 테이블이 아직 0개 */
fun needsSlowLap(elapsedMs: Long, state: ArUiState): Boolean =
    state.outlineClosed && elapsedMs >= SLOW_LAP_MS && confirmedTables(state) == 0

/** 경과 시간 "m:ss" */
fun formatElapsed(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
