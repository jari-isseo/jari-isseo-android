package com.jariisseo.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// #10: 시안 C 글자. 기본 한글 글꼴 + 자간 -0.02em
private val base = TextStyle(letterSpacing = (-0.02).em)

val Typography = Typography(
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = base.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = base.copy(fontSize = 17.sp),
)
