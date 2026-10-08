package com.efreedom.mutter.feature.compose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.efreedom.mutter.domain.model.LetterPhoto
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** 업로드 준비된 사진 — 줄인 JPEG과 그 픽셀 크기(paragraphs의 width/height). */
class ProcessedPhoto(val jpeg: ByteArray, val width: Int, val height: Int)

/**
 * 고른 사진을 계약대로 줄인다: 긴 변 2048px, JPEG 품질 80, EXIF 회전 반영.
 *
 * EXIF를 직접 적용하는 이유: BitmapFactory는 EXIF 방향을 무시해 세로 사진이 눕는다. 회전된 픽셀로
 * 새 JPEG을 만들면 EXIF가 사라지므로 어느 클라이언트(웹 <img>, iOS)에서도 같은 방향으로 보인다.
 * 블로킹 IO·디코드라 호출부가 백그라운드 디스패처에서 부른다.
 */
object PhotoProcessor {

    fun process(context: Context, uri: Uri): ProcessedPhoto {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "이미지를 읽을 수 없다" }

        // 1차: 2의 거듭제곱 다운샘플로 메모리 절약(긴 변이 2048 아래로 내려가지 않는 선까지).
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LetterPhoto.MAX_LONG_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("이미지를 디코드할 수 없다")

        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        // 2차: 긴 변을 정확히 2048 이하로 + EXIF 회전/반전을 한 번의 Matrix로.
        val scale = minOf(1f, LetterPhoto.MAX_LONG_SIDE.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            applyExif(orientation)
        }
        val output = if (matrix.isIdentity) {
            decoded
        } else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it !== decoded) decoded.recycle() }
        }

        val jpeg = ByteArrayOutputStream().use { stream ->
            output.compress(Bitmap.CompressFormat.JPEG, LetterPhoto.JPEG_QUALITY, stream)
            stream.toByteArray()
        }
        val result = ProcessedPhoto(jpeg, output.width, output.height)
        output.recycle()
        return result
    }

    private fun Matrix.applyExif(orientation: Int) {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        }
    }
}

