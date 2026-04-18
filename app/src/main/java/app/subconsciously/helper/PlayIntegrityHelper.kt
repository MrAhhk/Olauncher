package app.subconsciously.helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.subconsciously.BuildConfig
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

sealed class DeviceIntegrityResult {
    data object MeetsDeviceIntegrity : DeviceIntegrityResult()
    data object DoesNotMeetDeviceIntegrity : DeviceIntegrityResult()
    data object NotConfigured : DeviceIntegrityResult()
    data class Error(val message: String) : DeviceIntegrityResult()
}

object PlayIntegrityHelper {
    private val mainHandler = Handler(Looper.getMainLooper())

    // Cached provider — warm-up is done once per process; reused for subsequent requests.
    @Volatile
    private var tokenProvider: StandardIntegrityManager.StandardIntegrityTokenProvider? = null

    fun checkDeviceIntegrity(
        context: Context,
        onResult: (DeviceIntegrityResult) -> Unit
    ) {
        fun emit(result: DeviceIntegrityResult) {
            mainHandler.post { onResult(result) }
        }

        val projectNumberString = BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER
        val backendUrl = BuildConfig.INTEGRITY_BACKEND_URL.trim().trimEnd('/')
        val projectNumber = projectNumberString.toLongOrNull()
        if (projectNumber == null || projectNumber <= 0L || backendUrl.isEmpty()) {
            emit(DeviceIntegrityResult.NotConfigured)
            return
        }

        Thread {
            val nonce = fetchNonce("$backendUrl/integrity/nonce")
            if (nonce.isNullOrBlank()) {
                emit(DeviceIntegrityResult.Error("Unable to fetch nonce"))
                return@Thread
            }

            val existingProvider = tokenProvider
            if (existingProvider != null) {
                requestToken(existingProvider, nonce, backendUrl, context.packageName, ::emit)
            } else {
                val prepareRequest = StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                    .setCloudProjectNumber(projectNumber)
                    .build()

                IntegrityManagerFactory.createStandard(context)
                    .prepareIntegrityToken(prepareRequest)
                    .addOnSuccessListener { provider ->
                        tokenProvider = provider
                        requestToken(provider, nonce, backendUrl, context.packageName, ::emit)
                    }
                    .addOnFailureListener { error ->
                        emit(DeviceIntegrityResult.Error(error.message ?: "Integrity warm-up failed"))
                    }
            }
        }.start()
    }

    private fun requestToken(
        provider: StandardIntegrityManager.StandardIntegrityTokenProvider,
        requestHash: String,
        backendUrl: String,
        packageName: String,
        emit: (DeviceIntegrityResult) -> Unit
    ) {
        val tokenRequest = StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
            .setRequestHash(requestHash)
            .build()

        provider.request(tokenRequest)
            .addOnSuccessListener { response ->
                val meetsIntegrity = verifyTokenWithBackend(
                    verifyUrl = "$backendUrl/integrity/verify",
                    packageName = packageName,
                    nonce = requestHash,
                    token = response.token()
                )
                when (meetsIntegrity) {
                    null  -> emit(DeviceIntegrityResult.Error("Integrity verification failed"))
                    true  -> emit(DeviceIntegrityResult.MeetsDeviceIntegrity)
                    false -> emit(DeviceIntegrityResult.DoesNotMeetDeviceIntegrity)
                }
            }
            .addOnFailureListener { error ->
                emit(DeviceIntegrityResult.Error(error.message ?: "Integrity check failed"))
            }
    }

    private fun fetchNonce(nonceUrl: String): String? {
        return try {
            val conn = (URL(nonceUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            conn.disconnect()
            if (code !in 200..299) return null
            JSONObject(body).optString("nonce").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun verifyTokenWithBackend(
        verifyUrl: String,
        packageName: String,
        nonce: String,
        token: String
    ): Boolean? {
        return try {
            val conn = (URL(verifyUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val payload = JSONObject()
                .put("integrityToken", token)
                .put("nonce", nonce)
                .put("packageName", packageName)
                .toString()

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(payload)
                writer.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            conn.disconnect()
            if (code !in 200..299) return null

            JSONObject(body).optBoolean("meetsDeviceIntegrity")
        } catch (_: Exception) {
            null
        }
    }
}
