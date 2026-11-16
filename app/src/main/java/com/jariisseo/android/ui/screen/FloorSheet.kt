package com.jariisseo.android.ui.screen

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jariisseo.android.net.ApiException
import com.jariisseo.android.net.FloorDto
import com.jariisseo.android.net.Server
import com.jariisseo.android.net.displayMessage
import com.jariisseo.android.net.floorNameError
import com.jariisseo.android.ui.theme.Bg
import com.jariisseo.android.ui.theme.Danger
import com.jariisseo.android.ui.theme.Ink
import com.jariisseo.android.ui.theme.Sub
import kotlinx.coroutines.launch

/**
 * #25: 홈 층 행을 누르면 뜨는 층 관리 시트.
 * [보정] [다시 스캔] [이름 바꾸기] [위로] [아래로] [삭제]. 이름·순서·삭제는 서버 배치를 다시 받아 바꾼 뒤 저장 → onChanged(목록 새로고침)
 * index·count = 이 층의 순서 위치·층 수 (맨 위면 [위로], 맨 아래면 [아래로], 층이 1개면 [삭제] 비활성)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorSheet(
    floor: FloorDto,
    index: Int,
    count: Int,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRescan: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // 서버 작업 중에는 시트를 닫지 못하게 (닫히면 작업이 취소돼 서버엔 반영됐는데 홈 목록이 안 바뀔 수 있음)
    val sheetState = rememberModalBottomSheetState(confirmValueChange = { it != SheetValue.Hidden || !busy })

    // 서버 작업 하나: 성공하면 목록 새로고침, 실패하면 서버 문구 토스트 (시트는 그대로)
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
                onChanged()
            } catch (e: ApiException) {
                Toast.makeText(context, e.displayMessage(), Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState, containerColor = Bg) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(floor.name, Modifier.padding(horizontal = 24.dp), fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("테이블 ${floor.tables.size}개", Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp), fontSize = 14.sp, color = Sub)
            SheetAction("보정", enabled = !busy, onClick = onEdit)
            SheetAction("다시 스캔", enabled = !busy, onClick = onRescan)
            SheetAction("이름 바꾸기", enabled = !busy) { renaming = true }
            SheetAction("위로", enabled = !busy && index > 0) { run { Server.layouts.move(floor.id, up = true) } }
            SheetAction("아래로", enabled = !busy && index in 0 until count - 1) { run { Server.layouts.move(floor.id, up = false) } }
            SheetAction(
                if (count > 1) "삭제" else "삭제 (층이 하나뿐이라 지울 수 없어요)",
                enabled = !busy && count > 1,
                color = Danger,
            ) { confirmDelete = true }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(floor.name) }
        val error = floorNameError(name)
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("층 이름 바꾸기") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("층 이름", name, { name = it }, last = true)
                    error?.let { Text(it, color = Danger, fontSize = 14.sp) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { renaming = false; run { Server.layouts.rename(floor.id, name) } },
                    enabled = error == null && name.trim() != floor.name,
                ) { Text("바꾸기", color = Ink) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("취소", color = Ink) } },
            containerColor = Bg,
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("${floor.name}을 삭제할까요?") },
            text = { Text("이 층의 테이블과 손님 상태도 함께 지워져요. 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; run { Server.layouts.remove(floor.id) } }) { Text("삭제", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소", color = Ink) } },
            containerColor = Bg,
        )
    }
}

@Composable
private fun SheetAction(text: String, enabled: Boolean, color: androidx.compose.ui.graphics.Color = Ink, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 16.sp, color = if (enabled) color else Sub, fontWeight = FontWeight.Bold)
    }
}
