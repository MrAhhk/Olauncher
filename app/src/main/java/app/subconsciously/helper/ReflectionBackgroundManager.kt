package app.subconsciously.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import app.subconsciously.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches calm wallpaper-style images from Pexels (fallback: Picsum), caches to disk.
 * Runtime pick is sync from local cache → 0 network latency on overlay show.
 *
 * - Target cache size: [TARGET_CACHE_COUNT] files
 * - Refill triggered when cache has fewer than [REFILL_THRESHOLD]
 * - Images resized to ~720px wide, JPG, quality 80 → ~100–200 KB each
 */
object ReflectionBackgroundManager {

    private const val TAG = "ReflectionBg"

    private const val TARGET_CACHE_COUNT = 15
    private const val REFILL_WHEN_AT_OR_BELOW = 5
    private const val TARGET_WIDTH_PX = 720
    private const val JPEG_QUALITY = 80
    private const val CACHE_DIR_NAME = "reflection_bg"

    private val QUERY_POOL = listOf(
        "minimal sky",
        "fog",
        "mist",
        "soft horizon",
        "empty landscape",
        "calm water",
        "overcast",
        "blurred nature",
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val refillMutex = Mutex()

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, CACHE_DIR_NAME).apply { if (!exists()) mkdirs() }

    private fun listCachedFiles(context: Context): List<File> =
        cacheDir(context).listFiles { f -> f.isFile && f.length() > 0 }?.toList().orEmpty()

    /** Sync pick from disk cache. Returns null if cache empty. Caller must handle null → solid color. */
    fun pickRandomFile(context: Context): File? = listCachedFiles(context).randomOrNull()

    /** Kick off refill if cache is low. Call on app start, and after picking an image. */
    fun ensureCached(context: Context) {
        val appCtx = context.applicationContext
        scope.launch {
            refillMutex.withLock {
                val currentCount = listCachedFiles(appCtx).size
                if (currentCount > REFILL_WHEN_AT_OR_BELOW) {
                    Log.d(TAG, "cache ok ($currentCount files), skip refill")
                    return@withLock
                }
                val needed = TARGET_CACHE_COUNT - currentCount
                Log.d(TAG, "refill starting, need $needed more (have $currentCount)")
                refill(appCtx, needed)
                Log.d(TAG, "refill done, now have ${listCachedFiles(appCtx).size} files")
            }
        }
    }

    private fun refill(context: Context, needed: Int) {
        val urls = fetchImageUrls(needed) ?: return
        var savedCount = 0
        for (url in urls) {
            if (savedCount >= needed) break
            val bytes = downloadBytes(url) ?: continue
            val resized = decodeAndResize(bytes) ?: continue
            if (writeJpeg(context, resized)) {
                savedCount++
                resized.recycle()
            }
        }
    }

    private fun fetchImageUrls(needed: Int): List<String>? {
        val pexels = fetchPexelsUrls(needed)
        if (!pexels.isNullOrEmpty()) return pexels
        Log.w(TAG, "Pexels failed or empty, falling back to Picsum")
        return fetchPicsumUrls(needed)
    }

    private fun fetchPexelsUrls(needed: Int): List<String>? {
        val key = BuildConfig.PEXELS_API_KEY
        if (key.isBlank()) {
            Log.w(TAG, "PEXELS_API_KEY not set, skipping Pexels")
            return null
        }
        val query = QUERY_POOL.random()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val perPage = (needed + 2).coerceAtMost(20)
        val url = "https://api.pexels.com/v1/search?query=$encoded&per_page=$perPage&orientation=portrait"
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Authorization", key)
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "Pexels HTTP $code")
                conn.disconnect()
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val photos = JSONObject(body).optJSONArray("photos") ?: return null
            val out = mutableListOf<String>()
            for (i in 0 until photos.length()) {
                val src = photos.getJSONObject(i).optJSONObject("src") ?: continue
                val medium = src.optString("medium").ifBlank { src.optString("large") }
                if (medium.isNotBlank()) out.add(medium)
            }
            out
        } catch (t: Throwable) {
            Log.w(TAG, "Pexels fetch error: ${t.message}")
            null
        }
    }

    private fun fetchPicsumUrls(needed: Int): List<String> {
        // Picsum is deterministic by seed; randomize per-call to avoid duplicates.
        return (0 until needed).map {
            val seed = System.nanoTime().toString(36) + "_" + it
            "https://picsum.photos/seed/$seed/720/1280?blur=2"
        }
    }

    private fun downloadBytes(urlStr: String): ByteArray? {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "download HTTP $code for $urlStr")
                conn.disconnect()
                return null
            }
            val bytes = conn.inputStream.readBytes()
            conn.disconnect()
            bytes
        } catch (t: Throwable) {
            Log.w(TAG, "download error: ${t.message}")
            null
        }
    }

    private fun decodeAndResize(bytes: ByteArray): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            val srcW = boundsOpts.outWidth
            if (srcW <= 0) return null
            var sample = 1
            while (srcW / (sample * 2) >= TARGET_WIDTH_PX) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
            if (decoded.width <= TARGET_WIDTH_PX) return decoded
            val ratio = TARGET_WIDTH_PX.toFloat() / decoded.width.toFloat()
            val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(decoded, TARGET_WIDTH_PX, targetH, true)
            if (scaled != decoded) decoded.recycle()
            scaled
        } catch (t: Throwable) {
            Log.w(TAG, "decode error: ${t.message}")
            null
        }
    }

    private fun writeJpeg(context: Context, bitmap: Bitmap): Boolean {
        val file = File(cacheDir(context), "bg_${System.nanoTime()}.jpg")
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "write error: ${t.message}")
            file.delete()
            false
        }
    }
}
