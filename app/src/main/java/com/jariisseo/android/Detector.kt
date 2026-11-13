package com.jariisseo.android

import android.content.Context
import android.media.Image
import android.os.SystemClock
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

/**
 * 인식 결과 1개. 좌표는 ARCore CPU 카메라 이미지(센서 방향, 가로 640x480 등)의 픽셀 기준.
 * 화면 좌표로의 변환은 ArRenderer가 frame.transformCoordinates2d로 함.
 */
data class Detection(
    val label: String,
    val score: Float,
    val left: Float, val top: Float, val right: Float, val bottom: Float,
)

/**
 * ARCore 카메라 이미지(YUV_420_888)를 복사해 둔 것.
 * Image는 빨리 close()해야 해서, 바이트만 복사해 두고 백그라운드 스레드에서 처리.
 */
class YuvFrame(
    val width: Int, val height: Int,
    val y: ByteArray, val yRowStride: Int,
    val u: ByteArray, val v: ByteArray, val uvRowStride: Int, val uvPixelStride: Int,
) {
    companion object {
        fun from(image: Image): YuvFrame {
            val (yP, uP, vP) = Triple(image.planes[0], image.planes[1], image.planes[2])
            return YuvFrame(
                image.width, image.height,
                yP.buffer.copyBytes(), yP.rowStride,
                uP.buffer.copyBytes(), vP.buffer.copyBytes(), uP.rowStride, uP.pixelStride,
            )
        }

        private fun ByteBuffer.copyBytes(): ByteArray {
            val b = duplicate()
            b.rewind()
            return ByteArray(b.remaining()).also { b.get(it) }
        }
    }
}

/** YOLO 모델을 불러와서 실행하는 클래스. ⚠️ 한 스레드에서만 사용할 것 (Interpreter는 스레드 안전하지 않음) */
class Detector(context: Context) {

    private val interpreter: Interpreter = Interpreter(
        loadModel(context, "yolov8n.tflite"),
        Interpreter.Options().setNumThreads(4)   // CPU 코어 4개 사용
    )

    // 입력: [1, 3, 320, 320] (NCHW) → R 평면, G 평면, B 평면 순서로 채움
    private val inputPixels = FloatArray(3 * SIZE * SIZE)
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(4 * inputPixels.size).order(ByteOrder.nativeOrder())
    // 출력: [1, 84, 2100] → 84 = 박스 4개 값(cx, cy, w, h) + 클래스 80개 점수, 2100 = 후보 박스 수
    private val output = Array(1) { Array(84) { FloatArray(NUM_CANDIDATES) } }

    var lastInferenceMs = 0L
        private set
    var coordMode = "?"   // 모델 출력 좌표가 0~1(정규화)인지 0~320(픽셀)인지
        private set

    fun describe(): String {
        val input = interpreter.getInputTensor(0)
        val out = interpreter.getOutputTensor(0)
        return "입력: ${input.shape().contentToString()} ${input.dataType()}\n" +
               "출력: ${out.shape().contentToString()} ${out.dataType()}"
    }

    fun detect(frame: YuvFrame): List<Detection> {
        // ① 전처리: 카메라 이미지 → 세우기(90° 회전) → 비율 유지 축소 + 회색 여백(letterbox) → RGB 0~1
        val uprightW = frame.height          // 센서 이미지는 가로라서 90° 돌리면 가로/세로가 바뀜
        val uprightH = frame.width
        val scale = min(SIZE / uprightW.toFloat(), SIZE / uprightH.toFloat())
        val padX = (SIZE - uprightW * scale) / 2f
        val padY = (SIZE - uprightH * scale) / 2f
        fillInput(frame, scale, padX, padY)

        // ② 추론
        inputBuffer.rewind()
        inputBuffer.asFloatBuffer().put(inputPixels)
        val start = SystemClock.elapsedRealtime()
        interpreter.run(inputBuffer, output)
        lastInferenceMs = SystemClock.elapsedRealtime() - start

        // ③ 후처리: 후보 2100개 → 점수 필터 → 중복 제거(NMS) → 카메라 이미지 좌표로 되돌리기
        return postprocess(frame, scale, padX, padY)
    }

    /** 모델 입력 픽셀 하나하나에 대해, 대응하는 카메라 픽셀을 찾아 YUV→RGB 변환 (최근접 샘플링) */
    private fun fillInput(f: YuvFrame, scale: Float, padX: Float, padY: Float) {
        val plane = SIZE * SIZE
        for (my in 0 until SIZE) {
            for (mx in 0 until SIZE) {
                val idx = my * SIZE + mx
                // 모델 좌표 → 세운 이미지 좌표
                val ux = ((mx - padX) / scale).toInt()
                val uy = ((my - padY) / scale).toInt()
                if (ux < 0 || ux >= f.height || uy < 0 || uy >= f.width) {
                    inputPixels[idx] = PAD; inputPixels[plane + idx] = PAD; inputPixels[2 * plane + idx] = PAD
                    continue
                }
                // 세운 이미지 좌표 → 센서 이미지 좌표 (시계방향 90° 회전의 역변환)
                val sx = uy
                val sy = f.height - 1 - ux

                val yVal = f.y[sy * f.yRowStride + sx].toInt() and 0xFF
                val uvIdx = (sy / 2) * f.uvRowStride + (sx / 2) * f.uvPixelStride
                val cb = (f.u[uvIdx].toInt() and 0xFF) - 128
                val cr = (f.v[uvIdx].toInt() and 0xFF) - 128

                inputPixels[idx] = clamp01((yVal + 1.402f * cr) / 255f)
                inputPixels[plane + idx] = clamp01((yVal - 0.344f * cb - 0.714f * cr) / 255f)
                inputPixels[2 * plane + idx] = clamp01((yVal + 1.772f * cb) / 255f)
            }
        }
    }

    private fun postprocess(f: YuvFrame, scale: Float, padX: Float, padY: Float): List<Detection> {
        val out = output[0]

        // 출력 좌표가 0~1인지 0~320인지 자동 판별 (변환 방식에 따라 다를 수 있음)
        var maxW = 0f
        for (i in 0 until NUM_CANDIDATES) maxW = max(maxW, out[2][i])
        val coordScale = if (maxW <= 2f) SIZE.toFloat() else 1f
        coordMode = if (coordScale == 1f) "픽셀" else "정규화"

        val candidates = mutableListOf<Detection>()
        for (i in 0 until NUM_CANDIDATES) {
            // 관심 클래스(의자·소파·테이블) 중 가장 높은 점수
            var bestScore = 0f
            var bestLabel = ""
            for ((cls, label) in TARGETS) {
                val s = out[4 + cls][i]
                if (s > bestScore) { bestScore = s; bestLabel = label }
            }
            if (bestScore < SCORE_THRESHOLD) continue

            // 모델 좌표(320x320, 중심+크기) → 세운 이미지 좌표(모서리)
            val cx = out[0][i] * coordScale
            val cy = out[1][i] * coordScale
            val w = out[2][i] * coordScale
            val h = out[3][i] * coordScale
            val ul = ((cx - w / 2) - padX) / scale
            val ur = ((cx + w / 2) - padX) / scale
            val ut = ((cy - h / 2) - padY) / scale
            val ub = ((cy + h / 2) - padY) / scale

            // 세운 이미지 → 센서 이미지 (sx = uy, sy = H-1-ux)
            candidates += Detection(
                label = bestLabel, score = bestScore,
                left = ut, right = ub,
                top = f.height - 1 - ur, bottom = f.height - 1 - ul,
            )
        }
        return nms(candidates)
    }

    /** NMS: 같은 물체에 겹쳐 나온 박스 중 점수 가장 높은 것만 남김 (클래스별) */
    private fun nms(boxes: List<Detection>): List<Detection> {
        val result = mutableListOf<Detection>()
        for (box in boxes.sortedByDescending { it.score }) {
            if (result.none { it.label == box.label && iou(it, box) > IOU_THRESHOLD }) result += box
        }
        return result
    }

    private fun iou(a: Detection, b: Detection): Float {
        val iw = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val ih = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val inter = iw * ih
        val union = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - inter
        return if (union <= 0f) 0f else inter / union
    }

    fun close() = interpreter.close()

    private fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v

    /** assets의 파일을 압축 해제 없이 메모리에 바로 매핑 */
    private fun loadModel(context: Context, name: String): MappedByteBuffer {
        context.assets.openFd(name).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }

    companion object {
        const val SIZE = 320
        const val NUM_CANDIDATES = 2100            // 40x40 + 20x20 + 10x10
        const val SCORE_THRESHOLD = 0.4f
        const val IOU_THRESHOLD = 0.5f
        const val PAD = 114f / 255f                // YOLO 학습 때 쓰는 회색 여백
        // COCO 클래스 번호 → 우리가 관심 있는 것만
        val TARGETS = mapOf(56 to "의자", 57 to "소파", 60 to "테이블")
    }
}
