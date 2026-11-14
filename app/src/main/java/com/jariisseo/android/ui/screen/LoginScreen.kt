package com.jariisseo.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jariisseo.android.ui.Glyph
import com.jariisseo.android.ui.LineIcon
import com.jariisseo.android.ui.PrimaryButton
import com.jariisseo.android.ui.theme.Bg
import com.jariisseo.android.ui.theme.Brown
import com.jariisseo.android.ui.theme.Ink
import com.jariisseo.android.ui.theme.Sage
import com.jariisseo.android.ui.theme.SageInk
import com.jariisseo.android.ui.theme.Sub

/** #10: 시안 D ③ 로그인. 입력 검증·가입은 #12 (지금은 [로그인]이 홈으로 이동만) */
@Composable
fun LoginScreen(onLogin: () -> Unit) {
    var id by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(88.dp))
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Sage), contentAlignment = Alignment.Center) {
            LineIcon(Glyph.TABLE, SageInk, size = 30.dp)
        }
        Spacer(Modifier.height(24.dp))
        Text("사장님,\n반가워요", fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 42.sp)
        Spacer(Modifier.height(10.dp))
        Text("매장 빈자리를 손님에게 바로 알려드려요", fontSize = 15.sp, color = Sub)
        Spacer(Modifier.height(36.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Field("아이디", id, { id = it })
            Field("비밀번호", password, { password = it }, placeholder = "8자 이상", secret = true)
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton("로그인", onLogin, Modifier.fillMaxWidth())
        TextButton(onClick = {}, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 20.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Sub)) { append("처음이신가요? ") }
                    withStyle(SpanStyle(color = Ink, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) { append("가입하기") }
                },
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, placeholder: String = "", secret: Boolean = false) {
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
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text),
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
