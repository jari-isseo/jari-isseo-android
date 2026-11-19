package com.jariisseo.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// #10: 시안 D 라이트 고정 (기기 동적 색·다크 모드 사용 안 함). 글꼴은 기본(Roboto + 한글 Noto Sans CJK)
private val ColorScheme = lightColorScheme(
    primary = Brown,
    onPrimary = Color.White,
    secondary = Sage,
    onSecondary = SageInk,
    background = Bg,
    onBackground = Ink,
    surface = Bg,
    onSurface = Ink,
    surfaceVariant = Muted,
    onSurfaceVariant = Sub,
    outline = Line,
    error = Danger,
)

@Composable
fun JariIsseoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = Typography,
        content = content,
    )
}
