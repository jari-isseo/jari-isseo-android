package com.jariisseo.android

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CameraRayTest {
    // fx, fy, cx, cy (640x480 이미지)
    private val k = floatArrayOf(500f, 500f, 320f, 240f)

    @Test
    fun principalPoint_looksStraightAhead() {
        assertArrayEquals(floatArrayOf(0f, 0f, -1f), cameraRay(320f, 240f, k), 1e-6f)
    }

    @Test
    fun rightAndDown_inImage_areRightAndDown_inCamera() {
        // 오른쪽으로 100px → +X 0.2, 아래로 50px → -Y 0.1
        assertArrayEquals(floatArrayOf(0.2f, -0.1f, -1f), cameraRay(420f, 290f, k), 1e-6f)
    }
}
