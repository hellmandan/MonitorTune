package com.dan.ose11p

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos

/**
 * Transparent live traffic-awareness map placed behind the MonitorTune dash.
 * Police locations are community reports supplied by Waze; speed-camera locations
 * are mapped OpenStreetMap data. Neither source guarantees completeness or accuracy.
 */
class PoliceMapBackground(private val activity: Activity) : FrameLayout(activity), LocationListener {
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val lm = activity.getSystemService(Activity.LOCATION_SERVICE) as LocationManager
    private val web = WebView(activity)
    private var lastLocation: Location? = null
    private var started = false
    private var lastCameraFetch = 0L

    private val refresh = object : Runnable {
        override fun run() {
            fetchPolice()
            if (System.currentTimeMillis() - lastCameraFetch > 6 * 60 * 60 * 1000L) fetchCameras()
            handler.postDelayed(this, 15_000L)
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        web.setBackgroundColor(Color.TRANSPARENT)
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.cacheMode = WebSettings.LOAD_DEFAULT
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.isClickable = false
        web.isFocusable = false
        web.loadUrl("file:///android_asset/police_map.html")
        addView(web, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        alpha = 0.76f
    }

    fun start() {
        if (started) return
        started = true
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ), LOCATION_REQUEST)
            return
        }
        startGps()
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            startGps()
            handler.removeCallbacks(refresh)
            handler.post(refresh)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500L, 0f, this, Looper.getMainLooper())
        } catch (_: Exception) {
            try {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f, this, Looper.getMainLooper())
            } catch (_: Exception) { }
        }
        val initial = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { null }
        initial?.let { onLocationChanged(it) }
    }

    fun stop() {
        if (!started) return
        handler.removeCallbacks(refresh)
        try { lm.removeUpdates(this) } catch (_: Exception) {}
        started = false
    }

    fun destroy() {
        stop()
        io.shutdownNow()
        web.stopLoading()
        web.loadUrl("about:blank")
        web.destroy()
    }

    override fun onLocationChanged(location: Location) {
        lastLocation = location
        val bearing = if (location.hasBearing() && location.hasSpeed() && location.speed > 2f) location.bearing.toDouble() else -1.0
        val js = "setMe(${location.latitude},${location.longitude},$bearing,${location.accuracy})"
        web.post { web.evaluateJavascript(js, null) }
        fetchPolice()
        if (System.currentTimeMillis() - lastCameraFetch > 6 * 60 * 60 * 1000L) fetchCameras()
    }

    @Deprecated("Deprecated by Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {
        web.post { web.evaluateJavascript("setStatus('GPS disabled')", null) }
    }

    private fun fetchPolice() {
        val loc = lastLocation ?: return
        if (!busy.compareAndSet(false, true)) return
        val lat = loc.latitude
        val lon = loc.longitude
        io.execute {
            try {
                val halfLat = 0.065
                val halfLon = halfLat / cos(Math.toRadians(lat)).coerceAtLeast(0.2)
                val url = "https://www.waze.com/live-map/api/georss?env=row&types=alerts&ma=200&mj=0&mu=0" +
                    "&left=${lon-halfLon}&right=${lon+halfLon}&top=${lat+halfLat}&bottom=${lat-halfLat}"
                val body = get(url, 15_000)
                val alerts = JSONObject(body).optJSONArray("alerts") ?: JSONArray()
                val out = JSONArray()
                for (i in 0 until alerts.length()) {
                    val a = alerts.optJSONObject(i) ?: continue
                    if (a.optString("type") != "POLICE") continue
                    val p = a.optJSONObject("location") ?: continue
                    val y = p.optDouble("y", Double.NaN)
                    val x = p.optDouble("x", Double.NaN)
                    if (y.isNaN() || x.isNaN()) continue
                    out.put(JSONObject()
                        .put("id", a.optString("uuid", a.optString("id", "$y,$x")))
                        .put("lat", y).put("lon", x)
                        .put("street", a.optString("street"))
                        .put("city", a.optString("city"))
                        .put("sub", a.optString("subtype"))
                        .put("ts", a.optLong("pubMillis", 0)))
                }
                web.post {
                    web.evaluateJavascript("setPolice($out)", null)
                    web.evaluateJavascript("setStatus('${out.length()} community police reports nearby • refresh 15s')", null)
                }
            } catch (e: Exception) {
                val detail = (e.message ?: e.javaClass.simpleName)
                    .replace(Regex("[^A-Za-z0-9 _-]"), "")
                    .take(48)
                web.post {
                    web.evaluateJavascript("setStatus('Police feed error: $detail')", null)
                }
            } finally { busy.set(false) }
        }
    }

    private fun fetchCameras() {
        val loc = lastLocation ?: return
        val lat = loc.latitude
        val lon = loc.longitude
        io.execute {
            try {
                val south = lat - 0.12
                val north = lat + 0.12
                val west = lon - 0.12 / cos(Math.toRadians(lat)).coerceAtLeast(0.2)
                val east = lon + 0.12 / cos(Math.toRadians(lat)).coerceAtLeast(0.2)
                val bbox = "($south,$west,$north,$east)"
                val query = "[out:json][timeout:25];node[\"highway\"=\"speed_camera\"]$bbox;out body;"
                val url = URL("https://overpass-api.de/api/interpreter")
                val c = url.openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", "MonitorTune/5.3 Android")
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                try {
                    c.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
                    if (c.responseCode != 200) throw IllegalStateException("Overpass HTTP ${c.responseCode}")
                    val els = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("elements") ?: JSONArray()
                    val out = JSONArray()
                    for (i in 0 until els.length()) {
                        val e = els.optJSONObject(i) ?: continue
                        val tags = e.optJSONObject("tags")
                        out.put(JSONObject().put("id", e.optString("id"))
                            .put("lat", e.optDouble("lat")).put("lon", e.optDouble("lon"))
                            .put("ms", tags?.optString("maxspeed").orEmpty()))
                    }
                    lastCameraFetch = System.currentTimeMillis()
                    web.post { web.evaluateJavascript("setCams($out)", null) }
                } finally { c.disconnect() }
            } catch (_: Exception) {
                // Keep the map and previously loaded police reports usable if camera data is offline.
            }
        }
    }

    private fun get(address: String, timeout: Int): String {
        val c = URL(address).openConnection() as HttpURLConnection
        c.connectTimeout = timeout
        c.readTimeout = timeout
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
        c.setRequestProperty("Referer", "https://www.waze.com/live-map")
        c.setRequestProperty("Accept", "application/json")
        return try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    companion object { const val LOCATION_REQUEST = 5317 }
}
