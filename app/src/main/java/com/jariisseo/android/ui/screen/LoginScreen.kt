package com.jariisseo.android.ui.screen

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jariisseo.android.net.ApiException
import com.jariisseo.android.net.Demo
import com.jariisseo.android.net.Server
import com.jariisseo.android.net.displayMessage
import com.jariisseo.android.R
import com.jariisseo.android.ui.PrimaryButton
import com.jariisseo.android.ui.theme.Bg
import com.jariisseo.android.ui.theme.Brown
import com.jariisseo.android.ui.theme.Danger
import com.jariisseo.android.ui.theme.Ink
import com.jariisseo.android.ui.theme.Sub
import kotlinx.coroutines.launch

/**
 * #10: 시안 D ③ 로그인. #12: 실제 로그인, [가입하기]는 가입 화면(SignupScreen)으로.
 * 성공하면 토큰 저장 후 onLoggedIn. 에러는 서버 문구 그대로
 * #16: [데모로 체험하기] = 데모 계정으로 로그인하고 배치를 샘플로 초기화 → 홈 (ARCore 없는 기기용)
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit, onSignup: () -> Unit) {
    var id by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }   // 비밀번호는 화면 회전 등에서 번들에 남기지 않음
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val canSubmit = !loading && id.isNotBlank() && password.isNotEmpty()
    fun demo() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            try {
                val json = context.assets.open(Demo.LAYOUT_ASSET).bufferedReader().use { it.readText() }
                Server.demo.start(json)
                onLoggedIn()
            } catch (e: ApiException) {
                error = e.displayMessage()
            } finally {
                loading = false
            }
        }
    }
    fun submit() {
        // canSubmit은 그린 시점 값이라, 다시 그리기 전에 두 번 눌리면 둘 다 통과 → loading은 지금 값으로 확인
        if (loading || !canSubmit) return
        loading = true
        error = null
        scope.launch {
            try {
                Server.auth.login(id, password)
                onLoggedIn()
            } catch (e: ApiException) {
                error = e.displayMessage()
            } finally {
                loading = false
            }
        }
    }

    // 화면이 짧으면(멀티 윈도우·키보드) 스크롤, 길면 버튼이 맨 아래에 붙도록 최소 높이 = 화면 높이
    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(88.dp))
            // mipmap/ic_launcher는 API 26+에서 adaptive-icon XML이라 painterResource가 못 읽음 → 전경 PNG 사용
            Image(
                painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = "자리있어",
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(Color.White),
            )
            Spacer(Modifier.height(24.dp))
            Text("사장님,\n반가워요", fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 42.sp)
            Spacer(Modifier.height(10.dp))
            Text("매장 빈자리를 손님에게 바로 알려드려요", fontSize = 15.sp, color = Sub)
            Spacer(Modifier.height(36.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Field("아이디", id, { id = it; error = null })
                Field("비밀번호", password, { password = it; error = null }, secret = true, last = true, onDone = ::submit)
            }

            Spacer(Modifier.weight(1f))
            error?.let { Text(it, Modifier.padding(top = 20.dp), color = Danger, fontSize = 14.sp) }
            PrimaryButton(if (loading) "확인 중…" else "로그인", ::submit, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = canSubmit)
            TextButton(
                onClick = onSignup,
                enabled = !loading,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Sub)) { append("처음이신가요? ") }
                        withStyle(SpanStyle(color = Ink, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) { append("가입하기") }
                    },
                    fontSize = 15.sp,
                )
            }
            // #16: 스캔 없이 샘플 배치로 둘러보기
            TextButton(
                onClick = ::demo,
                enabled = !loading,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp),
            ) {
                Text("데모로 체험하기", color = Sub, fontSize = 14.sp, textDecoration = TextDecoration.Underline)
            }
        }
    }
}

/** 로그인·가입 입력 칸. last = 마지막 칸 (키보드 [완료] = onDone), 아니면 [다음] */
@Composable
internal fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    secret: Boolean = false,
    last: Boolean = false,
    onDone: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Sub)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Brown,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                cursorColor = Brown,
            ),
        )
    }
}
