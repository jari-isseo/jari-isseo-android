package com.jariisseo.android

import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * #7: 스캔 결과(매장 좌표) → 배치 JSON (server `docs/samples/sample-layout.json` 형식, version 3).
 * #13: 변환 규칙(확정 가구만·ID·이름)은 `layoutOf`로 옮기고 여기서는 직렬화만
 */
fun toLayoutJson(state: ArUiState, random: Random = Random.Default): String = toLayoutJson(layoutOf(state, random))

/** 배치 → JSON. 층 1개를 floors[]에 넣음 */
fun toLayoutJson(layout: Layout): String {
    val tables = layout.tables.map { t ->
        "{\"id\": \"${t.id}\", \"name\": \"${t.name}\", \"x\": ${num(t.x)}, \"z\": ${num(t.z)}, " +
            "\"width\": ${num(t.width)}, \"depth\": ${num(t.depth)}, \"rotation\": ${t.rotation.roundToInt()}, \"shape\": \"${t.shape}\"}"
    }
    val chairs = layout.chairs.map { c -> "{\"id\": \"${c.id}\", \"x\": ${num(c.x)}, \"z\": ${num(c.z)}}" }
    val walls = layout.walls.mapIndexed { i, (a, b) ->
        "{\"id\": \"w${i + 1}\", \"from\": ${point(a)}, \"to\": ${point(b)}}"
    }
    val polygon = layout.floor.joinToString(", ", "[", "]") { point(it) }

    return """
        |{
        |  "version": 3,
        |  "unit": "m",
        |  "floors": [
        |    {
        |      "id": "${layout.floorId}", "name": "1층", "order": 0,
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
