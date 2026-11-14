package com.jariisseo.android.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jariisseo.android.net.ApiException
import com.jariisseo.android.net.Server
import com.jariisseo.android.net.UpdateStoreRequest
import com.jariisseo.android.net.apiCall
import com.jariisseo.android.net.displayMessage
import com.jariisseo.android.net.storeNameError
import com.jariisseo.android.ui.CircleButton
import com.jariisseo.android.ui.Glyph
import com.jariisseo.android.ui.OutlineButton
import com.jariisseo.android.ui.PageTitle
import com.jariisseo.android.ui.PillTab
import com.jariisseo.android.ui.PrimaryButton
import com.jariisseo.android.ui.theme.Bg
import com.jariisseo.android.ui.theme.Danger
import com.jariisseo.android.ui.theme.Sub
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * #42: 가게 정보 (홈 하단 메뉴 [가게 정보]). 매장 이름 확인·수정, 아이디 표시, 로그아웃.
 * 이름 저장 실패 시 마지막으로 저장된 이름으로 되돌리고 토스트. 로그아웃 = 토큰 삭제 → onLoggedOut (뒤로 가기 기록은 AppNav가 비움)
 */
@Composable
fun StoreScreen(onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loginId by Server.tokens.loginIdFlow.collectAsState(initial = null)
    var attempt by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf<String?>(null) }   // 서버에 저장된 이름 (불러오기 전 null)
    var name by remember { mutableStateOf("") }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        loadError = null
        try {
            val store = apiCall { Server.api.myStore() }
            saved = store.name
            name = store.name
        } catch (e: ApiException) {
            loadError = e.displayMessage()
        }
    }

    val nameError = if (saved != null) storeNameError(name) else null
    val canSave = saved != null && !saving && nameError == null && name.trim() != saved
    fun save() {
        if (saving || !canSave) return
        saving = true
        // 응답 전에 또 고쳐 쓴 이름은 덮어쓰지 않음 (보낸 그대로일 때만 결과로 바꾸거나 되돌림)
        val submitted = name
        scope.launch {
            try {
                val res = apiCall { Server.api.updateStore(UpdateStoreRequest(submitted.trim())) }
                saved = res.name
                if (name == submitted) name = res.name
                Toast.makeText(context, "매장 이름을 바꿨어요", Toast.LENGTH_SHORT).show()
            } catch (e: ApiException) {
                val last = saved
                if (name == submitted && last != null) name = last
                Toast.makeText(context, e.displayMessage(), Toast.LENGTH_SHORT).show()
            } finally {
                saving = false
            }
        }
    }

    // 로그인 화면과 같이: 짧은 화면은 스크롤, 길면 로그아웃이 맨 아래
    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)) {
            Row(Modifier.padding(start = 16.dp, top = 24.dp)) { CircleButton(Glyph.BACK, "홈으로", onBack) }
            PageTitle("가게 정보", "매장 이름은 손님 화면 맨 위에 보여요", Modifier.padding(top = 16.dp))

            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val error = loadError
                when {
                    error != null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("가게 정보를 불러오지 못했어요\n$error", fontSize = 14.sp, color = Sub)
                        PillTab("다시 시도", selected = true) { attempt++ }
                    }
                    saved == null -> Text("불러오는 중…", fontSize = 14.sp, color = Sub)
                    else -> {
                        Field("매장 이름", name, { name = it }, last = true, onDone = ::save)
                        nameError?.let { Text(it, color = Danger, fontSize = 14.sp) }
                        PrimaryButton(if (saving) "저장 중…" else "저장", ::save, Modifier.fillMaxWidth(), enabled = canSave)
                    }
                }

                // 아이디 (바꿀 수 없음)
                Column(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("아이디", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Sub)
                    Text(loginId ?: "-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.weight(1f))
            Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp)) {
                OutlineButton(
                    "로그아웃",
                    {
                        scope.launch {
                            // 저장소 쓰기 실패 → 토큰이 남아 있으므로 화면에 그대로 (앱이 꺼지지 않게)
                            try {
                                Server.tokens.delete()
                            } catch (_: IOException) {
                                Toast.makeText(context, "로그아웃하지 못했어요. 다시 시도해 주세요", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            onLoggedOut()
                        }
                    },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
