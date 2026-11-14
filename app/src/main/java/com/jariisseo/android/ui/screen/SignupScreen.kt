package com.jariisseo.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jariisseo.android.net.ApiException
import com.jariisseo.android.net.Server
import com.jariisseo.android.net.displayMessage
import com.jariisseo.android.ui.CircleButton
import com.jariisseo.android.ui.Glyph
import com.jariisseo.android.ui.PrimaryButton
import com.jariisseo.android.ui.theme.Bg
import com.jariisseo.android.ui.theme.Danger
import com.jariisseo.android.ui.theme.Sub
import kotlinx.coroutines.launch

/**
 * #12: 가입 (아이디·비밀번호·매장 이름). 성공하면 가입 응답 토큰을 저장하고 onSignedUp (= 바로 로그인 상태).
 * 에러는 서버 문구 그대로 (검증 에러의 필드 이름만 뗌)
 */
@Composable
fun SignupScreen(onSignedUp: () -> Unit, onBack: () -> Unit) {
    var id by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }   // 비밀번호는 번들에 남기지 않음
    var storeName by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val canSubmit = !loading && id.isNotBlank() && password.isNotEmpty() && storeName.isNotBlank()
    fun submit() {
        if (!canSubmit) return
        loading = true
        error = null
        scope.launch {
            try {
                Server.auth.signup(id, password, storeName)
                onSignedUp()
            } catch (e: ApiException) {
                error = e.displayMessage()
            } finally {
                loading = false
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            CircleButton(Glyph.BACK, "뒤로", onBack)
            Spacer(Modifier.height(28.dp))
            Text("매장을\n등록해요", fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 42.sp)
            Spacer(Modifier.height(10.dp))
            Text("가입하면 바로 매장 배치를 만들 수 있어요", fontSize = 15.sp, color = Sub)
            Spacer(Modifier.height(36.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Field("아이디", id, { id = it; error = null }, placeholder = "영문 소문자·숫자·_ 4~20자")
                Field("비밀번호", password, { password = it; error = null }, placeholder = "8자 이상", secret = true)
                Field("매장 이름", storeName, { storeName = it; error = null }, placeholder = "예: 카페 문", last = true, onDone = ::submit)
            }

            Spacer(Modifier.weight(1f))
            error?.let { Text(it, Modifier.padding(top = 20.dp), color = Danger, fontSize = 14.sp) }
            PrimaryButton(
                if (loading) "확인 중…" else "가입하고 시작하기",
                ::submit,
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 28.dp),
                enabled = canSubmit,
            )
        }
    }
}
