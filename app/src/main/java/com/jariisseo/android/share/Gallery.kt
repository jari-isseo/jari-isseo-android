package com.jariisseo.android.share

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException

/**
 * #22: PNG를 사진 앱(Pictures/자리있어)에 저장. 성공하면 true.
 * Android 9 이하는 WRITE_EXTERNAL_STORAGE 권한을 받은 뒤 불러야 함 (없으면 false)
 */
fun savePngToGallery(context: Context, bitmap: Bitmap, name: String): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/자리있어")
            put(MediaStore.Images.Media.IS_PENDING, 1)   // 다 쓰기 전엔 사진 앱에 안 보이게
        }
    }
    val uri = try {
        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    } catch (_: SecurityException) {
        null
    } ?: return false

    val written = try {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
    } catch (_: IOException) {
        false
    }
    if (!written) {
        resolver.delete(uri, null, null)
        return false
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    }
    return true
}
