package com.jariisseo.android

import kotlin.math.roundToLong
import kotlin.random.Random

private const val ID_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz"
private const val TABLE_SIZE_M = 0.8f   // 아직 실측하지 않아 고정 (샘플의 기본 테이블 크기)

/**
 * #7: 스캔 결과(매장 좌표) → 배치 JSON (server `docs/samples/sample-layout.json` 형식, version 3).
 * 스캔한 층 1개를 floors[]에 넣음. 확정된 테이블·의자만 (소파는 스키마에 없어 제외).
 * ID는 접두사 + 랜덤 4자리, 매장 전체에서 유일
 */
fun toLayoutJson(state: ArUiState, random: Random = Random.Default): String {
    val used = mutableSetOf<String>()
    fun newId(prefix: String): String {
        while (true) {
            val id = prefix + (1..4).map { ID_CHARS[random.nextInt(ID_CHARS.length)] }.joinToString("")
            if (used.add(id)) return id
        }
    }

    val floorId = newId("f-")
    val confirmed = state.mapItems.filter { it.confirmed }
    val tables = confirmed.filter { it.label == "테이블" }.mapIndexed { i, t ->
        "{\"id\": \"${newId("t-")}\", \"name\": \"${i + 1}번\", \"x\": ${num(t.x)}, \"z\": ${num(t.z)}, " +
            "\"width\": ${num(TABLE_SIZE_M)}, \"depth\": ${num(TABLE_SIZE_M)}, \"rotation\": 0, \"shape\": \"RECT\"}"
    }
    val chairs = confirmed.filter { it.label == "의자" }.map { c ->
        "{\"id\": \"${newId("c-")}\", \"x\": ${num(c.x)}, \"z\": ${num(c.z)}}"
    }
    val walls = state.walls.mapIndexed { i, (a, b) ->
        "{\"id\": \"w${i + 1}\", \"from\": ${point(a)}, \"to\": ${point(b)}}"
    }
    val polygon = state.floorPolygon.joinToString(", ", "[", "]") { point(it) }

    return """
        |{
        |  "version": 3,
        |  "unit": "m",
        |  "floors": [
        |    {
        |      "id": "$floorId", "name": "1층", "order": 0,
        |      "floor": { "polygon": $polygon },
        |      "walls": ${array(walls)},
        |      "tables": ${array(tables)},
        |      "chairs": ${array(chairs)}
        |    }
        |  ]
        |}
        |""".trimMargin()
}

private fun array(items: List<String>) =
    if (items.isEmpty()) "[]" else items.joinToString(",\n        ", "[\n        ", "\n      ]")

private fun point(p: Pair<Float, Float>) = "[${num(p.first)}, ${num(p.second)}]"

/** cm 단위로 반올림 (1.5, 0.0 처럼 Locale과 무관한 형식) */
private fun num(v: Float): String = ((v * 100.0).roundToLong() / 100.0 + 0.0).toString()
