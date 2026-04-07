package app.subconsciously.helper

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.subconsciously.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class WeatherWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val DEFAULT_LAT = 40.7128
        private const val DEFAULT_LON = -74.0060
        private const val PREF_WEATHER = "weather_cache"
        private const val KEY_TEMP = "temp"
        private const val KEY_HUMIDITY = "humidity"
        private const val KEY_VISIBILITY = "visibility_m"
        private const val KEY_AQI = "aqi"
        private const val KEY_CODE = "weather_code"
        private const val KEY_TS = "cache_ts"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_LOCATION_NAME = "location_name"
    }

    private val prefs = Prefs(applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!prefs.showWeatherWidget) return@withContext Result.success()

        val coords = getWeatherCoordinates() ?: return@withContext Result.failure()
        val (lat, lon) = coords

        try {
            val info = fetchWeatherInfo(lat, lon) ?: return@withContext Result.retry()
            cacheWeatherInfo(info, lat, lon)
            
            getLocationName(lat, lon)?.let {
                cacheLocationName(it)
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private data class WeatherInfo(
        val temp: Double,
        val humidity: Int,
        val visibilityMeters: Double,
        val aqi: Int,
        val weatherCode: Int
    )

    private fun launcherPrefs(): SharedPreferences =
        applicationContext.getSharedPreferences(PREF_WEATHER, Context.MODE_PRIVATE)

    private fun hasLocationPermission(): Boolean {
        val ctx = applicationContext
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun getWeatherCoordinates(): Pair<Double, Double>? {
        if (hasLocationPermission()) {
            try {
                val lm = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val loc = getLastKnownLocation(lm)
                if (loc != null) return loc.latitude to loc.longitude
            } catch (_: SecurityException) {}
        }
        val sp = launcherPrefs()
        if (sp.contains(KEY_TS)) {
            val lat = sp.getFloat(KEY_LAT, DEFAULT_LAT.toFloat()).toDouble()
            val lon = sp.getFloat(KEY_LON, DEFAULT_LON.toFloat()).toDouble()
            return lat to lon
        }
        return null
    }

    private fun getLastKnownLocation(lm: LocationManager): android.location.Location? {
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            try {
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null) return loc
            } catch (_: Exception) {}
        }
        return null
    }

    private fun fetchWeatherInfo(lat: Double, lon: Double): WeatherInfo? {
        val forecastUrl = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,visibility"
        val aqiUrl = "https://air-quality-api.open-meteo.com/v1/air-quality" +
                "?latitude=$lat&longitude=$lon" +
                "&current=european_aqi"

        val forecastJson = httpGet(forecastUrl) ?: return null
        val current = forecastJson.getJSONObject("current")
        val temp = current.getDouble("temperature_2m")
        val humidity = current.getInt("relative_humidity_2m")
        val vis = current.getDouble("visibility")
        val code = current.getInt("weather_code")

        var aqi = 0
        try {
            val aqiJson = httpGet(aqiUrl)
            aqi = aqiJson?.getJSONObject("current")?.getInt("european_aqi") ?: 0
        } catch (_: Exception) {}

        return WeatherInfo(temp, humidity, vis, aqi, code)
    }

    private fun httpGet(url: String): JSONObject? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        return try {
            if (conn.responseCode == 200) JSONObject(conn.inputStream.bufferedReader().readText())
            else null
        } finally {
            conn.disconnect()
        }
    }

    private fun cacheWeatherInfo(info: WeatherInfo, lat: Double, lon: Double) {
        launcherPrefs().edit()
            .putFloat(KEY_TEMP, info.temp.toFloat())
            .putInt(KEY_HUMIDITY, info.humidity)
            .putFloat(KEY_VISIBILITY, info.visibilityMeters.toFloat())
            .putInt(KEY_AQI, info.aqi)
            .putInt(KEY_CODE, info.weatherCode)
            .putLong(KEY_TS, System.currentTimeMillis())
            .putFloat(KEY_LAT, lat.toFloat())
            .putFloat(KEY_LON, lon.toFloat())
            .apply()
    }

    private fun getLocationName(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(applicationContext, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.let { addr ->
                addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: addr.countryName
            }
        } catch (_: Exception) { null }
    }

    private fun cacheLocationName(name: String) {
        launcherPrefs().edit().putString(KEY_LOCATION_NAME, name).apply()
    }
}
