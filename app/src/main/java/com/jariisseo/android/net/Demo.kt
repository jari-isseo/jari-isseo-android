package com.jariisseo.android.net

/**
 * #16: [데모로 체험하기]. ARCore가 없는 기기(채점·시연)에서도 로그인 → 배치 → 저장 → 상태 전환 → 손님 웹을 볼 수 있게.
 * 운영 서버에 미리 가입해 둔 데모 계정으로 로그인하고, 매번 배치를 샘플(assets)로 되돌리고 상태를 모두 빈자리로.
 * → 앞사람이 바꾼 배치·손님 상태가 남지 않음. 비밀번호는 APK에 들어가 공개되므로 데모 전용 값
 */
object Demo {
    const val LOGIN_ID = "demo"
    const val PASSWORD = "jariisseo-demo"
    const val LAYOUT_ASSET = "demo-layout.json"

    /** assets의 샘플 배치 JSON (서버 docs/samples/sample-layout.json과 같은 형식) */
    fun parseLayout(json: String): LayoutDto = ApiClient.json.decodeFromString(LayoutDto.serializer(), json)
}

/** 데모 로그인 + 배치·상태 초기화. 실패는 ApiException (로그인 성공 후 초기화가 실패해도 로그인은 된 상태) */
class DemoRepository(private val auth: AuthRepository, private val layouts: LayoutRepository) {
    suspend fun start(layoutJson: String) {
        auth.login(Demo.LOGIN_ID, Demo.PASSWORD)
        layouts.resetTo(Demo.parseLayout(layoutJson))
    }
}
