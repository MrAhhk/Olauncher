package app.subconsciously.helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.subconsciously.BuildConfig
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
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

            val integrityManager = IntegrityManagerFactory.create(context)
            val request = IntegrityTokenRequest.builder()
                .setCloudProjectNumber(projectNumber)
                .setNonce(nonce)
                .build()

            integrityManager.requestIntegrityToken(request)
                .addOnSuccessListener { response ->
                    val meetsIntegrity = verifyTokenWithBackend(
                        verifyUrl = "$backendUrl/integrity/verify",
                        packageName = context.packageName,
                        nonce = nonce,
                        token = response.token()
                    )
                    if (meetsIntegrity == null) {
                        emit(DeviceIntegrityResult.Error("Integrity verification failed"))
                    } else if (meetsIntegrity) {
                        emit(DeviceIntegrityResult.MeetsDeviceIntegrity)
                    } else {
                        emit(DeviceIntegrityResult.DoesNotMeetDeviceIntegrity)
                    }
                }
                .addOnFailureListener { error ->
                    emit(DeviceIntegrityResult.Error(error.message ?: "Integrity check failed"))
                }
        }.start()
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
