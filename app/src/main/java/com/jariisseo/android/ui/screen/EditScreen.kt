package com.jariisseo.android.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jariisseo.android.Layout
import com.jariisseo.android.TABLE_SIZE_MAX
import com.jariisseo.android.TABLE_SIZE_MIN
import com.jariisseo.android.TableItem
import com.jariisseo.android.addTable
import com.jariisseo.android.net.ApiException
import com.jariisseo.android.net.FloorTarget
import com.jariisseo.android.net.Server
import com.jariisseo.android.net.displayMessage
import com.jariisseo.android.normalizeDegrees
import com.jariisseo.android.removeChair
import com.jariisseo.android.removeTable
import com.jariisseo.android.rotateLayout
import com.jariisseo.android.updateTable
import com.jariisseo.android.ui.CircleButton
import com.jariisseo.android.ui.FOCUS_ANIM_MS
import com.jariisseo.android.ui.TopView
import com.jariisseo.android.ui.Glyph
import com.jariisseo.android.ui.LineIcon
import com.jariisseo.android.ui.PageTitle
import com.jariisseo.android.ui.PillTab
import com.jariisseo.android.ui.points
import com.jariisseo.android.ui.theme.Bg
import com.jariisseo.android.ui.theme.Brown
import com.jariisseo.android.ui.theme.Danger
import com.jariisseo.android.ui.theme.Ink
import com.jariisseo.android.ui.theme.Line
import com.jariisseo.android.ui.theme.Sage
import com.jariisseo.android.ui.theme.Sub
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * #10: 시안 D ⑥ 탑뷰 보정. #13: 스캔한 배치를 탑뷰로 보여주고 테이블 드래그·확대 축소.
 * #14: 테이블 추가·탭 편집·길게 눌러 삭제, 의자 이동·삭제, 배치 전체 회전(90° + 미세 조정), 되돌리기.
 * #15: [저장] = 서버에 업로드 (다른 층은 그대로 두고 이 층만 교체). 다시 스캔한 층이면 상태 초기화 확인 후 저장.
 *   들어왔을 때 보정할 배치가 없으면(layout = null) 서버 배치를 불러와 기존 테이블 ID 그대로 다시 보정.
 * 벽 맞추기(방 외곽선 꼭짓점)는 #26
 * #25: 층 단위 (floorTarget). 기존 층은 그 층만 불러오고 교체, 새 층은 첫 저장 때 서버에 생김 → 이후엔 기존 층으로
 */
@Composable
fun EditScreen(
    layout: Layout?,
    onChange: (Layout) -> Unit,
    floorTarget: FloorTarget?,
    onTargetChange: (FloorTarget) -> Unit,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var target by remember { mutableIntStateOf(0) }   // 0 = 테이블 옮기기, 1 = 벽 맞추기 (방 외곽선 꼭짓점, #26)
    // 되돌리기: 편집 한 번(끌기 1회, 시트 1회, 회전 1회) 전의 배치. 새 스캔이면 비움
    var history by remember(layout?.floorId) { mutableStateOf(listOf<Layout>()) }
    var fitCount by remember { mutableIntStateOf(0) }              // 바뀌면 탑뷰가 배치 전체를 다시 맞춤
    var editingId by remember { mutableStateOf<String?>(null) }   // 편집 시트의 테이블
    var rotateBase by remember { mutableStateOf<Layout?>(null) }  // 회전 모드: 들어올 때의 배치
    var quarter by remember { mutableIntStateOf(0) }               // 회전 모드: 90° 몇 번
    var fine by remember { mutableFloatStateOf(0f) }               // 회전 모드: 미세 조정 (-45~45°)

    fun push(before: Layout) { history = history + before }
    fun commit(before: Layout, after: Layout) { push(before); onChange(after) }
    fun previewRotation(base: Layout) = onChange(rotateLayout(base, quarter * 90f + fine))
    val editing = layout?.tables?.firstOrNull { it.id == editingId }

    // #15: 서버 저장·불러오기
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }                  // 저장 중에는 편집을 막음 (끝나면 저장한 배치로 바뀌고 화면을 떠남)
    var message by remember { mutableStateOf<String?>(null) }         // 저장·불러오기 실패 문구
    var confirmReset by remember { mutableStateOf(false) }            // 다시 스캔한 층 저장 전 확인
    var loading by remember { mutableStateOf(layout == null) }

    // 보정할 배치가 없으면 서버 배치를 불러옴 (저장 안 한 로컬 편집이 있으면 그걸 그대로 씀)
    LaunchedEffect(Unit) {
        val existing = floorTarget as? FloorTarget.Existing
        if (layout != null || existing == null) {
            loading = false
            return@LaunchedEffect
        }
        try {
            Server.layouts.load(existing.id)?.let(onChange)
        } catch (e: ApiException) {
            message = e.displayMessage()
        } finally {
            loading = false
        }
    }

    // checkReset = true면 먼저 초기화 확인이 필요한지 보고, 필요하면 확인 창만 띄우고 멈춤
    fun save(checkReset: Boolean) {
        val current = layout ?: return
        val t = floorTarget ?: return
        if (saving) return
        saving = true
        message = null
        scope.launch {
            try {
                if (checkReset && Server.layouts.resetsStatus(current, t)) {
                    confirmReset = true   // [바꾸기]면 save(false), [취소]면 저장 안 함
                    return@launch
                }
                val saved = Server.layouts.save(current, t)
                onChange(saved)
                // 새 층은 이제 서버에 있음 → 다시 저장해도 또 추가되지 않게
                onTargetChange(FloorTarget.Existing(saved.floorId, t.name))
                onSaved()
            } catch (e: ApiException) {
                message = e.displayMessage()
            } finally {
                saving = false
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("이 층 상태가 초기화돼요") },
            text = { Text("다시 스캔한 배치로 바꾸면 이 층 테이블의 손님 상태가 모두 '빈자리'로 돌아가요.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; save(checkReset = false) }) { Text("바꾸기", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("취소", color = Ink) } },
            containerColor = Bg,
        )
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(Glyph.BACK, "뒤로", onBack)
            // 회전 모드 중에는 [완료] 전이라 저장 안 함
            val canSave = layout != null && !saving && rotateBase == null
            Box(
                Modifier
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(if (canSave) Brown else Brown.copy(alpha = 0.35f))
                    .clickable(enabled = canSave) { save(checkReset = true) }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (saving) "저장 중…" else "저장", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
        message?.let { Text(it, Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp), color = Danger, fontSize = 14.sp) }

        // 편집 시트가 열려 있으면 제목·모드를 접어 배치도를 위로 올림 (시트에 가려지지 않게, 탑뷰 포커스와 같은 길이)
        AnimatedVisibility(
            visible = editing == null,
            enter = expandVertically(tween(FOCUS_ANIM_MS)) + fadeIn(tween(FOCUS_ANIM_MS)),
            exit = shrinkVertically(tween(FOCUS_ANIM_MS)) + fadeOut(tween(FOCUS_ANIM_MS)),
        ) {
            Column {
                PageTitle(
                    floorTarget?.let { "${it.name} 배치 보정" } ?: "배치 보정",
                    when {
                        rotateBase != null -> "매장 입구가 화면 아래쪽에 오도록 돌려 주세요"
                        target == 0 -> "끌어서 이동 · 탭해서 편집 · 길게 눌러 삭제"
                        else -> "방 모서리 점을 끌어서 실제 벽 위치에 맞춰 주세요"
                    },
                    Modifier.padding(top = 16.dp),
                )

                // 편집 모드 (명사 대신 동작으로: 테이블 옮기기 / 벽 맞추기)
                Row(
                    Modifier
                        .padding(start = 20.dp, top = 16.dp)
                        .border(1.5.dp, Line, CircleShape)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf("테이블 옮기기", "벽 맞추기").forEachIndexed { i, label ->
                        val selected = i == target
                        Box(
                            Modifier
                                .height(38.dp)
                                .clip(CircleShape)
                                .background(if (selected) Brown else Color.Transparent)
                                .clickable { target = i }
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = if (selected) Color.White else Sub,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }

        // 탑뷰 배치도 (남는 높이를 모두 씀)
        Box(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = 14.dp)
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (layout == null) {
                Text(if (loading) "배치를 불러오는 중…" else "매장을 스캔하면 배치가 여기에 보여요", fontSize = 14.sp, color = Sub)
            } else {
                // 벽 맞추기·회전 모드에서는 테이블·의자가 끌리지 않게 (꼭짓점 편집은 #26)
                TopView(
                    layout, onChange, Modifier.fillMaxSize(),
                    tablesMovable = target == 0 && rotateBase == null && !saving,
                    fitKey = "${layout.floorId}/$fitCount",
                    focusId = editing?.id,
                    onEditStart = { push(layout) },
                    onTableTap = { id -> push(layout); editingId = id },
                    onDelete = { id -> commit(layout, removeChair(removeTable(layout, id), id)) },
                )
                Text(
                    "두 손가락으로 확대·축소",
                    Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 14.dp),
                    fontSize = 12.sp, color = Sub,
                )
            }
        }

        val base = rotateBase
        if (layout != null && base != null) {
            // 회전 모드: 90° 단위 + 미세 조정. 미리보기는 들어올 때의 배치에서 매번 새로 계산 (오차 누적 없음)
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("미세 조정 ${fine.roundToInt()}°", Modifier.weight(1f), fontSize = 14.sp, color = Ink)
                    PillTab("90° 회전", false) { quarter = (quarter + 1) % 4; previewRotation(base); fitCount++ }
                    Box(Modifier.size(8.dp))
                    PillTab("완료", true) {
                        // 결국 안 돌렸으면 원래 배치로, 되돌리기 단계도 남기지 않음
                        if (normalizeDegrees(quarter * 90f + fine) == 0f) {
                            history = history.dropLast(1)
                            onChange(base)
                        }
                        rotateBase = null
                        fitCount++
                    }
                }
                Slider(
                    value = fine,
                    onValueChange = { fine = it.roundToInt().toFloat(); previewRotation(base) },
                    valueRange = -45f..45f,
                    steps = 89,
                    colors = SliderDefaults.colors(thumbColor = Brown, activeTrackColor = Brown, inactiveTrackColor = Line),
                )
            }
        } else {
            // 도구
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
            ) {
                val canEdit = layout != null && target == 0 && !saving
                listOf(
                    Triple(Glyph.PLUS, "테이블 추가", canEdit),
                    Triple(Glyph.ROTATE, "배치 회전", canEdit),
                    Triple(Glyph.UNDO, "되돌리기", history.isNotEmpty() && !saving),
                ).forEach { (glyph, label, enabled) ->
                    Column(
                        Modifier
                            .clickable(enabled = enabled) {
                                val l = layout ?: return@clickable
                                when (glyph) {
                                    Glyph.PLUS -> {
                                        // 배치 가운데에 놓고 바로 편집 시트
                                        val pts = l.points()
                                        val cx = if (pts.isEmpty()) 1f else (pts.minOf { it.first } + pts.maxOf { it.first }) / 2
                                        val cz = if (pts.isEmpty()) 1f else (pts.minOf { it.second } + pts.maxOf { it.second }) / 2
                                        val added = addTable(l, cx, cz)
                                        commit(l, added)
                                        editingId = added.tables.last().id
                                    }
                                    Glyph.ROTATE -> { push(l); rotateBase = l; quarter = 0; fine = 0f }
                                    else -> {
                                        val prev = history.last()
                                        history = history.dropLast(1)
                                        if (prev.floor != l.floor) fitCount++   // 회전을 되돌렸으면 다시 맞춤
                                        onChange(prev)
                                    }
                                }
                            }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                            LineIcon(glyph, if (enabled) Ink else Line)
                        }
                        Text(label, fontSize = 12.sp, color = if (enabled) Brown else Sub)
                    }
                }
            }
        }
    }

    // 편집 시트: 바꾸는 대로 바로 반영. 열 때 되돌리기 단계 1개 (테이블 추가는 추가 단계에 포함),
    // 아무것도 안 바꾸고 닫으면 그 단계는 지움
    if (layout != null && editing != null) {
        TableSheet(
            table = editing,
            onChange = { onChange(updateTable(layout, it)) },
            onDelete = { onChange(removeTable(layout, editing.id)); editingId = null },
            onDismiss = {
                if (history.lastOrNull() == layout) history = history.dropLast(1)
                editingId = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableSheet(table: TableItem, onChange: (TableItem) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    // 배경을 어둡게 하지 않음: 위로 올린 배치도에서 고치는 테이블이 그대로 보이게
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Bg, scrimColor = Color.Transparent) {
        // 미리보기가 들어가 작은 화면에서는 아래(회전·삭제)가 잘리므로 스크롤
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("테이블 편집", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            OutlinedTextField(
                value = table.name,
                onValueChange = { onChange(table.copy(name = it)) },
                label = { Text("이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillTab("사각", table.shape == "RECT") { onChange(table.copy(shape = "RECT")) }
                PillTab("원형", table.shape == "ROUND") { onChange(table.copy(shape = "ROUND")) }
            }
            // 크기 0.1m 단위 (0.3~3m = 28칸)
            SheetSlider("가로 ${"%.1f".format(table.width)}m", table.width, TABLE_SIZE_MIN..TABLE_SIZE_MAX, 26) {
                onChange(table.copy(width = (it * 10).roundToInt() / 10f))
            }
            SheetSlider("세로 ${"%.1f".format(table.depth)}m", table.depth, TABLE_SIZE_MIN..TABLE_SIZE_MAX, 26) {
                onChange(table.copy(depth = (it * 10).roundToInt() / 10f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SheetSlider("회전 ${table.rotation.roundToInt()}°", table.rotation, 0f..359f, 358) {
                        onChange(table.copy(rotation = it.roundToInt().toFloat()))
                    }
                }
                Box(Modifier.size(8.dp))
                PillTab("90°", false) { onChange(table.copy(rotation = normalizeDegrees(table.rotation.roundToInt() + 90f))) }
            }
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).border(1.5.dp, Danger, CircleShape).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) { Text("테이블 삭제", color = Danger, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SheetSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onValueChange: (Float) -> Unit) {
    Column {
        Text(label, fontSize = 14.sp, color = Sub)
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(thumbColor = Brown, activeTrackColor = Sage, inactiveTrackColor = Line),
        )
    }
}
