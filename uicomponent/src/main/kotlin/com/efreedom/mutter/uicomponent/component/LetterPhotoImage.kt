package com.efreedom.mutter.uicomponent.component

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 편지 사진 1장 — 원본 비율을 유지해 폭을 채운다. 로딩 중·실패 시에는 같은 자리에 차분한 플레이스홀더.
 *
 * 이미지 로더 라이브러리(Coil 등)가 이 저장소에 없어 의존성을 늘리지 않고 최소 로더를 둔다:
 * 메모리 LRU 캐시 + HttpURLConnection + BitmapFactory(다운샘플). 사진은 편지당 최대 5장이라 이 정도로 충분하다.
 *
 * @param cacheKey 캐시 키. 서명 URL은 열 때마다 바뀌므로 **Storage path**를 넘긴다(업로드 전이면 사진 id).
 * @param url 서명 URL. null이면 [bytes]를 쓴다.
 * @param bytes 업로드 대기 중인 JPEG(작성 화면 미리보기).
 */
@Composable
fun LetterPhotoImage(
    cacheKey: String,
    url: String?,
    bytes: ByteArray?,
    width: Int,
    height: Int,
    placeholderColor: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 6.dp,
) {
    val bitmap by produceState(initialValue = PhotoBitmapCache.get(cacheKey), cacheKey, url, bytes) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val decoded = when {
                    bytes != null -> decodeSampled(bytes)
                    url != null -> decodeSampled(download(url))
                    else -> null
                }
                decoded?.also { PhotoBitmapCache.put(cacheKey, it) }
            }.getOrNull()
        }
    }
    // 크기를 모르는(0) 비정상 데이터도 자리를 잡도록 4:3으로 대체.
    val ratio = if (width > 0 && height > 0) width.toFloat() / height else 4f / 3f
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(shape)
            .background(placeholderColor),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** path(또는 사진 id) → 디코드된 비트맵. 앱 힙의 1/8까지(바이트 기준). */
private object PhotoBitmapCache {
    private val cache = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, bitmap: ImageBitmap) { cache.put(key, bitmap) }
}

private const val DISPLAY_MAX_SIDE = 1440

private fun download(url: String): ByteArray {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    try {
        check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
        return connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}

/** 화면 표시용으로 긴 변을 [DISPLAY_MAX_SIDE] 근처까지 2의 거듭제곱으로 다운샘플해 메모리를 아낀다. */
private fun decodeSampled(data: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= DISPLAY_MAX_SIDE) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(data, 0, data.size, options)?.asImageBitmap()
}
