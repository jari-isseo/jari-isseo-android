package com.jariisseo.android

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** ARCore가 GPU 텍스처에 넣어 둔 카메라 영상을 화면 전체에 그린다. */
class BackgroundRenderer {

    var textureId = -1
        private set          // 밖에서 읽기만 가능

    private var program = 0
    private var positionAttrib = 0
    private var texCoordAttrib = 0

    // 화면 전체를 덮는 사각형의 꼭짓점 4개 (x, y). -1~1이 화면 끝
    private val quadCoords = floatBufferOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    // 각 꼭짓점에 카메라 영상의 어느 부분을 붙일지. ARCore가 채워 줌
    private val texCoords = floatBufferOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

    /** GL 스레드에서 한 번 호출: 텍스처와 셰이더 준비 */
    fun createOnGlThread() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        val target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES   // 카메라 영상 전용 텍스처 종류
        GLES20.glBindTexture(target, textureId)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordAttrib = GLES20.glGetAttribLocation(program, "a_TexCoord")
    }

    /** 매 프레임 호출: 카메라 영상 그리기 */
    fun draw(frame: Frame) {
        // 화면 크기나 회전이 바뀌면 영상이 찌그러지지 않게 좌표를 다시 계산
        if (frame.hasDisplayGeometryChanged()) {
            quadCoords.position(0)
            texCoords.position(0)
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quadCoords,
                Coordinates2d.TEXTURE_NORMALIZED, texCoords
            )
        }
        if (frame.timestamp == 0L) return   // 아직 카메라 프레임이 안 들어옴

        quadCoords.position(0)
        texCoords.position(0)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUseProgram(program)

        GLES20.glVertexAttribPointer(positionAttrib, 2, GLES20.GL_FLOAT, false, 0, quadCoords)
        GLES20.glVertexAttribPointer(texCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, texCoords)
        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glEnableVertexAttribArray(texCoordAttrib)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)   // 사각형 하나 그리기

        GLES20.glDisableVertexAttribArray(positionAttrib)
        GLES20.glDisableVertexAttribArray(texCoordAttrib)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun createProgram(vertexSrc: String, fragmentSrc: String): Int {
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, loadShader(GLES20.GL_VERTEX_SHADER, vertexSrc))
        GLES20.glAttachShader(program, loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc))
        GLES20.glLinkProgram(program)
        return program
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) error("셰이더 컴파일 실패: ${GLES20.glGetShaderInfoLog(shader)}")
        return shader
    }

    // Java의 static 영역과 같음
    companion object {
        // 꼭짓점 셰이더: 각 꼭짓점 위치를 그대로 통과
        private const val VERTEX_SHADER = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        // 픽셀 셰이더: 각 픽셀 색 = 카메라 영상의 해당 위치 색
        // (#extension은 셰이더 맨 앞에 와야 해서 trimIndent 없이 첫 줄에 둠)
        private const val FRAGMENT_SHADER = """#extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, v_TexCoord);
            }
        """

        private fun floatBufferOf(vararg values: Float): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(values); position(0) }
    }
}
