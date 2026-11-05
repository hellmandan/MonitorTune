package com.dan.ose11p

import android.animation.ValueAnimator
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private val ACTION_USB_PERMISSION = "com.dan.ose11p.USB_PERMISSION"
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private var link: AldlLink? = null
    private var elmLink: Elm327Link? = null
    private var lastObd: ObdLive? = null
    private var connectionMode = "ALDL"
    private var last: Sample? = null
    private var prevKnock = -1
    private var logging = false
    private var logStart = 0L
    private val logRows = ArrayList<DoubleArray>()
    private var activeLogColumns: List<String> = Sample.COLUMNS
    private var ve: VeFill? = null
    private var sp: SparkKnock? = null
    private var pendingBytes: ByteArray? = null
    private var loadTarget = ""

    private lateinit var status: TextView
    private lateinit var dot: StatusDot
    private lateinit var btnConnect: Button
    private lateinit var chkSim: CheckBox
    private lateinit var chkFill: CheckBox
    private lateinit var chkSmooth: CheckBox
    private lateinit var btnLog: Button
    private lateinit var logInfo: TextView
    private lateinit var capView: TextView
    private lateinit var veInfo: TextView
    private lateinit var spInfo: TextView
    private lateinit var tabs: Array<View>
    private lateinit var tabButtons: Array<Button>
    private lateinit var gRpm: ArcGauge
    private lateinit var gSpeed: ArcGauge
    private lateinit var gBoost: ArcGauge
    private lateinit var gCool: ArcGauge
    private lateinit var gSpark: ArcGauge
    private lateinit var gIac: ArcGauge
    private lateinit var veRpmGauge: ArcGauge
    private lateinit var veBoostGauge: ArcGauge
    private lateinit var veCoolantGauge: ArcGauge
    private lateinit var dashAfr: TextView
    private lateinit var dashMap: TextView
    private lateinit var dashTps: TextView
    private lateinit var dashKnock: TextView
    private lateinit var veGrids: Array<HeatGrid>
    private lateinit var spGrids: Array<HeatGrid>
    private val tileVals = LinkedHashMap<String, TextView>()
    private val tileDec = HashMap<String, Int>()
    private var activeTab = 0
    private var afrColor = Theme.TEXT
    private var roadDyno: RoadDyno? = null
    private var dynoRuns = mutableListOf<DynoRun>()
    private var dynoChart: DynoGraph? = null
    private var dynoInfo: TextView? = null
    private var dynoMassKg = 1700.0
    private var dynoActive = false
    private var workshopProfiles = mutableListOf<VehicleProfile>()
    private var selectedProfileIndex = 0
    private var workshopProfileLabel: TextView? = null
    private var workshopReportText: String = ""
    private var definitionImportMode = "XDF"
    private var importedDefinitionSummary = "No TunerPro definition imported this session."
    private var importedXdf: ImportedDefinition? = null
    private var v4Definition: V4VehicleDefinition? = null
    private var v4LastDecoded: V4FrameResult? = null
    private var v4Recorder = V4SessionRecorder()
    private var v4Chart = V4ChartHistory()
    private var v4Info: TextView? = null
    private var v4Values: TextView? = null
    private var v4Status: TextView? = null
    private var v4RawMonitor: TextView? = null
    private var v4ChartView: V4LiveChartView? = null
    private var v4GaugeContainer: LinearLayout? = null
    private val v4GaugeViews = linkedMapOf<String, V4MiniGaugeView>()
    private var v4Transport: V4RawTransport? = null
    private val v4Io = Executors.newSingleThreadExecutor()
    private var v4PendingConnect = false
    private var v4Polling = false
    private var v4TrendParameterId: String? = null
    private var v4PollRunnable: Runnable? = null
    private val aiMonitor = AiMonitor()
    private var aiSummary: TextView? = null
    private var aiDataStatus: TextView? = null
    private var obdLiveReadout: TextView? = null
    private var aiAlertsBox: LinearLayout? = null
    private var geminiOutput: TextView? = null
    private var geminiApiKey: String = "" // session-only; never persisted in preferences or APK
    private val geminiExecutor = Executors.newSingleThreadExecutor()
    private var logPulse: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var navScroll: HorizontalScrollView
    private lateinit var dashTab: LinearLayout
    private lateinit var tilesBox: LinearLayout
    private val tileViews = ArrayList<View>()
    private lateinit var aiPulse: AiPulseView
    private var geminiBusy = false

    // ---- alerts (v1.5)
    private lateinit var alertSounds: AlertSounds
    private lateinit var alertEngine: AlertEngine
    private lateinit var alertOverlay: AlertOverlay
    private lateinit var banner: LinearLayout
    private lateinit var bannerBg: GradientDrawable
    private lateinit var bannerIcon: TextView
    private lateinit var bannerTitle: TextView
    private lateinit var bannerDetail: TextView
    private lateinit var bannerSilence: Button
    private lateinit var btnMute: Button
    private var chkVoice: CheckBox? = null
    private var chkVib: CheckBox? = null
    private var bannerAnim: ValueAnimator? = null
    private var bannerSev = Sev.NONE
    private var navPulse: ValueAnimator? = null
    private var badgeKey = ""
    private var cardPulse: ValueAnimator? = null
    private val redCards = ArrayList<GradientDrawable>()
    private var alertSig = ""
    private var summarySev = Sev.NONE
    private var connectedAt = 0L
    private var bgAnim: ValueAnimator? = null

    // ---- file pickers
    private val openBin = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) loadBin(uri)
    }
    private val saveBin = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? -> if (uri != null) writeUri(uri) }
    private val saveCsv = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? -> if (uri != null) writeUri(uri) }
    private val saveTsv = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/tab-separated-values")
    ) { uri: Uri? -> if (uri != null) writeUri(uri) }
    private val saveWorkshopReport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? -> if (uri != null) try { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(workshopReportText) }; Toast.makeText(this, "Workshop report saved", Toast.LENGTH_LONG).show() } catch (e: Exception) { Toast.makeText(this, "Report export failed: ${e.message}", Toast.LENGTH_LONG).show() } }
    private val openTunerProDefinition = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read selected file")
            require(bytes.size <= 10 * 1024 * 1024) { "Definition file exceeds 10 MB limit" }
            val imported = if (definitionImportMode == "ADX") TunerProDefinitionImporter.importAdx(bytes) else TunerProDefinitionImporter.importXdf(bytes)
            if (definitionImportMode == "XDF") importedXdf = imported
            importedDefinitionSummary = buildString {
                appendLine("${imported.format}: ${imported.title}")
                appendLine("Recognised items: ${imported.items.size}")
                imported.items.take(100).forEach { item -> appendLine("${item.kind}: ${item.name} | address=${item.address?.let { "0x${it.toString(16)}" } ?: "unknown"} | ${item.rows} x ${item.columns} | ${item.elementBits ?: "?"}-bit | ${item.units} | ${item.equation}") }
                imported.warnings.forEach { appendLine("WARNING: $it") }
                if (imported.items.size > 100) appendLine("Only first 100 entries shown.")
            }
            AlertDialog.Builder(this).setTitle("TunerPro ${imported.format} imported")
                .setMessage(importedDefinitionSummary.take(12000)).setPositiveButton("OK", null).show()
        } catch (e: Exception) { Toast.makeText(this, "Definition import failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private val openV4Definition = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read definition")
            disconnectV4()
            v4Definition = V4MacroStore.merge(this, V4DefinitionImporter.import(bytes))
            v4Recorder = V4SessionRecorder(); v4Chart.clear(); v4LastDecoded = null
            refreshV4Definition()
            Toast.makeText(this, "MonitorTune V4 definition imported", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(this, "V4 definition import failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private val openXdfPreviewBin = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) try {
            val bin = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read BIN")
            require(bin.size <= 2 * 1024 * 1024) { "BIN exceeds 2 MB safety limit" }
            val tables = importedXdf?.items?.filter { it.kind == "TABLE" && it.elementBits in listOf(8, 16, 32) }.orEmpty()
            require(tables.isNotEmpty()) { "Import an XDF containing a supported table first" }
            AlertDialog.Builder(this).setTitle("Select XDF table")
                .setItems(tables.map { "${it.name} (${it.rows} x ${it.columns}, ${it.elementBits}-bit)" }.toTypedArray()) { _, which ->
                    val selected = tables[which]
                    val offsetInput = EditText(this).apply { hint = "Confirmed BIN file offset (decimal or 0xHEX)"; setSingleLine(true); setText(selected.address?.let { "0x${it.toString(16)}" } ?: "") }
                    AlertDialog.Builder(this).setTitle("Confirm file offset")
                        .setMessage("XDF address is not assumed to equal the BIN file offset. Confirm the correct file offset after checking address base/bank mapping.")
                        .setView(offsetInput).setPositiveButton("Preview raw values") { _, _ ->
                            val raw = offsetInput.text.toString().trim()
                            val offset = try { if (raw.startsWith("0x", true)) raw.substring(2).toLong(16) else raw.toLong() } catch (_: Exception) { -1L }
                            val preview = TunerProDefinitionImporter.previewTable(selected, bin, offset)
                            val text = buildString { preview.values.forEach { appendLine(it.joinToString("  ")) }; preview.warnings.forEach { appendLine("\nWARNING: $it") } }
                            AlertDialog.Builder(this).setTitle("${selected.name} — raw preview").setMessage(text.ifBlank { "No values available." }).setPositiveButton("Close", null).show()
                        }.setNegativeButton("Cancel", null).show()
                }.setNegativeButton("Cancel", null).show()
        } catch (e: Exception) { Toast.makeText(this, "XDF preview failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_USB_PERMISSION) {
                val ok = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (v4PendingConnect) { v4PendingConnect = false; if (ok) startV4UsbTransport() else setV4Status("USB permission denied") }
                else if (ok) startLink(false) else setStatus("USB permission denied")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Theme.BG
        window.navigationBarColor = Theme.BG
        val f = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(usbReceiver, f)
        dynoRuns = DynoStore.load(this)
        workshopProfiles = VehicleProfileStore.load(this)
        alertSounds = AlertSounds(this)
        alertEngine = AlertEngine(alertSounds)
        loadAlertPrefs()
        loadAiSettings()
        buildUi()
        handler.post(object : Runnable {
            override fun run() {
                refresh()
                handler.postDelayed(this, 250)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        link?.stopFlag = true
        elmLink?.stopFlag = true
        v4Polling = false
        v4PollRunnable?.let { handler.removeCallbacks(it) }
        try { v4Transport?.close() } catch (_: Exception) {}
        v4Transport = null
        v4Io.shutdownNow()
        bgAnim?.cancel()
        bannerAnim?.cancel()
        navPulse?.cancel()
        cardPulse?.cancel()
        alertSounds.release()
        unregisterReceiver(usbReceiver)
    }

    override fun onPause() {
        super.onPause()
        bgAnim?.pause()
    }

    override fun onResume() {
        super.onResume()
        bgAnim?.resume()
    }

    private fun loadAlertPrefs() {
        val p = getSharedPreferences("ai_monitor", MODE_PRIVATE)
        alertEngine.soundOn = p.getBoolean("snd", true)
        alertEngine.voiceOn = p.getBoolean("voice", true)
        alertEngine.vibrateOn = p.getBoolean("vib", true)
    }

    private fun saveAlertPrefs() {
        getSharedPreferences("ai_monitor", MODE_PRIVATE).edit()
            .putBoolean("snd", alertEngine.soundOn).putBoolean("voice", alertEngine.voiceOn)
            .putBoolean("vib", alertEngine.vibrateOn).apply()
    }

    // ---------------------------------------------------------------- UI helpers
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun lp(w: Int, h: Int, wt: Float = 0f, m: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h, wt)
        p.setMargins(m, m, m, m)
        return p
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radiusDp).toFloat()
        return g
    }

    private fun btn(text: String, color: Int, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.textSize = 14f
        b.setTextColor(Color.WHITE)
        b.background = rounded(color, 10)
        b.minHeight = 0
        b.minimumHeight = 0
        b.setPadding(dp(14), dp(10), dp(14), dp(10))
        b.setOnClickListener { onClick() }
        b.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).start()
            } else if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
        return b
    }

    private fun label(text: String, size: Float, color: Int): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        return t
    }

    private fun tile(key: String, name: String, dec: Int, big: Boolean): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = rounded(Theme.CARD, 12)
        box.setPadding(dp(10), dp(6), dp(10), dp(6))
        val v = TextView(this)
        v.text = "-"
        v.textSize = if (big) 30f else 20f
        v.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        v.setTextColor(Theme.TEXT)
        box.addView(label(name, 11f, Theme.MUTED))
        box.addView(v)
        tileVals[key] = v
        tileDec[key] = dec
        return box
    }

    private fun tileRow(items: List<Array<Any>>, big: Boolean): LinearLayout {
        val row = LinearLayout(this)
        for (it in items) {
            row.addView(tile(it[0] as String, it[1] as String, it[2] as Int, big), lp(0, WRAP, 1f, dp(4)))
        }
        return row
    }

    private fun wide(): Boolean = resources.configuration.screenWidthDp >= 560

    private fun hscroll(v: View): View {
        val h = HorizontalScrollView(this)
        h.isHorizontalScrollBarEnabled = false
        h.addView(v)
        return h
    }

    private fun dashMetric(caption: String): Pair<LinearLayout, TextView> {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(8), dp(8))
            background = rounded(Color.rgb(17, 27, 42), 12)
        }
        card.addView(label(caption, 10f, Theme.MUTED))
        val value = label("--", 20f, Theme.TEXT).apply {
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setSingleLine(true)
        }
        card.addView(value)
        return Pair(card, value)
    }

    private fun layoutDash() {
        val gs = arrayOf<View>(gRpm, gSpeed, gBoost, gCool, gSpark)
        for (g in gs) (g.parent as? ViewGroup)?.removeView(g)
        dashTab.removeAllViews()
        val heading = LinearLayout(this).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(4))
        }
        heading.addView(label("LIVE ENGINE", 14f, Theme.TEXT).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }, lp(0, WRAP, 1f))
        heading.addView(label("REAL-TIME TELEMETRY  •  OSE / OBD", 9f, Theme.MUTED))
        dashTab.addView(heading, lp(MATCH, WRAP, 0f, dp(3)))

        fun gaugeRow(vararg views: View) {
            val r = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER }
            views.forEach { r.addView(it, lp(0, MATCH, 1f, dp(3))) }
            dashTab.addView(r, lp(MATCH, 0, 1f, dp(3)))
        }
        if (wide()) {
            gaugeRow(gRpm, gBoost, gSpeed, gCool, gSpark)
        } else {
            gaugeRow(gRpm)
            gaugeRow(gBoost, gSpeed)
            gaugeRow(gCool, gSpark)
        }

        val afr = dashMetric("WIDEBAND AFR")
        val map = dashMetric("MANIFOLD PRESSURE")
        val tps = dashMetric("THROTTLE")
        val knock = dashMetric("KNOCK RETARD")
        dashAfr = afr.second
        dashMap = map.second
        dashTps = tps.second
        dashKnock = knock.second
        if (wide()) {
            val metrics = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER }
            for (card in listOf(afr.first, map.first, tps.first, knock.first)) metrics.addView(card, lp(0, WRAP, 1f, dp(3)))
            dashTab.addView(metrics, lp(MATCH, WRAP, 0f, dp(3)))
        } else {
            fun metricRow(a: View, b: View) {
                val row = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER }
                row.addView(a, lp(0, WRAP, 1f, dp(3)))
                row.addView(b, lp(0, WRAP, 1f, dp(3)))
                dashTab.addView(row, lp(MATCH, WRAP, 0f, dp(2)))
            }
            metricRow(afr.first, map.first)
            metricRow(tps.first, knock.first)
        }
        dashTab.addView(label("Values are live ECU readings. N/A means the connected protocol does not supply that parameter.", 9f, Theme.MUTED), lp(MATCH, WRAP, 0f, dp(2)))
    }

    private fun layoutTiles() {
        for (tv in tileViews) (tv.parent as? ViewGroup)?.removeView(tv)
        tilesBox.removeAllViews()
        val cols = if (wide()) 4 else 2
        var i = 0
        while (i < tileViews.size) {
            val row = LinearLayout(this)
            for (k in 0 until cols) {
                if (i + k < tileViews.size) row.addView(tileViews[i + k], lp(0, WRAP, 1f, dp(4)))
                else row.addView(View(this), lp(0, 1, 1f, dp(4)))
            }
            tilesBox.addView(row)
            i += cols
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        layoutDash()
        layoutTiles()
    }

    private fun scrollBoth(v: View): View {
        val h = HorizontalScrollView(this)
        h.addView(v)
        val s = ScrollView(this)
        s.addView(h)
        return s
    }

    // ---------------------------------------------------------------- UI
    private fun buildUi() {
        val frame = FrameLayout(this)
        frame.setBackgroundColor(Theme.BG)
        val bg = ImageView(this)
        bg.setImageResource(R.drawable.bg_engine)
        bg.scaleType = ImageView.ScaleType.CENTER_CROP
        frame.addView(bg, FrameLayout.LayoutParams(MATCH, MATCH))
        // slow drifting zoom on the engine-bay photo
        val kb = ValueAnimator.ofFloat(1.04f, 1.14f)
        kb.duration = 40000
        kb.repeatMode = ValueAnimator.REVERSE
        kb.repeatCount = ValueAnimator.INFINITE
        kb.addUpdateListener {
            val v = it.animatedValue as Float
            bg.scaleX = v
            bg.scaleY = v
            bg.translationX = (v - 1.09f) * 120f * resources.displayMetrics.density
        }
        kb.start()
        bgAnim = kb
        val scrim = View(this)
        scrim.setBackgroundColor(Color.argb(150, 6, 9, 15))
        frame.addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(10), dp(8), dp(10), dp(8))
        frame.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))

        // alert edge-glow overlay and top banner sit above all tabs
        alertOverlay = AlertOverlay(this)
        frame.addView(alertOverlay, FrameLayout.LayoutParams(MATCH, MATCH))
        bannerBg = GradientDrawable()
        bannerBg.cornerRadius = dp(12).toFloat()
        banner = LinearLayout(this)
        banner.orientation = LinearLayout.HORIZONTAL
        banner.gravity = android.view.Gravity.CENTER_VERTICAL
        banner.background = bannerBg
        banner.setPadding(dp(12), dp(8), dp(8), dp(8))
        bannerIcon = label("\u25B2", 26f, Color.WHITE)
        val bannerCol = LinearLayout(this)
        bannerCol.orientation = LinearLayout.VERTICAL
        bannerTitle = label("", 16f, Color.WHITE)
        bannerTitle.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        bannerDetail = label("", 12f, Color.WHITE)
        bannerDetail.maxLines = 2
        bannerDetail.ellipsize = TextUtils.TruncateAt.END
        bannerCol.addView(bannerTitle)
        bannerCol.addView(bannerDetail)
        bannerSilence = btn("SILENCE", Color.argb(110, 255, 255, 255)) { alertEngine.acknowledge(); alertTick() }
        banner.addView(bannerIcon, lp(WRAP, WRAP, 0f, dp(4)))
        banner.addView(bannerCol, lp(0, WRAP, 1f, dp(4)))
        banner.addView(bannerSilence, lp(WRAP, WRAP, 0f, dp(2)))
        banner.setOnClickListener { selectTab(0) }
        banner.visibility = View.GONE
        val bannerLp = FrameLayout.LayoutParams(MATCH, WRAP)
        bannerLp.gravity = android.view.Gravity.TOP
        bannerLp.setMargins(dp(8), dp(6), dp(8), 0)

        // header: row 1 = title + connection status, row 2 = simulate + connect
        val top = LinearLayout(this)
        top.gravity = android.view.Gravity.CENTER_VERTICAL
        val title = label("MonitorTune", 22f, Theme.TEXT)
        val ttl = SpannableString("MonitorTune")
        ttl.setSpan(ForegroundColorSpan(Theme.ACCENT), 7, 11, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        title.text = ttl
        title.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        dot = StatusDot(this)
        status = label("disconnected", 12f, Theme.MUTED)
        status.maxLines = 1
        status.ellipsize = TextUtils.TruncateAt.END
        top.addView(title)
        top.addView(dot, lp(dp(10), dp(10), 0f, dp(8)))
        top.addView(status, lp(0, WRAP, 1f, 0))
        root.addView(top)
        val top2 = LinearLayout(this)
        top2.gravity = android.view.Gravity.CENTER_VERTICAL
        chkSim = CheckBox(this)
        chkSim.text = "Simulate"
        chkSim.setTextColor(Theme.TEXT)
        btnConnect = btn("Connect", Color.parseColor("#0E9F6E")) { toggleConnect() }
        btnConnect.minimumHeight = dp(44)
        btnConnect.minHeight = dp(44)
        top2.addView(View(this), lp(0, 1, 1f))
        btnMute = btn("", Color.parseColor("#374151")) { toggleMute() }
        btnMute.minimumHeight = dp(44)
        btnMute.minHeight = dp(44)
        updateMuteButton()
        top2.addView(btnMute, lp(WRAP, WRAP, 0f, dp(4)))
        top2.addView(btnConnect)
        root.addView(top2, lp(MATCH, WRAP, 0f, 0))

        // bottom navigation (built here, attached under the content below)
        val navBar = LinearLayout(this)
        val names = arrayOf("VE TABLE")
        tabButtons = Array(names.size) { k ->
            val b = btn(names[k], Theme.CARD) { selectTab(k) }
            b.minimumHeight = dp(46)
            b.minHeight = dp(46)
            b.minimumWidth = dp(82)
            b
        }
        navBar.addView(tabButtons[0], lp(WRAP, WRAP, 0f, dp(3)))
        navScroll = HorizontalScrollView(this)
        navScroll.isHorizontalScrollBarEnabled = false
        navScroll.addView(navBar)

        // Log tab
        val logTab = LinearLayout(this)
        logTab.orientation = LinearLayout.VERTICAL
        val logBar = LinearLayout(this)
        logBar.gravity = android.view.Gravity.CENTER_VERTICAL
        btnLog = btn("Start Log", Color.parseColor("#2563EB")) { toggleLog() }
        logInfo = label("", 12f, Theme.MUTED)
        logBar.addView(btnLog)
        logBar.addView(logInfo, lp(WRAP, WRAP, 0f, dp(10)))
        capView = label("", 12f, Theme.MUTED)
        capView.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        logTab.addView(logBar, lp(MATCH, WRAP, 0f, dp(4)))
        logTab.addView(capView, lp(MATCH, WRAP, 0f, dp(6)))
        tilesBox = LinearLayout(this)
        tilesBox.orientation = LinearLayout.VERTICAL
        fun addTile(key: String, name: String, dec: Int, big: Boolean) { tileViews.add(tile(key, name, dec, big)) }
        addTile("CurrentVE", "CURRENT VE %", 1, true); addTile("CalculatedVE", "CALCULATED VE %", 1, true)
        addTile("WidebandAFR", "WIDEBAND AFR", 1, true); addTile("CommandedAFR", "COMMANDED AFR", 1, true)
        addTile("RPM", "RPM", 0, false); addTile("MAP_kPa", "MAP kPa", 1, false)
        addTile("BoostPSI", "BOOST psi", 1, false); addTile("TPS_pct", "TPS %", 1, false)
        addTile("Spark_deg", "SPARK deg", 1, false); addTile("Coolant_C", "COOLANT C", 0, false)
        addTile("Speed_kph", "SPEED km/h", 0, false); addTile("Baro_kPa", "BARO kPa", 1, false)
        addTile("KnockRetard_deg", "KNOCK RETARD deg", 1, false); addTile("KnockCounts", "KNOCK COUNT", 0, false)
        addTile("KnockDelta", "KNOCK DELTA", 0, false)
        layoutTiles()
        val logScroll = ScrollView(this)
        logScroll.addView(tilesBox)
        logTab.addView(logScroll)

        // Dash tab
        dashTab = LinearLayout(this)
        dashTab.orientation = LinearLayout.VERTICAL
        dashTab.setPadding(0, dp(6), 0, 0)
        gRpm = ArcGauge(this, "RPM", "rpm", 0f, 8000f, 6000f, 0, 1000f, 1000f)
        gSpeed = ArcGauge(this, "SPEED", "km/h", 0f, 240f, Float.MAX_VALUE, 0, 40f)
        gBoost = ArcGauge(this, "BOOST", "psi", -14f, 30f, 24f, 1, 10f)
        gCool = ArcGauge(this, "COOLANT", "C", 40f, 130f, 105f, 0, 20f)
        gSpark = ArcGauge(this, "SPARK", "deg", -10f, 50f, Float.MAX_VALUE, 1, 10f)
        gIac = ArcGauge(this, "IAC", "steps", 0f, 255f, Float.MAX_VALUE, 0, 32f)
        gIac.setUnavailable()
        layoutDash()

        // VE tab
        val veTab = LinearLayout(this)
        veTab.orientation = LinearLayout.VERTICAL
        val veBar = LinearLayout(this)
        veBar.gravity = android.view.Gravity.CENTER_VERTICAL
        veInfo = label("Load 64 KB BIN. Cyan border = sampled; dim = current table value.", 11f, Theme.MUTED)
        veBar.addView(btn("Load BIN", Color.parseColor("#2563EB")) { loadTarget = "ve"; openBin.launch(arrayOf("*/*")) }, lp(WRAP, WRAP, 0f, dp(3)))
        veBar.addView(btn("Clear", Theme.CARD_HI) { ve?.clear() }, lp(WRAP, WRAP, 0f, dp(3)))
        veBar.addView(btn("Write BIN", Color.parseColor("#0E9F6E")) { writeVe() }, lp(WRAP, WRAP, 0f, dp(3)))
        chkFill = CheckBox(this)
        chkFill.text = "Fill gaps"
        chkFill.isChecked = true
        chkFill.setTextColor(Theme.TEXT)
        chkSmooth = CheckBox(this)
        chkSmooth.text = "Smooth"
        chkSmooth.isChecked = true
        chkSmooth.setTextColor(Theme.TEXT)
        val hitsLbl = label("Min hits 3", 12f, Theme.TEXT)
        veBar.addView(btn("-", Theme.CARD_HI) {
            val v = ve
            if (v != null && v.minHits > 1) v.minHits--
            hitsLbl.text = "Min hits " + (ve?.minHits ?: 3)
        }, lp(WRAP, WRAP, 0f, dp(3)))
        veBar.addView(hitsLbl)
        veBar.addView(btn("+", Theme.CARD_HI) {
            val v = ve
            if (v != null && v.minHits < 10) v.minHits++
            hitsLbl.text = "Min hits " + (ve?.minHits ?: 3)
        }, lp(WRAP, WRAP, 0f, dp(3)))
        veBar.addView(chkFill)
        veBar.addView(chkSmooth)
        veBar.addView(veInfo, lp(WRAP, WRAP, 0f, dp(8)))
        veTab.addView(hscroll(veBar))
        veTab.addView(label("OSE $11P V104  •  LIVE ENGINE / VE CAPTURE", 11f, Theme.ACCENT).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }, lp(MATCH, WRAP, 0f, dp(3)))
        val gaugeRow = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        fun gaugeCard(g: View): View { val card = FrameLayout(this); card.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(34,43,55), Color.rgb(8,12,19))).apply { cornerRadius = dp(12).toFloat(); setStroke(dp(1), Color.rgb(68,83,102)) }; card.setPadding(dp(3),dp(3),dp(3),dp(3)); card.addView(g, FrameLayout.LayoutParams(MATCH, dp(112))); return card }
        veRpmGauge = ArcGauge(this, "RPM", "rpm", 0f, 8000f, 6500f, 0, 1000f)
        veBoostGauge = ArcGauge(this, "BOOST", "psi", -14f, 30f, 24f, 1, 10f)
        veCoolantGauge = ArcGauge(this, "COOLANT", "°C", 40f, 130f, 105f, 0, 20f)
        gaugeRow.addView(gaugeCard(veRpmGauge), lp(0, dp(118), 1f, dp(2)))
        gaugeRow.addView(gaugeCard(veBoostGauge), lp(0, dp(118), 1f, dp(2)))
        gaugeRow.addView(gaugeCard(veCoolantGauge), lp(0, dp(118), 1f, dp(2)))
        gaugeRow.addView(gaugeCard(gIac), lp(0, dp(118), 1f, dp(2)))
        veTab.addView(gaugeRow, lp(MATCH, dp(122), 0f, dp(2)))
        veTab.addView(label("IAC dial is included; live value is disabled until its byte offset and scaling are verified against the matching OSE $11P V104 ADX.", 9f, Theme.WARN), lp(MATCH, WRAP, 0f, dp(2)))
        val veCol = LinearLayout(this)
        veCol.orientation = LinearLayout.VERTICAL
        veGrids = Array(Cfg.VE_TABLES.size) { ti ->
            veCol.addView(label(Cfg.VE_TABLES[ti].name, 13f, Theme.TEXT), lp(WRAP, WRAP, 0f, dp(4)))
            val g = HeatGrid(this)
            g.minV = 40f
            g.maxV = 110f
            veCol.addView(g)
            g
        }
        veTab.addView(scrollBoth(veCol), lp(MATCH, 0, 1f, 0))

        // Knock tab
        val spTab = LinearLayout(this)
        spTab.orientation = LinearLayout.VERTICAL
        val spBar = LinearLayout(this)
        spBar.gravity = android.view.Gravity.CENTER_VERTICAL
        spInfo = label("Load 64 KB BIN. Red border = proposed retard.", 11f, Theme.MUTED)
        spBar.addView(btn("Load BIN", Color.parseColor("#2563EB")) { loadTarget = "sp"; openBin.launch(arrayOf("*/*")) }, lp(WRAP, WRAP, 0f, dp(3)))
        spBar.addView(btn("Clear", Theme.CARD_HI) { sp?.clear() }, lp(WRAP, WRAP, 0f, dp(3)))
        spBar.addView(btn("Write BIN", Color.parseColor("#0E9F6E")) { writeSp() }, lp(WRAP, WRAP, 0f, dp(3)))
        spBar.addView(spInfo, lp(WRAP, WRAP, 0f, dp(8)))
        spTab.addView(hscroll(spBar))
        val spCol = LinearLayout(this)
        spCol.orientation = LinearLayout.VERTICAL
        spGrids = Array(Cfg.SPARK_TABLES.size) { ti ->
            spCol.addView(label(Cfg.SPARK_TABLES[ti].name, 13f, Theme.TEXT), lp(WRAP, WRAP, 0f, dp(4)))
            val g = HeatGrid(this)
            g.minV = 0f
            g.maxV = 45f
            spCol.addView(g)
            g
        }
        spTab.addView(scrollBoth(spCol), lp(MATCH, 0, 1f, 0))

        // DYNO tab: road estimate plus manually entered measured dyno result.
        val dynoTab = LinearLayout(this)
        dynoTab.orientation = LinearLayout.VERTICAL
        val dynoControls = LinearLayout(this)
        dynoControls.gravity = android.view.Gravity.CENTER_VERTICAL
        val massInput = EditText(this)
        massInput.setSingleLine(true); massInput.setText("1700"); massInput.hint = "Vehicle kg"
        massInput.setTextColor(Theme.TEXT); massInput.setHintTextColor(Theme.MUTED); massInput.textSize = 13f
        massInput.setEms(5)
        dynoControls.addView(label("Mass kg", 12f, Theme.MUTED))
        dynoControls.addView(massInput, lp(WRAP, WRAP, 0f, dp(4)))
        lateinit var dynoStart: Button
        dynoStart = btn("Start Road Run", Color.parseColor("#0E9F6E")) {
            val mass = massInput.text.toString().toDoubleOrNull()
            if (mass == null || mass !in 500.0..5000.0) Toast.makeText(this, "Enter vehicle mass 500-5000 kg", Toast.LENGTH_LONG).show()
            else if (dynoActive) {
                dynoActive = false
                val run = roadDyno?.finish("Road ${dynoRuns.size + 1}")
                roadDyno = null
                if (run != null && run.points.size >= 3) { dynoRuns.add(run); DynoStore.save(this, dynoRuns); dynoInfo?.text = "Saved ${run.name}: peak ${String.format(Locale.US, "%.1f", run.peakHp)} wheel HP / ${String.format(Locale.US, "%.1f", run.peakTorque)} Nm" }
                else dynoInfo?.text = "Run discarded: insufficient valid acceleration samples. Use a smoother speed source / longer pull."
                dynoStart.text = "Start Road Run"; refreshDyno()
            } else {
                dynoMassKg = mass; roadDyno = RoadDyno(mass); dynoActive = true
                dynoStart.text = "Stop + Save Run"; dynoInfo?.text = "Recording road estimate — perform a safe, legal pull in a suitable closed area."
            }
        }
        dynoControls.addView(dynoStart, lp(WRAP, WRAP, 0f, dp(5)))
        dynoControls.addView(btn("Add Measured", Color.parseColor("#2563EB")) { addMeasuredDyno() }, lp(WRAP, WRAP, 0f, dp(4)))
        dynoControls.addView(btn("Export CSV", Theme.CARD_HI) { exportDyno() }, lp(WRAP, WRAP, 0f, dp(4)))
        dynoTab.addView(hscroll(dynoControls))
        dynoInfo = label("Road dyno estimates wheel power from vehicle mass and ALDL vehicle-speed acceleration. Set actual running mass.", 12f, Theme.MUTED)
        dynoTab.addView(dynoInfo, lp(MATCH, WRAP, 0f, dp(6)))
        dynoChart = DynoGraph(this)
        dynoChart?.minimumHeight = dp(260)
        dynoTab.addView(dynoChart, lp(MATCH, 0, 1f, dp(4)))
        dynoTab.addView(label("Road estimate is approximate: ALDL speed resolution and 4 Hz sampling limit accuracy; it reports wheel power and does not add assumed drivetrain loss. Measured runs are entered separately.", 11f, Theme.WARN))

        // AI ENGINE DOCTOR — on-device live rules; no cloud dependency or ECU write access.
        val aiTab = LinearLayout(this)
        aiTab.orientation = LinearLayout.VERTICAL
        aiTab.setPadding(dp(4), dp(6), dp(4), dp(4))
        aiPulse = AiPulseView(this)
        aiPulse.background = rounded(Theme.CARD, 12)
        aiTab.addView(aiPulse, lp(MATCH, dp(110), 0f, dp(4)))
        val aiHeader = LinearLayout(this)
        aiHeader.gravity = android.view.Gravity.CENTER_VERTICAL
        aiHeader.addView(label("LIVE ENGINE MONITOR", 16f, Theme.TEXT), lp(WRAP, WRAP, 1f))
        aiHeader.addView(btn("Alert Settings", Theme.CARD_HI) { showAiSettings() })
        aiTab.addView(aiHeader)
        val optRow = LinearLayout(this)
        optRow.gravity = android.view.Gravity.CENTER_VERTICAL
        chkVoice = CheckBox(this)
        chkVoice?.text = "Voice"
        chkVoice?.isChecked = alertEngine.voiceOn
        chkVoice?.setTextColor(Theme.TEXT)
        chkVoice?.setOnCheckedChangeListener { _, c -> alertEngine.voiceOn = c; saveAlertPrefs() }
        chkVib = CheckBox(this)
        chkVib?.text = "Vibrate"
        chkVib?.isChecked = alertEngine.vibrateOn
        chkVib?.setTextColor(Theme.TEXT)
        chkVib?.setOnCheckedChangeListener { _, c -> alertEngine.vibrateOn = c; saveAlertPrefs() }
        optRow.addView(chkVoice)
        optRow.addView(chkVib)
        optRow.addView(View(this), lp(0, 1, 1f))
        optRow.addView(label("Alarm volume sets loudness", 10f, Theme.MUTED))
        aiTab.addView(optRow, lp(MATCH, WRAP, 0f, dp(2)))
        val testRow = LinearLayout(this)
        testRow.addView(btn("Test caution", Theme.CARD_HI) { runAlertTest(Sev.CAUTION) }, lp(0, WRAP, 1f, dp(3)))
        testRow.addView(btn("Test critical", Color.parseColor("#B91C1C")) { runAlertTest(Sev.CRITICAL) }, lp(0, WRAP, 1f, dp(3)))
        aiTab.addView(testRow, lp(MATCH, WRAP, 0f, dp(2)))
        aiDataStatus = label("NO DATA — connect to ECU or enable simulation", 12f, Theme.WARN)
        aiTab.addView(aiDataStatus, lp(MATCH, WRAP, 0f, dp(4)))
        obdLiveReadout = label("Generic OBD-II live readings will appear here when an ELM327 is connected.", 11f, Theme.TEXT)
        obdLiveReadout?.setPadding(dp(10), dp(8), dp(10), dp(8))
        obdLiveReadout?.background = rounded(Theme.CARD, 10)
        aiTab.addView(obdLiveReadout, lp(MATCH, WRAP, 0f, dp(5)))
        val dtcControls = LinearLayout(this)
        dtcControls.orientation = LinearLayout.HORIZONTAL
        dtcControls.addView(btn("Read ECU Codes", Theme.BLUE) {
            if (elmLink?.requestReadDtcs() == true) {
                Toast.makeText(this, "Reading stored, pending and permanent ECU codes…", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Connect a Bluetooth ELM327 first, or wait for the current ECU request to finish.", Toast.LENGTH_LONG).show()
            }
        }, lp(0, WRAP, 1f, dp(3)))
        dtcControls.addView(btn("Clear ECU Codes", Color.parseColor("#B45309")) {
            AlertDialog.Builder(this)
                .setTitle("Clear emissions fault codes?")
                .setMessage("This sends the standard OBD-II Mode 04 clear command to the engine ECU. It may erase stored and pending emissions codes, freeze-frame data and reset readiness monitors. It does not repair the fault. Permanent DTCs may remain until the ECU confirms the fault is fixed and its monitor completes. Save or record the codes before proceeding.")
                .setPositiveButton("Clear codes") { _, _ ->
                    if (elmLink?.requestClearCodes() == true) {
                        Toast.makeText(this, "Clear command queued. Keep ignition on and adapter connected.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Connect a Bluetooth ELM327 first, or wait for the current ECU request to finish.", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }, lp(0, WRAP, 1f, dp(3)))
        aiTab.addView(dtcControls, lp(MATCH, WRAP, 0f, dp(5)))
        aiTab.addView(label("DTC tools use standard OBD-II engine/emissions services only. They do not scan or clear ABS, SRS, transmission or other manufacturer-specific modules.", 10f, Theme.WARN), lp(MATCH, WRAP, 0f, dp(4)))
        val summaryView = label("Waiting for ECU data", 20f, Theme.TEXT)
        aiSummary = summaryView
        summaryView.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        summaryView.setPadding(dp(12), dp(12), dp(12), dp(12))
        summaryView.background = rounded(Theme.CARD, 12)
        aiTab.addView(summaryView, lp(MATCH, WRAP, 0f, dp(5)))
        aiTab.addView(label("LIVE ALERTS", 12f, Theme.MUTED), lp(MATCH, WRAP, 0f, dp(5)))
        aiAlertsBox = LinearLayout(this)
        aiAlertsBox?.orientation = LinearLayout.VERTICAL
        aiTab.addView(aiAlertsBox, lp(MATCH, WRAP, 0f, 0))
        aiTab.addView(label("Monitors: supported live ECU readings and data freshness. OBD-II generic mode does not provide a universal wideband AFR or knock-retard PID; these require manufacturer-specific support or additional sensors. No automatic ECU changes are made.", 11f, Theme.WARN), lp(MATCH, WRAP, 0f, dp(5)))
        val geminiControls = LinearLayout(this)
        geminiControls.orientation = LinearLayout.HORIZONTAL
        geminiControls.addView(btn("Set Gemini API Key", Theme.CARD_HI) { showGeminiKeyDialog() }, lp(0, WRAP, 1f, dp(3)))
        geminiControls.addView(btn("Analyse Live Data", Theme.BLUE) { askGemini() }, lp(0, WRAP, 1f, dp(3)))
        aiTab.addView(geminiControls, lp(MATCH, WRAP, 0f, dp(6)))
        aiTab.addView(label("GEMINI DIAGNOSTIC", 12f, Theme.MUTED), lp(MATCH, WRAP, 0f, dp(3)))
        geminiOutput = label("Set your Gemini API key, connect to the ECU, then tap Analyse Live Data. Nothing is sent until you press the button.", 12f, Theme.TEXT)
        geminiOutput?.setPadding(dp(12), dp(10), dp(12), dp(10))
        geminiOutput?.background = rounded(Theme.CARD, 10)
        aiTab.addView(geminiOutput, lp(MATCH, WRAP, 0f, dp(5)))
        aiTab.addView(label("Gemini analysis is manual and advisory. Each request sends one snapshot to Google Gemini. The app never streams every live sample to the cloud. Generic OBD-II only exposes standard supported PIDs; enhanced manufacturer data varies by vehicle and adapter. API key is held in memory for this session only.", 11f, Theme.WARN))

        val aiRoot = ScrollView(this)
        aiRoot.addView(aiTab)

        // WORKSHOP: persistent vehicle profiles and exportable technician reports.
        val workshopTab = LinearLayout(this)
        workshopTab.orientation = LinearLayout.VERTICAL
        workshopTab.setPadding(dp(10), dp(10), dp(10), dp(10))
        workshopTab.addView(label("WORKSHOP / VEHICLE DATABASE", 17f, Theme.TEXT), lp(MATCH, WRAP, 0f, dp(8)))
        workshopProfileLabel = label("No vehicle profile selected", 13f, Theme.MUTED)
        workshopProfileLabel?.setPadding(dp(10), dp(10), dp(10), dp(10))
        workshopProfileLabel?.background = rounded(Theme.CARD, 10)
        workshopTab.addView(workshopProfileLabel, lp(MATCH, WRAP, 0f, dp(8)))
        val profileButtons = LinearLayout(this)
        profileButtons.addView(btn("Previous", Theme.CARD_HI) { if (workshopProfiles.isNotEmpty()) { selectedProfileIndex = (selectedProfileIndex - 1 + workshopProfiles.size) % workshopProfiles.size; refreshWorkshopProfile() } }, lp(0, WRAP, 1f, dp(3)))
        profileButtons.addView(btn("Next", Theme.CARD_HI) { if (workshopProfiles.isNotEmpty()) { selectedProfileIndex = (selectedProfileIndex + 1) % workshopProfiles.size; refreshWorkshopProfile() } }, lp(0, WRAP, 1f, dp(3)))
        workshopTab.addView(profileButtons, lp(MATCH, WRAP, 0f, dp(5)))
        val profileActions = LinearLayout(this)
        profileActions.addView(btn("Add Vehicle", Color.parseColor("#2563EB")) { showAddVehicleDialog() }, lp(0, WRAP, 1f, dp(3)))
        profileActions.addView(btn("Delete Selected", Color.parseColor("#9B2C2C")) { deleteSelectedVehicle() }, lp(0, WRAP, 1f, dp(3)))
        workshopTab.addView(profileActions, lp(MATCH, WRAP, 0f, dp(8)))
        workshopTab.addView(btn("Generate Diagnostic Report", Color.parseColor("#0E9F6E")) { exportWorkshopReport() }, lp(MATCH, WRAP, 0f, dp(8)))
        workshopTab.addView(label("TUNERPRO DEFINITION TOOLS", 14f, Theme.TEXT), lp(MATCH, WRAP, 0f, dp(8)))
        val definitionButtons = LinearLayout(this)
        definitionButtons.addView(btn("Import XDF", Color.parseColor("#2563EB")) { definitionImportMode = "XDF"; openTunerProDefinition.launch(arrayOf("*/*")) }, lp(0, WRAP, 1f, dp(3)))
        definitionButtons.addView(btn("Inspect ADX", Color.parseColor("#475569")) { definitionImportMode = "ADX"; openTunerProDefinition.launch(arrayOf("*/*")) }, lp(0, WRAP, 1f, dp(3)))
        workshopTab.addView(definitionButtons, lp(MATCH, WRAP, 0f, dp(4)))
        workshopTab.addView(btn("Preview XDF Table Against BIN (Read Only)", Color.parseColor("#475569")) { if (importedXdf == null) Toast.makeText(this, "Import an XDF first", Toast.LENGTH_SHORT).show() else openXdfPreviewBin.launch(arrayOf("*/*")) }, lp(MATCH, WRAP, 0f, dp(4)))
        workshopTab.addView(label("$importedDefinitionSummary\nImport indexes definition metadata only. It does not change live ALDL decoding, write a BIN, or enable ECU flashing. Verify exact OS, address origin, endian, strides, axes, scaling and checksum independently.", 10f, Theme.WARN), lp(MATCH, WRAP, 0f, dp(6)))
        workshopTab.addView(label("VE LOG REVIEW: the new AFR correction advisor calculates capped suggestions from stable, repeated samples (minimum 5 hits). Suggestions are review-only; transient enrichment, acceleration, fuel pressure, wideband delay and closed-loop corrections must be filtered before calibration.", 10f, Theme.MUTED), lp(MATCH, WRAP, 0f, dp(6)))
        workshopTab.addView(label("Reports include the selected vehicle profile, live VIN/link information, available standard DTCs and sensor plausibility findings. Missing ECU data is clearly identified; generic OBD-II does not scan every module.", 12f, Theme.MUTED), lp(MATCH, WRAP, 0f, dp(8)))
        workshopTab.addView(label("ECU SUPPORT STATUS", 13f, Theme.TEXT), lp(MATCH, WRAP, 0f, dp(6)))
        EcuRegistry.definitions.forEach { def ->
            val card = label("${def.label}\n${def.transport} • ${def.diagnosticLevel}\n${def.notes}", 11f, if (def.id == "GM_DELPHI_11P_V104") Theme.GOOD else Theme.MUTED)
            card.setPadding(dp(9), dp(8), dp(9), dp(8)); card.background = rounded(Theme.CARD, 9)
            workshopTab.addView(card, lp(MATCH, WRAP, 0f, dp(5)))
        }
        workshopTab.addView(label("GM DELCO SPECIALIST DATABASE", 14f, Theme.TEXT), lp(MATCH, WRAP, 0f, dp(8)))
        workshopTab.addView(label("The existing OSE \$11P V104 tools remain a separate, project-specific ALDL/BIN workflow. Other Delco families are catalogued but locked until exact ECU, OS, protocol and matching definition data are verified.", 11f, Theme.WARN), lp(MATCH, WRAP, 0f, dp(5)))
        VehicleSupportCatalog.delcoFamilies.forEach { family ->
            val detail = "${family.name}\n${family.transport} • ${family.supportLevel}\n${family.definitionStatus}\nRequirements: ${family.requirements.joinToString("; ")}"
            val card = label(detail, 11f, if (family.id == "OSE_11P_V104") Theme.GOOD else Theme.MUTED)
            card.setPadding(dp(9), dp(8), dp(9), dp(8)); card.background = rounded(Theme.CARD, 9)
            workshopTab.addView(card, lp(MATCH, WRAP, 0f, dp(5)))
        }
        workshopTab.addView(label("MULTI-MANUFACTURER COVERAGE", 14f, Theme.TEXT), lp(MATCH, WRAP, 0f, dp(8)))
        workshopTab.addView(label("The makes below use generic SAE OBD-II read-only diagnostics where fitted and supported. This is not a claim of full OEM module coverage.", 11f, Theme.WARN), lp(MATCH, WRAP, 0f, dp(5)))
        VehicleSupportCatalog.manufacturers.forEach { make ->
            val card = label("${make.displayName}\nCommon legacy/current transports: ${make.commonProtocols.joinToString()}\n${make.genericObd2Note}\n${make.enhancedStatus}", 11f, Theme.MUTED)
            card.setPadding(dp(9), dp(8), dp(9), dp(8)); card.background = rounded(Theme.CARD, 9)
            workshopTab.addView(card, lp(MATCH, WRAP, 0f, dp(5)))
        }
        workshopTab.addView(label("PROTOCOL / HARDWARE LIMITS", 13f, Theme.TEXT), lp(MATCH, WRAP, 0f, dp(6)))
        VehicleSupportCatalog.protocolNotes.forEach { note ->
            workshopTab.addView(label("• $note", 11f, Theme.MUTED), lp(MATCH, WRAP, 0f, dp(3)))
        }
        val workshopRoot = ScrollView(this)
        workshopRoot.addView(workshopTab)
        refreshWorkshopProfile()

        // EFILive V4-style offline definition / frame inspection workspace.
        val v4Tab = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        v4Tab.addView(label("EFILIVE V4-STYLE ALDL SCANNER", 17f, Theme.TEXT), lp(MATCH, WRAP, 0f, dp(6)))
        v4Tab.addView(label("Independent, definition-driven GM ALDL scanner. Use a verified raw-serial ALDL interface; an ELM327 is not a raw ALDL adapter. Never use an unverified command definition.", 11f, Theme.WARN), lp(MATCH, WRAP, 0f, dp(8)))
        v4Tab.addView(btn("Import vehicle definition XML", Color.parseColor("#2563EB")) { openV4Definition.launch(arrayOf("text/xml", "application/xml", "text/plain")) }, lp(MATCH, WRAP, 0f, dp(5)))
        v4Info = label("No V4 definition loaded.", 12f, Theme.MUTED)
        v4Info?.setPadding(dp(9), dp(8), dp(9), dp(8)); v4Info?.background = rounded(Theme.CARD, 9)
        v4Tab.addView(v4Info, lp(MATCH, WRAP, 0f, dp(5)))
        v4Status = label("Disconnected — no ALDL serial interface open.", 12f, Theme.WARN)
        v4Tab.addView(v4Status, lp(MATCH, WRAP, 0f, dp(5)))
        val v4ConnectRow = LinearLayout(this)
        v4ConnectRow.addView(btn("USB serial", Color.parseColor("#0E9F6E")) { connectV4Usb() }, lp(0, WRAP, 1f, dp(2)))
        v4ConnectRow.addView(btn("Bluetooth SPP", Color.parseColor("#2563EB")) { connectV4Bluetooth() }, lp(0, WRAP, 1f, dp(2)))
        v4ConnectRow.addView(btn("Disconnect", Color.parseColor("#B91C1C")) { disconnectV4() }, lp(0, WRAP, 1f, dp(2)))
        v4Tab.addView(v4ConnectRow, lp(MATCH, WRAP, 0f, dp(5)))
        val v4RequestRow = LinearLayout(this)
        v4RequestRow.addView(btn("Requests / send", Theme.CARD_HI) { showV4Requests() }, lp(0, WRAP, 1f, dp(2)))
        v4RequestRow.addView(btn("Start polling", Color.parseColor("#0E9F6E")) { startV4Polling() }, lp(0, WRAP, 1f, dp(2)))
        v4RequestRow.addView(btn("Stop polling", Color.parseColor("#B91C1C")) { stopV4Polling() }, lp(0, WRAP, 1f, dp(2)))
        v4Tab.addView(v4RequestRow, lp(MATCH, WRAP, 0f, dp(5)))
        val v4DtcRow = LinearLayout(this)
        v4DtcRow.addView(btn("Read DTCs", Theme.CARD_HI) { readV4Dtcs() }, lp(0, WRAP, 1f, dp(2)))
        v4DtcRow.addView(btn("Clear DTCs", Color.parseColor("#B45309")) { clearV4Dtcs() }, lp(0, WRAP, 1f, dp(2)))
        v4DtcRow.addView(btn("Macros", Theme.CARD_HI) { showV4Macros() }, lp(0, WRAP, 1f, dp(2)))
        v4Tab.addView(v4DtcRow, lp(MATCH, WRAP, 0f, dp(5)))
        val gaugeScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = true }
        v4GaugeContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        gaugeScroll.addView(v4GaugeContainer)
        v4Tab.addView(gaugeScroll, lp(MATCH, dp(125), 0f, dp(3)))
        v4ChartView = V4LiveChartView(this).apply { setBackgroundColor(Theme.CARD) }
        v4Tab.addView(btn("Choose trend parameter", Theme.CARD_HI) { chooseV4TrendParameter() }, lp(MATCH, WRAP, 0f, dp(3)))
        v4Tab.addView(v4ChartView, lp(MATCH, dp(180), 0f, dp(4)))
        v4Values = label("Decoded ALDL values will appear here.", 12f, Theme.TEXT)
        v4Values?.setPadding(dp(9), dp(8), dp(9), dp(8)); v4Values?.background = rounded(Theme.CARD, 9)
        v4Tab.addView(v4Values, lp(MATCH, WRAP, 0f, dp(5)))
        v4RawMonitor = label("RAW SERIAL MONITOR\nTX: —\nRX: —", 11f, Theme.MUTED)
        v4RawMonitor?.setPadding(dp(9), dp(8), dp(9), dp(8)); v4RawMonitor?.background = rounded(Theme.CARD, 9)
        v4Tab.addView(v4RawMonitor, lp(MATCH, WRAP, 0f, dp(5)))
        val rawFrame = EditText(this).apply { hint = "Offline frame decode (hex)"; setTextColor(Theme.TEXT); setHintTextColor(Theme.MUTED); minLines = 2 }
        v4Tab.addView(rawFrame, lp(MATCH, WRAP, 0f, dp(4)))
        v4Tab.addView(btn("Decode offline frame", Color.parseColor("#0E9F6E")) {
            try {
                val def = v4Definition ?: error("Import a definition first.")
                val result = V4Protocol.decode(V4Hex.parse(rawFrame.text.toString()), def)
                displayV4Result(result, null)
            } catch (e: Exception) { Toast.makeText(this, "Decode failed: ${e.message}", Toast.LENGTH_LONG).show() }
        }, lp(MATCH, WRAP, 0f, dp(4)))
        val v4Actions = LinearLayout(this)
        v4Actions.addView(btn("Export CSV", Theme.BLUE) {
            val def = v4Definition
            if (def == null || v4Recorder.snapshot().isEmpty()) Toast.makeText(this, "No decoded session data to export.", Toast.LENGTH_LONG).show()
            else { pendingBytes = v4Recorder.toCsv(def).toByteArray(); saveCsv.launch("efilive_v4_session.csv") }
        }, lp(0, WRAP, 1f, dp(2)))
        v4Actions.addView(btn("Macro list", Theme.CARD_HI) { showV4Macros() }, lp(0, WRAP, 1f, dp(2)))
        v4Tab.addView(v4Actions, lp(MATCH, WRAP, 0f, dp(5)))
        val v4Root = ScrollView(this)
        v4Root.addView(v4Tab)
        tabs = arrayOf<View>(veTab)
        val holder = FrameLayout(this)
        for (t in tabs) holder.addView(t, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(holder, lp(MATCH, 0, 1f, 0))
        frame.addView(banner, bannerLp)
        setContentView(frame)
        selectTab(0) // VE workspace is the only screen
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(500).start()
    }

    private fun setV4Status(message: String) { v4Status?.text = message }

    private fun connectV4Usb() {
        val def = v4Definition ?: run { Toast.makeText(this, "Import a vehicle definition first.", Toast.LENGTH_LONG).show(); return }
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(manager).firstOrNull()
            ?: run { setV4Status("No USB serial adapter found. Check USB OTG and the ALDL interface."); return }
        if (manager.hasPermission(driver.device)) startV4UsbTransport()
        else {
            v4PendingConnect = true
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            manager.requestPermission(driver.device, PendingIntent.getBroadcast(this, 711, intent, flags))
            setV4Status("Waiting for USB serial permission…")
        }
    }

    private fun startV4UsbTransport() {
        val def = v4Definition ?: return
        setV4Status("Opening USB raw serial at ${def.baud} baud…")
        v4Io.execute {
            try {
                val transport = V4UsbTransport(this, def.baud, def.dataBits, def.stopBits, def.parity)
                def.connectHex?.let { transport.write(V4Hex.parse(it)) }
                v4Transport?.close(); v4Transport = transport
                runOnUiThread { setV4Status("Connected: USB raw serial • ${def.baud} baud ${def.dataBits}-${def.parity}-${def.stopBits}") }
            } catch (e: Exception) { runOnUiThread { setV4Status("USB connection failed: ${e.message}") } }
        }
    }

    private fun connectV4Bluetooth() {
        val def = v4Definition ?: run { Toast.makeText(this, "Import a vehicle definition first.", Toast.LENGTH_LONG).show(); return }
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: run { setV4Status("Bluetooth is not available on this phone."); return }
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), 713)
            setV4Status("Allow Nearby devices, then reconnect.")
            return
        }
        if (!adapter.isEnabled) { setV4Status("Turn Bluetooth on in Android settings."); return }
        val devices = try { adapter.bondedDevices.toList().sortedBy { it.name ?: it.address } } catch (_: SecurityException) { emptyList() }
        if (devices.isEmpty()) { setV4Status("Pair a raw ALDL Bluetooth serial bridge in Android settings first."); return }
        val labels = devices.map { "${it.name ?: "Bluetooth device"} (${it.address})" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Select raw ALDL Bluetooth SPP device")
            .setMessage("Select a Bluetooth Classic serial bridge that exposes raw UART bytes. ELM327 AT-command adapters are not compatible with this raw transport.")
            .setItems(labels) { _, which -> startV4BluetoothTransport(adapter, devices[which], def) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun startV4BluetoothTransport(adapter: BluetoothAdapter, device: BluetoothDevice, def: V4VehicleDefinition) {
        setV4Status("Connecting Bluetooth SPP…")
        v4Io.execute {
            try {
                val transport = V4BluetoothTransport(adapter, device)
                def.connectHex?.let { transport.write(V4Hex.parse(it)) }
                v4Transport?.close(); v4Transport = transport
                runOnUiThread { setV4Status("Connected: raw Bluetooth SPP • ${device.name ?: device.address} • ${def.baud} baud expected at bridge") }
            } catch (e: Exception) { runOnUiThread { setV4Status("Bluetooth connection failed: ${e.message}") } }
        }
    }

    private fun disconnectV4() {
        stopV4Polling()
        val endHex = v4Definition?.disconnectHex
        v4Io.execute {
            try {
                val t = v4Transport
                if (t != null && endHex != null) t.write(V4Hex.parse(endHex))
                t?.close()
            } catch (_: Exception) {}
            v4Transport = null
            runOnUiThread { setV4Status("Disconnected.") }
        }
    }

    private fun showV4Requests() {
        val d = v4Definition ?: run { Toast.makeText(this, "Import a definition first.", Toast.LENGTH_LONG).show(); return }
        if (d.requests.isEmpty()) { Toast.makeText(this, "No requests are defined.", Toast.LENGTH_LONG).show(); return }
        val labels = d.requests.map { req ->
            "[${req.module}] ${req.name} • TX ${req.txHex}" +
                (if (req.pollIntervalMs > 0) " • poll ${req.pollIntervalMs}ms" else "") +
                (if (req.requiresConfirmation) " • CONFIRM" else "")
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Configured ECU requests")
            .setItems(labels) { _, which -> sendV4RequestWithConfirmation(d.requests[which]) }
            .setNegativeButton("Close", null).show()
    }

    private fun sendV4RequestWithConfirmation(request: V4Request) {
        if (request.requiresConfirmation) {
            AlertDialog.Builder(this).setTitle("Confirm ECU command")
                .setMessage("This request is marked as state-changing. Only continue if verified for this exact ECU.\n\n${request.name}\nTX: ${request.txHex}")
                .setPositiveButton("Transmit") { _, _ -> sendV4Request(request) }
                .setNegativeButton("Cancel", null).show()
        } else sendV4Request(request)
    }

    private fun sendV4Request(request: V4Request) {
        if (v4Transport == null) { setV4Status("Connect a compatible raw ALDL serial interface first."); return }
        v4Io.execute {
            try {
                performV4Request(request)
                val def = v4Definition
                if (def != null && request.id == def.dtcClearRequestId) {
                    val readback = def.dtcReadRequestId?.let { id -> def.requests.firstOrNull { it.id == id } }
                    if (readback != null) {
                        Thread.sleep(300)
                        performV4Request(readback)
                        runOnUiThread { setV4Status("Clear request sent; DTC readback completed. Review the returned codes and ECU response.") }
                    } else runOnUiThread { setV4Status("Clear request transmitted; no DTC readback request is configured.") }
                }
            } catch (e: Exception) { runOnUiThread { setV4Status("Request failed: ${e.message}") } }
        }
    }

    private fun performV4Request(request: V4Request): V4FrameResult {
        val transport = v4Transport ?: throw IllegalStateException("Serial interface disconnected.")
        val tx = V4Hex.parse(request.txHex)
        transport.write(tx)
        val rx = transport.read(request.responseTimeoutMs, request.quietTimeMs)
        if (rx.isEmpty()) throw java.io.IOException("No response to ${request.name} (timeout).")
        val def = v4Definition ?: throw IllegalStateException("No vehicle definition loaded.")
        val result = V4Protocol.decode(rx, def, request)
        runOnUiThread {
            v4RawMonitor?.text = "RAW SERIAL MONITOR\nTX: ${V4Hex.format(tx)}\nRX: ${V4Hex.format(rx)}"
            displayV4Result(result, request)
        }
        return result
    }

    private fun displayV4Result(result: V4FrameResult, request: V4Request?) {
        val def = v4Definition ?: return
        v4LastDecoded = result
        if (result.values.isNotEmpty()) {
            v4Recorder.add(result.values)
            val elapsed = System.currentTimeMillis()
            result.values.forEach {
                v4Chart.add(it.parameter.id, V4ChartPoint(elapsed, it.value))
                v4GaugeViews[it.parameter.id]?.setValue(it.value)
            }
            if (v4TrendParameterId == null) v4TrendParameterId = result.values.first().parameter.id
            val selected = v4TrendParameterId
            if (selected != null) v4ChartView?.setSeries(def.parameters.firstOrNull { it.id == selected }?.name ?: selected, v4Chart.get(selected))
        }
        val dtcs = if (request != null && request.id == def.dtcReadRequestId) V4DtcDecoder.decode(result.bytes, def, request) else emptyList()
        v4Values?.text = buildString {
            if (request != null) appendLine("Request: ${request.name}")
            appendLine("RX: ${V4Hex.format(result.bytes)}")
            result.values.forEach { appendLine("${it.parameter.name}: ${String.format(Locale.US, "%.3f", it.value)} ${it.parameter.units} [raw ${it.raw}]") }
            if (dtcs.isNotEmpty()) { appendLine("DTCs:"); dtcs.forEach { appendLine("${it.first} — ${it.second}") } }
            if (request != null && request.id == def.dtcReadRequestId && dtcs.isEmpty()) appendLine("No configured DTC rule matched. Raw response shown; DTC decoding is vehicle-specific.")
            result.warnings.forEach { appendLine("WARNING: $it") }
            if (result.values.isEmpty() && result.warnings.isEmpty()) appendLine("Frame received; no parameter mapping matched.")
        }
        setV4Status("Received ${result.bytes.size} bytes" + (if (result.checksumValid == false) " • CHECKSUM MISMATCH" else if (result.checksumValid == true) " • checksum OK" else ""))
    }

    private fun startV4Polling() {
        val d = v4Definition ?: run { Toast.makeText(this, "Import a definition first.", Toast.LENGTH_LONG).show(); return }
        if (v4Transport == null) { setV4Status("Connect a raw ALDL interface before polling."); return }
        val pollable = d.requests.filter { it.pollIntervalMs >= 100 && !it.requiresConfirmation && it.id != d.dtcClearRequestId }
        if (pollable.isEmpty()) { Toast.makeText(this, "No read-only request has pollIntervalMs >= 100 configured.", Toast.LENGTH_LONG).show(); return }
        val labels = pollable.map { "${it.name} — every ${it.pollIntervalMs} ms" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Select live data request").setItems(labels) { _, which ->
            stopV4Polling()
            v4Polling = true
            scheduleV4Poll(pollable[which])
            setV4Status("Live polling: ${pollable[which].name}")
        }.setNegativeButton("Cancel", null).show()
    }

    private fun scheduleV4Poll(request: V4Request) {
        val task = Runnable {
            if (!v4Polling) return@Runnable
            v4Io.execute {
                try { performV4Request(request) }
                catch (e: Exception) { runOnUiThread { setV4Status("Poll error: ${e.message}") } }
                finally {
                    if (v4Polling) handler.postDelayed({ if (v4Polling) scheduleV4Poll(request) }, request.pollIntervalMs)
                }
            }
        }
        v4PollRunnable = task
        handler.post(task)
    }

    private fun stopV4Polling() {
        v4Polling = false
        v4PollRunnable?.let { handler.removeCallbacks(it) }
        v4PollRunnable = null
        if (v4Transport != null) setV4Status("Connected — live polling stopped.")
    }

    private fun readV4Dtcs() {
        val d = v4Definition ?: run { Toast.makeText(this, "Import a definition first.", Toast.LENGTH_LONG).show(); return }
        val id = d.dtcReadRequestId ?: run { Toast.makeText(this, "No vehicle-specific DTC read request configured.", Toast.LENGTH_LONG).show(); return }
        val req = d.requests.firstOrNull { it.id == id } ?: return
        sendV4RequestWithConfirmation(req)
    }

    private fun clearV4Dtcs() {
        val d = v4Definition ?: run { Toast.makeText(this, "Import a definition first.", Toast.LENGTH_LONG).show(); return }
        val id = d.dtcClearRequestId ?: run { Toast.makeText(this, "No vehicle-specific DTC clear request configured. Nothing transmitted.", Toast.LENGTH_LONG).show(); return }
        val req = d.requests.firstOrNull { it.id == id } ?: return
        AlertDialog.Builder(this).setTitle("Clear ECU trouble codes?")
            .setMessage("This sends a vehicle-specific command that may erase diagnostic information and learned data. Verify this request for the connected ECU.\n\n${req.name}\nTX: ${req.txHex}")
            .setPositiveButton("I understand — transmit") { _, _ -> sendV4Request(req) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun chooseV4TrendParameter() {
        val d = v4Definition ?: run { Toast.makeText(this, "Import a definition first.", Toast.LENGTH_LONG).show(); return }
        if (d.parameters.isEmpty()) return
        AlertDialog.Builder(this).setTitle("Chart parameter")
            .setItems(d.parameters.map { "${it.name} (${it.units})" }.toTypedArray()) { _, which ->
                v4TrendParameterId = d.parameters[which].id
                v4ChartView?.setSeries(d.parameters[which].name, v4Chart.get(d.parameters[which].id))
            }.setNegativeButton("Close", null).show()
    }

    private fun runV4Macro(macro: V4Macro) {
        val d = v4Definition ?: return
        val sensitive = macro.requiresConfirmation || macro.steps.any { step ->
            val req = d.requests.firstOrNull { it.id == step.requestId }
            req?.requiresConfirmation == true || step.requestId == d.dtcClearRequestId
        }
        val run = {
            if (v4Transport == null) setV4Status("Connect a raw ALDL interface first.")
            else v4Io.execute {
                try {
                    V4MacroExecutor(d, v4Transport!!).execute(macro.copy(requiresConfirmation = sensitive)) { step, request, result ->
                        runOnUiThread {
                            v4RawMonitor?.text = "MACRO ${macro.name} • STEP ${step + 1}\nTX: ${request.txHex}\nRX: ${V4Hex.format(result.bytes)}"
                            displayV4Result(result, request)
                        }
                    }
                    runOnUiThread { setV4Status("Macro complete: ${macro.name}") }
                } catch (e: Exception) { runOnUiThread { setV4Status("Macro failed: ${e.message}") } }
            }
        }
        if (sensitive) AlertDialog.Builder(this).setTitle("Confirm macro transmission")
            .setMessage("This macro includes a request marked state-changing or a DTC-clear request. Verify all steps before transmitting.\n\n${macro.name}")
            .setPositiveButton("Run macro") { _, _ -> run() }.setNegativeButton("Cancel", null).show()
        else run()
    }

    private fun refreshV4Definition() {
        val d = v4Definition ?: return
        v4Info?.text = "${d.name} | ${d.baud} baud ${d.dataBits}-${d.parity}-${d.stopBits}\nParameters: ${d.parameters.size} | Requests: ${d.requests.size} | Macros: ${d.macros.size}\n${d.warnings.joinToString("\n")}"
        v4GaugeViews.clear()
        v4GaugeContainer?.removeAllViews()
        d.parameters.take(8).forEach { parameter ->
            val gauge = V4MiniGaugeView(this, parameter).apply { setBackgroundColor(Theme.CARD) }
            v4GaugeViews[parameter.id] = gauge
            v4GaugeContainer?.addView(gauge, lp(dp(112), dp(118), 0f, dp(3)))
        }
        v4TrendParameterId = d.parameters.firstOrNull()?.id
        v4TrendParameterId?.let { id -> v4ChartView?.setSeries(d.parameters.first { it.id == id }.name, v4Chart.get(id)) }
    }

    private fun showV4Macros() {
        val d = v4Definition ?: run { Toast.makeText(this, "Import a V4 definition first.", Toast.LENGTH_LONG).show(); return }
        val labels = d.macros.map { "${it.name}${if (it.requiresConfirmation) " [CONFIRMATION REQUIRED]" else ""} • ${it.steps.size} steps" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Diagnostic macros")
            .setItems(labels) { _, which -> manageV4Macro(d.macros[which]) }
            .setPositiveButton("Create macro") { _, _ -> editV4Macro(null) }
            .setNegativeButton("Close", null).show()
    }

    private fun manageV4Macro(macro: V4Macro) {
        val actions = if (macro.id.startsWith("user_")) arrayOf("Run", "Edit", "Delete") else arrayOf("Run")
        AlertDialog.Builder(this).setTitle(macro.name).setItems(actions) { _, which ->
            when (actions[which]) {
                "Run" -> runV4Macro(macro)
                "Edit" -> editV4Macro(macro)
                "Delete" -> AlertDialog.Builder(this).setTitle("Delete macro?")
                    .setMessage("Remove the user-created macro ${macro.name}?")
                    .setPositiveButton("Delete") { _, _ ->
                        val d = v4Definition ?: return@setPositiveButton
                        v4Definition = d.copy(macros = d.macros.filterNot { it.id == macro.id })
                        V4MacroStore.save(this, v4Definition!!)
                        Toast.makeText(this, "Macro deleted.", Toast.LENGTH_SHORT).show()
                    }.setNegativeButton("Cancel", null).show()
            }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun editV4Macro(existing: V4Macro?) {
        val d = v4Definition ?: return
        if (d.requests.isEmpty()) { Toast.makeText(this, "Add requests to the imported vehicle definition first.", Toast.LENGTH_LONG).show(); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(6), dp(14), dp(6)) }
        val name = EditText(this).apply {
            hint = "Macro name"; setSingleLine(true); setTextColor(Theme.TEXT); setHintTextColor(Theme.MUTED)
            setText(existing?.name.orEmpty())
        }
        val confirm = CheckBox(this).apply {
            text = "Require confirmation before running"
            setTextColor(Theme.TEXT)
            isChecked = existing?.requiresConfirmation ?: false
        }
        box.addView(name)
        box.addView(confirm)
        val selected = d.requests.map { request -> request.id to (existing?.steps?.any { it.requestId == request.id } == true) }.toMap().toMutableMap()
        d.requests.forEach { request ->
            val check = CheckBox(this).apply {
                text = "[${request.module}] ${request.name} — ${request.txHex}" + if (request.requiresConfirmation) " • STATE-CHANGING" else ""
                setTextColor(Theme.TEXT)
                isChecked = selected[request.id] == true
                setOnCheckedChangeListener { _, checked -> selected[request.id] = checked }
            }
            box.addView(check)
        }
        val scroll = ScrollView(this).apply { addView(box) }
        AlertDialog.Builder(this).setTitle(if (existing == null) "Create diagnostic macro" else "Edit diagnostic macro")
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                val macroName = name.text.toString().trim()
                val steps = d.requests.filter { selected[it.id] == true }.map { V4MacroStep(it.id, 100) }
                if (macroName.isBlank() || steps.isEmpty()) {
                    Toast.makeText(this, "Enter a name and select at least one request.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val id = existing?.id ?: "user_${System.currentTimeMillis()}"
                val macro = V4Macro(id, macroName, steps, confirm.isChecked)
                val updated = d.copy(macros = d.macros.filterNot { it.id == id } + macro)
                v4Definition = updated
                V4MacroStore.save(this, updated)
                Toast.makeText(this, "Macro saved.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun refreshWorkshopProfile() {
        val p = workshopProfiles.getOrNull(selectedProfileIndex)
        val mods = p?.modifications?.joinToString().orEmpty()
        workshopProfileLabel?.text = if (p == null) "No vehicle profile saved. Add a vehicle to begin." else
            "${p.name}\n${p.year ?: "Year unknown"} ${p.make} ${p.model}\nEngine: ${p.engine.ifBlank { "Not recorded" }}\nECU: ${p.ecu.ifBlank { "Not recorded" }}\nProtocol: ${p.protocol} | Fuel: ${p.fuel.ifBlank { "Not recorded" }}\nMass: ${p.massKg?.let { "${it} kg" } ?: "Not recorded"}\nModifications: $mods${if (p.notes.isNotBlank()) "\nNotes: ${p.notes}" else ""}"
    }

    private fun showAddVehicleDialog() {
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(dp(18), dp(6), dp(18), dp(4))
        val fields = listOf("Profile name", "Make", "Model", "Year", "Engine", "ECU / calibration", "Fuel", "Mass kg", "Modifications / notes")
        val edits = fields.map { hint -> EditText(this).apply { this.hint = hint; setSingleLine(hint != "Modifications / notes"); setTextColor(Theme.TEXT); setHintTextColor(Theme.MUTED); textSize = 14f; if (hint == "Modifications / notes") minLines = 2 } }
        edits.forEach { box.addView(it, lp(MATCH, WRAP, 0f, dp(2))) }
        val scroll = ScrollView(this); scroll.addView(box)
        AlertDialog.Builder(this).setTitle("Add vehicle profile").setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                val name = edits[0].text.toString().trim()
                val year = edits[3].text.toString().toIntOrNull()
                val mass = edits[7].text.toString().toDoubleOrNull()
                if (name.isBlank() || (edits[3].text.isNotBlank() && year == null) || (edits[7].text.isNotBlank() && mass == null)) {
                    Toast.makeText(this, "Enter a profile name and valid year/mass", Toast.LENGTH_LONG).show(); return@setPositiveButton
                }
                val notes = edits[8].text.toString()
                val profile = VehicleProfile(name = name, make = edits[1].text.toString(), model = edits[2].text.toString(), year = year,
                    engine = edits[4].text.toString(), ecu = edits[5].text.toString(), fuel = edits[6].text.toString(), massKg = mass,
                    notes = notes, modifications = notes.split("\n").map { it.trim() }.filter { it.isNotBlank() })
                workshopProfiles.add(profile); selectedProfileIndex = workshopProfiles.lastIndex
                VehicleProfileStore.save(this, workshopProfiles); refreshWorkshopProfile()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun deleteSelectedVehicle() {
        if (workshopProfiles.isEmpty()) return
        AlertDialog.Builder(this).setTitle("Delete vehicle profile?").setMessage("This removes the selected profile from this device.")
            .setPositiveButton("Delete") { _, _ -> workshopProfiles.removeAt(selectedProfileIndex); selectedProfileIndex = (selectedProfileIndex - 1).coerceAtLeast(0); VehicleProfileStore.save(this, workshopProfiles); refreshWorkshopProfile() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun exportWorkshopReport() {
        val o = lastObd
        val dtcs = mutableListOf<TroubleCode>()
        o?.storedDtc?.forEach { dtcs.add(TroubleCode(it, DtcStatus.STORED, DtcCatalog.description(it))) }
        o?.pendingDtc?.forEach { dtcs.add(TroubleCode(it, DtcStatus.PENDING, DtcCatalog.description(it))) }
        o?.permanentDtc?.forEach { dtcs.add(TroubleCode(it, DtcStatus.PERMANENT, DtcCatalog.description(it))) }
        val findings = o?.let { SensorSanity.inspect(it) } ?: emptyList()
        workshopReportText = DiagnosticReportText.build(workshopProfiles.getOrNull(selectedProfileIndex), o?.vin,
            o?.protocol ?: connectionMode + " (not currently connected)", dtcs, findings,
            if (o == null) "No generic OBD-II live sample available at report generation." else "Report generated from latest received generic OBD-II sample.")
        saveWorkshopReport.launch("MonitorTune_Diagnostic_${System.currentTimeMillis()}.txt")
    }

    private fun selectTab(k: Int) {
        activeTab = k
        badgeKey = ""
        navScroll.post { navScroll.smoothScrollTo(maxOf(0, tabButtons[k].left - dp(60)), 0) }
        for (i in tabs.indices) {
            tabButtons[i].background = rounded(if (i == k) Theme.BLUE else Theme.CARD, 10)
            if (i == k) {
                tabs[i].visibility = View.VISIBLE
                tabs[i].alpha = 0f
                tabs[i].translationY = dp(14).toFloat()
                tabs[i].animate().alpha(1f).translationY(0f).setDuration(200).start()
            } else {
                tabs[i].visibility = View.GONE
            }
        }
    }

    private fun setStatus(msg: String) {
        status.text = msg
        if (msg == "connected" || msg.startsWith("connected ELM327") || msg == "simulating") {
            status.setTextColor(Theme.GOOD)
            dot.set(Theme.GOOD, true)
        } else if (msg.startsWith("error") || msg.contains("denied") || msg.startsWith("no USB")) {
            status.setTextColor(Theme.BAD)
            dot.set(Theme.BAD, false)
        } else if (msg.startsWith("no frame") || msg == "re-syncing") {
            status.setTextColor(Theme.WARN)
            dot.set(Theme.WARN, true)
        } else {
            status.setTextColor(Theme.MUTED)
            dot.set(Theme.MUTED, false)
        }
    }

    // ---------------------------------------------------------------- connection
    private fun toggleConnect() {
        if (link != null) { link?.stopFlag = true; return }
        connectionMode = "ALDL"
        chkSim.isChecked = false
        connectUsbAldl()
    }

    private fun connectUsbAldl() {
        connectionMode = "ALDL"
        val m = getSystemService(Context.USB_SERVICE) as UsbManager
        val drv = UsbSerialProber.getDefaultProber().findAllDrivers(m).firstOrNull()
        if (drv == null) { setStatus("no USB serial device (OTG cable?)"); return }
        val dev = drv.device
        if (m.hasPermission(dev)) startLink(false) else {
            val i = Intent(ACTION_USB_PERMISSION); i.setPackage(packageName)
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            m.requestPermission(dev, PendingIntent.getBroadcast(this, 0, i, flags))
        }
    }

    private fun chooseElm327() {
        val bt = BluetoothAdapter.getDefaultAdapter()
        if (bt == null) { setStatus("error: this phone has no Bluetooth adapter"); return }
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), 712)
            Toast.makeText(this, "Allow Nearby devices, then tap Connect again.", Toast.LENGTH_LONG).show()
            return
        }
        if (!bt.isEnabled) { setStatus("error: turn Bluetooth on in Android settings"); return }
        val devices = try { bt.bondedDevices?.toList()?.sortedBy { it.name ?: it.address } ?: emptyList() }
            catch (_: SecurityException) { emptyList() }
        if (devices.isEmpty()) {
            AlertDialog.Builder(this).setTitle("No paired ELM327 found")
                .setMessage("Pair your Bluetooth Classic ELM327 adapter in Android Settings first. Common PINs are 1234 or 0000. Then return here and tap Connect.")
                .setPositiveButton("OK", null).show(); return
        }
        val labels = devices.map { d -> "${try { d.name ?: "Bluetooth device" } catch (_: SecurityException) { "Bluetooth device" }}  (${d.address})" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Select paired ELM327 adapter")
            .setItems(labels) { _, which -> startElmLink(bt, devices[which]) }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if ((requestCode == 712 || requestCode == 713) && grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth permission granted. Tap Connect again and select the paired adapter.", Toast.LENGTH_LONG).show()
        }
    }

    private fun startElmLink(bt: BluetoothAdapter, device: BluetoothDevice) {
        if (elmLink != null || link != null) return
        connectionMode = "OBD-II"
        connectedAt = System.currentTimeMillis()
        if (alertSounds.alarmVolumeZero()) Toast.makeText(this, "Alarm volume is 0 - audio alerts will be silent.", Toast.LENGTH_LONG).show()
        val l = Elm327Link(bt, device,
            { obd -> runOnUiThread { handleObdSample(obd) } },
            { msg -> runOnUiThread {
                when {
                    msg == "DTC_CLEAR_OK" -> {
                        setStatus("ECU accepted clear command • DTCs reread")
                        Toast.makeText(this, "ECU accepted Mode 04. Check the refreshed code list; permanent codes may remain.", Toast.LENGTH_LONG).show()
                    }
                    msg.startsWith("DTC_CLEAR_FAILED:") -> {
                        setStatus("ECU did not confirm DTC clear")
                        Toast.makeText(this, "DTC clear failed: ${msg.substringAfter(":").trim()}", Toast.LENGTH_LONG).show()
                    }
                    msg == "DTC_READ_COMPLETE" -> {
                        setStatus("ECU fault codes refreshed")
                        Toast.makeText(this, "ECU fault-code read complete. See Stored, Pending and Permanent DTCs above.", Toast.LENGTH_SHORT).show()
                    }
                    else -> setStatus(msg)
                }
                if (msg == "disconnected") {
                    elmLink = null
                    btnConnect.text = "Connect"
                    btnConnect.background = rounded(Color.parseColor("#0E9F6E"), 10)
                }
            } })
        elmLink = l
        btnConnect.text = "Disconnect"
        btnConnect.background = rounded(Color.parseColor("#B91C1C"), 10)
        l.start()
    }

    private fun handleObdSample(o: ObdLive) {
        lastObd = o
        aiMonitor.widebandAvailable = false
        aiMonitor.boostMonitoringAvailable = false
        aiMonitor.knockMonitoringAvailable = false
        aiMonitor.rpmLimitAvailable = false
        aiMonitor.staleAfterMs = 6000L
        val map = o.mapKpa ?: 0.0
        val baro = o.baroKpa ?: 0.0
        val boost = if (o.mapKpa != null && o.baroKpa != null) map - baro else 0.0
        val s = Sample(
            rpm = o.rpm ?: 0.0, speed = o.speedKph ?: 0.0, coolant = o.coolantC ?: 0.0,
            baro = baro, map = map, baroCompMap = map, boostKpa = boost,
            boostPsi = boost * 0.145038, tps = o.tpsPct ?: 0.0,
            spark = o.timingDeg ?: 0.0, currentVe = 0.0, calcVe = 0.0,
            wbAfr = 0.0, cmdAfr = 0.0, knockRetard = 0.0, knockCounts = 0, wbRaw = 0
        )
        handleSample(s)
        aiMonitor.addObdCoverage(o)
        fun fmt(x: Double?, unit: String = "") = x?.let { String.format(Locale.US, "%.1f%s", it, unit) } ?: "N/A"
        fun dtcText(label: String, codes: List<String>) = "$label: " + if (codes.isEmpty()) "None reported" else codes.joinToString("\n") { code -> "$code — ${DtcCatalog.description(code)}" }
        val plausibility = SensorSanity.inspect(o).take(3).joinToString("\n") { "${it.severity}: ${it.sensor} — ${it.message}" }
        obdLiveReadout?.text = "OBD-II LIVE • ${o.protocol}\nRPM ${fmt(o.rpm)}   Speed ${fmt(o.speedKph, " km/h")}   Coolant ${fmt(o.coolantC, " °C")}\nMAP ${fmt(o.mapKpa, " kPa")}   Baro ${fmt(o.baroKpa, " kPa")}   IAT ${fmt(o.intakeC, " °C")}\nThrottle ${fmt(o.tpsPct, "%")}   Timing ${fmt(o.timingDeg, "°")}   Load ${fmt(o.loadPct, "%")}\nMAF ${fmt(o.mafGps, " g/s")}   Module voltage ${fmt(o.moduleVoltage, " V")}   Fuel rail ${fmt(o.fuelRailKpa, " kPa")}\nSupported standard PIDs: ${o.supportedPids.size}\nVIN: ${o.vin ?: "Not supplied"}\n${dtcText("Stored DTCs", o.storedDtc)}\n${dtcText("Pending DTCs", o.pendingDtc)}\n${dtcText("Permanent DTCs", o.permanentDtc)}\nFreeze-frame response: ${if (o.freezeFrameAvailable) "Available (raw support check only)" else "Unavailable"}${if (plausibility.isBlank()) "" else "\nSensor plausibility checks (verify vehicle-specific limits):\n$plausibility"}"
        capView.text = "GENERIC OBD-II — standard PID data; AFR/VE capture unavailable"
        capView.setTextColor(Theme.WARN)
        if (logging) {
            val n = Double.NaN
            logRows.add(doubleArrayOf((System.currentTimeMillis() - logStart) / 1000.0) + doubleArrayOf(
                o.rpm ?: n, o.speedKph ?: n, o.coolantC ?: n, o.mapKpa ?: n, o.baroKpa ?: n,
                o.intakeC ?: n, o.tpsPct ?: n, o.timingDeg ?: n, o.loadPct ?: n, o.mafGps ?: n,
                o.moduleVoltage ?: n, o.fuelRailKpa ?: n
            ))
        }
        aiDataStatus?.text = "LIVE GENERIC OBD-II • ${o.protocol}"
    }

    private fun startLink(sim: Boolean) {
        if (link != null || elmLink != null) return
        connectedAt = System.currentTimeMillis()
        if (alertSounds.alarmVolumeZero()) Toast.makeText(this, "Alarm volume is 0 - audio alerts will be silent.", Toast.LENGTH_LONG).show()
        aiMonitor.boostMonitoringAvailable = true
        aiMonitor.knockMonitoringAvailable = true
        aiMonitor.rpmLimitAvailable = true
        aiMonitor.widebandAvailable = true
        aiMonitor.staleAfterMs = 1500L
        obdLiveReadout?.text = "OSE $11P ALDL data mode."
        val m = getSystemService(Context.USB_SERVICE) as UsbManager
        val l = AldlLink(m, sim,
            { s -> runOnUiThread { handleSample(s) } },
            { msg ->
                runOnUiThread {
                    setStatus(msg)
                    if (msg == "disconnected") {
                        link = null
                        btnConnect.text = "Connect"
                        btnConnect.background = rounded(Color.parseColor("#0E9F6E"), 10)
                    }
                }
            })
        link = l
        btnConnect.text = "Disconnect"
        btnConnect.background = rounded(Color.parseColor("#B91C1C"), 10)
        l.start()
    }

    private fun handleSample(s: Sample) {
        s.knockDelta = if (prevKnock < 0) 0 else (s.knockCounts - prevKnock) and 0xFF
        prevKnock = s.knockCounts
        last = s
        if (connectionMode != "OBD-II") { aiMonitor.widebandAvailable = true; aiMonitor.staleAfterMs = 1500L }
        aiMonitor.update(s)
        val ok = s.wbAfr >= Cfg.WB_MIN && s.wbAfr <= Cfg.WB_MAX
        if (ok) {
            capView.text = String.format(Locale.US, "CAPTURING (wideband %.1f)", s.wbAfr)
            capView.setTextColor(Theme.GOOD)
        } else {
            capView.text = String.format(Locale.US, "PAUSED - wideband %.1f outside %.0f-%.0f", s.wbAfr, Cfg.WB_MIN, Cfg.WB_MAX)
            capView.setTextColor(Theme.WARN)
        }
        if (logging && ok) logRows.add(doubleArrayOf((System.currentTimeMillis() - logStart) / 1000.0) + s.row())
        if (connectionMode != "OBD-II") {
            ve?.add(s)
            sp?.add(s)
        }
        if (dynoActive) { roadDyno?.add(s); if (activeTab == 4) refreshDyno() }
    }

    // ---------------------------------------------------------------- logging / saving
    private fun toggleLog() {
        if (!logging) {
            logRows.clear()
            activeLogColumns = if (connectionMode == "OBD-II") listOf("RPM", "Speed_kph", "Coolant_C", "MAP_kPa", "Baro_kPa", "IntakeAir_C", "TPS_pct", "TimingAdvance_deg", "CalculatedLoad_pct", "MAF_gps", "ModuleVoltage_V", "FuelRailPressure_kPa") else Sample.COLUMNS
            logStart = System.currentTimeMillis()
            logging = true
            btnLog.text = "Stop Log + Save"
            btnLog.background = rounded(Color.parseColor("#B91C1C"), 10)
            val a = ValueAnimator.ofFloat(0.55f, 1f)
            a.duration = 700
            a.repeatMode = ValueAnimator.REVERSE
            a.repeatCount = ValueAnimator.INFINITE
            a.addUpdateListener { btnLog.alpha = it.animatedValue as Float }
            a.start()
            logPulse = a
        } else {
            logging = false
            logPulse?.cancel()
            btnLog.alpha = 1f
            btnLog.text = "Start Log"
            btnLog.background = rounded(Color.parseColor("#2563EB"), 10)
            logInfo.text = ""
            if (logRows.isEmpty()) return
            AlertDialog.Builder(this)
                .setTitle("Save log as")
                .setItems(arrayOf("CSV (comma)", "TSV (tab)")) { _, which ->
                    val d = if (which == 0) "," else "\t"
                    pendingBytes = buildLog(d).toByteArray()
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    if (which == 0) saveCsv.launch("log_$stamp.csv") else saveTsv.launch("log_$stamp.tsv")
                }
                .show()
        }
    }

    private fun buildLog(d: String): String {
        val sb = StringBuilder()
        sb.append("Time_s").append(d).append(activeLogColumns.joinToString(d)).append("\n")
        for (r in logRows) {
            sb.append(r.joinToString(d) { String.format(Locale.US, "%.3f", it) }).append("\n")
        }
        return sb.toString()
    }

    private fun writeUri(uri: Uri) {
        val b = pendingBytes ?: return
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(b) }
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: " + e.message, Toast.LENGTH_LONG).show()
        }
        pendingBytes = null
    }

    // ---------------------------------------------------------------- BIN tools
    private fun loadBin(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            if (loadTarget == "ve") {
                ve = VeFill(bytes)
                veInfo.text = "BIN loaded. Cyan border = sampled; dim = current table value."
            } else {
                sp = SparkKnock(bytes)
                spInfo.text = "BIN loaded. Red border = proposed retard."
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Load failed: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun writeVe() {
        val v = ve ?: return
        val r = v.write(chkFill.isChecked, if (chkSmooth.isChecked) 0.5 else 0.0)
        pendingBytes = r.first
        Toast.makeText(this, "${r.second} cells changed - choose file name", Toast.LENGTH_SHORT).show()
        saveBin.launch("OSE11P_VE_tuned.bin")
    }

    private fun writeSp() {
        val v = sp ?: return
        val r = v.write()
        pendingBytes = r.first
        Toast.makeText(this, "${r.second} cells retarded - choose file name", Toast.LENGTH_SHORT).show()
        saveBin.launch("OSE11P_spark_corrected.bin")
    }

    // ---------------------------------------------------------------- display
    private fun animateAfrColor(to: Int) {
        if (to == afrColor) return
        val from = afrColor
        afrColor = to
        val a = ValueAnimator.ofArgb(from, to)
        a.duration = 300
        a.addUpdateListener {
            val c = it.animatedValue as Int
            tileVals["WidebandAFR"]?.setTextColor(c)
        }
        a.start()
    }

    private fun refresh() {
        alertTick()
        val s = last
        if (s != null) {
            when (activeTab) {
                0 -> {
                    val vals = s.row()
                    for ((k, tv) in tileVals) {
                        val idx = Sample.COLUMNS.indexOf(k)
                        if (idx >= 0) tv.text = String.format(Locale.US, "%." + (tileDec[k] ?: 1) + "f", vals[idx])
                    }
                    if (connectionMode == "OBD-II") {
                        val o = lastObd
                        val unavailable = setOf("CurrentVE", "CalculatedVE", "WidebandAFR", "CommandedAFR", "KnockRetard_deg", "KnockCounts", "KnockDelta", "WBRaw")
                        for (key in unavailable) tileVals[key]?.text = "N/A"
                        if (o != null) {
                            tileVals["RPM"]?.text = o.rpm?.let { String.format(Locale.US, "%.0f", it) } ?: "N/A"
                            tileVals["Speed_kph"]?.text = o.speedKph?.let { String.format(Locale.US, "%.0f", it) } ?: "N/A"
                            tileVals["Coolant_C"]?.text = o.coolantC?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A"
                            tileVals["MAP_kPa"]?.text = o.mapKpa?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A"
                            tileVals["TPS_pct"]?.text = o.tpsPct?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A"
                            tileVals["Spark_deg"]?.text = o.timingDeg?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A"
                        }
                    }

                    val ast = aiMonitor.settings
                    fun lvl(v: Double, w: Double, c: Double) = if (v >= c) Theme.BAD else if (v >= w) Theme.WARN else Theme.TEXT
                    tileVals["Coolant_C"]?.setTextColor(lvl(s.coolant, ast.warningCoolantC, ast.criticalCoolantC))
                    if (connectionMode != "OBD-II") {
                        tileVals["BoostPSI"]?.setTextColor(lvl(s.boostPsi, ast.warningBoostPsi, ast.criticalBoostPsi))
                        tileVals["KnockRetard_deg"]?.setTextColor(lvl(s.knockRetard, ast.warningKnockDeg, ast.criticalKnockDeg))
                    } else {
                        tileVals["BoostPSI"]?.setTextColor(Theme.TEXT)
                        tileVals["KnockRetard_deg"]?.setTextColor(Theme.TEXT)
                    }
                    val err = abs(s.wbAfr - s.cmdAfr)
                    animateAfrColor(if (err < 0.3) Theme.GOOD else if (err < 0.8) Theme.WARN else Theme.BAD)
                    if (logging) logInfo.text = "recording: ${logRows.size} rows"
                }
                1 -> {
                    gRpm.setValue(s.rpm.toFloat())
                    gSpeed.setValue(s.speed.toFloat())
                    gBoost.setValue(s.boostPsi.toFloat())
                    gCool.setValue(s.coolant.toFloat())
                    gSpark.setValue(s.spark.toFloat())
                    dashAfr.text = if (connectionMode == "OBD-II") "N/A" else String.format(Locale.US, "%.1f", s.wbAfr)
                    dashMap.text = String.format(Locale.US, "%.1f kPa", s.map)
                    dashTps.text = String.format(Locale.US, "%.1f%%", s.tps)
                    dashKnock.text = if (connectionMode == "OBD-II") "N/A" else String.format(Locale.US, "%.1f°", s.knockRetard)
                    dashAfr.setTextColor(if (connectionMode == "OBD-II") Theme.MUTED else if (abs(s.wbAfr - s.cmdAfr) < 0.3) Theme.GOOD else if (abs(s.wbAfr - s.cmdAfr) < 0.8) Theme.WARN else Theme.BAD)
                    dashKnock.setTextColor(if (s.knockRetard >= aiMonitor.settings.criticalKnockDeg) Theme.BAD else if (s.knockRetard >= aiMonitor.settings.warningKnockDeg) Theme.WARN else Theme.TEXT)
                }
            }
        }
        if (activeTab == 0) {
            renderVe()
            last?.let { sample ->
                veRpmGauge.setValue(sample.rpm.toFloat())
                veBoostGauge.setValue(sample.boostPsi.toFloat())
                veCoolantGauge.setValue(sample.coolant.toFloat())
            }
        }
        if (activeTab == 3) renderSp()
        if (activeTab == 4) refreshDyno()
        if (activeTab == 5) refreshAi()
    }

    // ---------------------------------------------------------------- alerts (v1.5)
    private fun alertTick() {
        val now = System.currentTimeMillis()
        val connected = link != null || elmLink != null
        val lastAt = aiMonitor.lastSampleAt
        val fresh = connected && lastAt >= connectedAt && now - lastAt <= aiMonitor.staleAfterMs
        // data lost = had live samples this session and they stopped, or link up but nothing for 15 s
        val dataLost = connected && !fresh && (lastAt >= connectedAt || now - connectedAt > 15000L)
        val real = if (fresh) aiMonitor.alerts else emptyList()
        applyAlertUi(alertEngine.evaluate(now, real, dataLost))
    }

    private fun applyAlertUi(act: List<ActiveAlert>) {
        val sev = if (act.any { it.sev == Sev.CRITICAL }) Sev.CRITICAL else if (act.any { it.sev == Sev.CAUTION }) Sev.CAUTION else Sev.NONE
        alertOverlay.setSeverity(sev)
        if (sev == Sev.NONE) {
            if (bannerSev != Sev.NONE) { bannerSev = Sev.NONE; hideBanner() }
        } else {
            val top = act.first()
            bannerTitle.text = (if (top.sev == Sev.CRITICAL) "CRITICAL  " else "CAUTION  ") + top.title
            val more = act.size - 1
            bannerDetail.text = top.detail + (if (more > 0) "   (+$more more)" else "")
            val allAcked = act.all { it.acked }
            bannerSilence.text = if (allAcked) "SILENCED" else if (!alertEngine.soundOn && !alertEngine.vibrateOn) "MUTED" else "SILENCE"
            bannerSilence.alpha = if (allAcked) 0.5f else 1f
            if (bannerSev != sev) showBanner(sev)
        }
        updateNavBadge(sev)
    }

    private fun showBanner(sev: Sev) {
        bannerSev = sev
        val base = if (sev == Sev.CRITICAL) Theme.BAD else Theme.WARN
        bannerBg.setColor(if (sev == Sev.CRITICAL) Color.argb(240, 84, 12, 18) else Color.argb(240, 84, 58, 8))
        bannerIcon.setTextColor(base)
        bannerAnim?.cancel()
        val a = ValueAnimator.ofFloat(0.35f, 1f)
        a.duration = if (sev == Sev.CRITICAL) 350 else 1100
        a.repeatMode = ValueAnimator.REVERSE
        a.repeatCount = ValueAnimator.INFINITE
        a.addUpdateListener {
            val f = it.animatedValue as Float
            bannerBg.setStroke(dp(2), Color.argb((255 * f).toInt(), Color.red(base), Color.green(base), Color.blue(base)))
            bannerIcon.alpha = 0.4f + 0.6f * f
        }
        a.start()
        bannerAnim = a
        banner.animate().cancel()
        if (banner.visibility != View.VISIBLE) {
            banner.visibility = View.VISIBLE
            banner.alpha = 0f
            banner.translationY = -dp(40).toFloat()
        }
        banner.animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    private fun hideBanner() {
        bannerAnim?.cancel()
        bannerAnim = null
        banner.animate().cancel()
        banner.animate().alpha(0f).translationY(-dp(40).toFloat()).setDuration(200)
            .withEndAction { if (bannerSev == Sev.NONE) banner.visibility = View.GONE }.start()
    }

    private fun updateNavBadge(sev: Sev) {
        return
        val key = if (activeTab != 5 && sev != Sev.NONE) sev.name else "off"
        if (key == badgeKey) return
        badgeKey = key
        navPulse?.cancel()
        navPulse = null
        val b = tabButtons[5]
        if (key == "off") {
            b.alpha = 1f
            b.background = rounded(if (activeTab == 5) Theme.BLUE else Theme.CARD, 10)
            return
        }
        b.background = rounded(if (sev == Sev.CRITICAL) Theme.BAD else Theme.WARN, 10)
        val a = ValueAnimator.ofFloat(0.45f, 1f)
        a.duration = if (sev == Sev.CRITICAL) 350 else 900
        a.repeatMode = ValueAnimator.REVERSE
        a.repeatCount = ValueAnimator.INFINITE
        a.addUpdateListener { b.alpha = it.animatedValue as Float }
        a.start()
        navPulse = a
    }

    private fun updateMuteButton() {
        btnMute.text = if (alertEngine.soundOn) "Sound ON" else "Muted"
        btnMute.background = rounded(if (alertEngine.soundOn) Color.parseColor("#374151") else Color.parseColor("#92400E"), 10)
    }

    private fun toggleMute() {
        alertEngine.soundOn = !alertEngine.soundOn
        if (!alertEngine.soundOn) alertSounds.stopSpeech()
        else if (alertSounds.alarmVolumeZero()) Toast.makeText(this, "Alarm volume is 0 - raise it with the volume keys.", Toast.LENGTH_LONG).show()
        saveAlertPrefs()
        updateMuteButton()
    }

    private fun runAlertTest(sev: Sev) {
        if (alertSounds.alarmVolumeZero()) Toast.makeText(this, "Alarm volume is 0 - raise it with the volume keys.", Toast.LENGTH_LONG).show()
        alertEngine.test(sev)
    }

    private fun refreshAi() {
        run {
            val stale = aiMonitor.lastSampleAt == 0L || System.currentTimeMillis() - aiMonitor.lastSampleAt > aiMonitor.staleAfterMs
            val al = aiMonitor.alerts
            val c = if (stale) Theme.MUTED else if (al.any { it.level == AiMonitor.Level.RED }) Theme.BAD else if (al.any { it.level == AiMonitor.Level.AMBER }) Theme.WARN else Theme.GOOD
            val cap = if (geminiBusy) "GEMINI ANALYSING" else if (stale) "STANDBY - NO LIVE DATA" else "LIVE - ${al.size} ALERT(S)"
            aiPulse.set(c, !stale, geminiBusy, cap)
            if (geminiBusy) geminiOutput?.text = "Analysing live data with Gemini" + ".".repeat(((System.currentTimeMillis() / 400L) % 4L).toInt())
        }
        aiDataStatus?.text = if (connectionMode == "OBD-II" && lastObd != null) "LIVE OBD-II • ${lastObd!!.protocol} • ${lastObd!!.supportedPids.size} supported PIDs • ${aiMonitor.dataStatus()}" else aiMonitor.dataStatus()
        aiDataStatus?.setTextColor(if (aiMonitor.lastSampleAt == 0L || System.currentTimeMillis() - aiMonitor.lastSampleAt > aiMonitor.staleAfterMs) Theme.WARN else Theme.GOOD)
        val alerts = aiMonitor.alerts
        aiSummary?.text = if (aiMonitor.lastSampleAt == 0L || System.currentTimeMillis() - aiMonitor.lastSampleAt > aiMonitor.staleAfterMs) "DATA NOT CURRENT — warnings cannot be trusted" else aiMonitor.summary
        aiSummary?.setTextColor(if (alerts.any { it.level == AiMonitor.Level.RED }) Theme.BAD else if (alerts.any { it.level == AiMonitor.Level.AMBER }) Theme.WARN else Theme.GOOD)
        val sumSev = if (aiMonitor.lastSampleAt == 0L || System.currentTimeMillis() - aiMonitor.lastSampleAt > aiMonitor.staleAfterMs) Sev.NONE
            else if (alerts.any { it.level == AiMonitor.Level.RED }) Sev.CRITICAL else if (alerts.any { it.level == AiMonitor.Level.AMBER }) Sev.CAUTION else Sev.NONE
        if (sumSev != summarySev) {
            summarySev = sumSev
            aiSummary?.background = rounded(when (sumSev) {
                Sev.CRITICAL -> Color.argb(235, 84, 12, 18)
                Sev.CAUTION -> Color.argb(235, 84, 58, 8)
                Sev.NONE -> Theme.CARD
            }, 12)
        }
        val box = aiAlertsBox ?: return
        if (box.tag == alerts.hashCode().toString() + aiMonitor.lastSampleAt / 1000L) return
        box.tag = alerts.hashCode().toString() + aiMonitor.lastSampleAt / 1000L
        val sig = alerts.joinToString("|") { it.level.name + it.title }
        val animateIn = sig != alertSig
        alertSig = sig
        cardPulse?.cancel()
        cardPulse = null
        redCards.clear()
        box.removeAllViews()
        if (alerts.isEmpty()) {
            val t = label("No current rule alerts. This does not guarantee the engine is safe; confirm sensor accuracy and tune on a proper dyno.", 14f, Theme.MUTED)
            t.setPadding(dp(12), dp(12), dp(12), dp(12)); t.background = rounded(Theme.CARD, 10); box.addView(t, lp(MATCH, WRAP, 0f, dp(3)))
        } else for ((idx, a) in alerts.withIndex()) {
            val color = when (a.level) { AiMonitor.Level.RED -> Theme.BAD; AiMonitor.Level.AMBER -> Theme.WARN; AiMonitor.Level.INFO -> Theme.BLUE }
            val card = LinearLayout(this); card.orientation = LinearLayout.VERTICAL; card.setPadding(dp(12), dp(9), dp(12), dp(9))
            val cardBg = rounded(Theme.CARD, 10)
            if (a.level == AiMonitor.Level.RED) { cardBg.setStroke(dp(2), Theme.BAD); redCards.add(cardBg) }
            else if (a.level == AiMonitor.Level.AMBER) cardBg.setStroke(dp(1), Color.argb(170, 245, 158, 11))
            card.background = cardBg
            val title = label((if (a.level == AiMonitor.Level.RED) "\u25B2 CRITICAL • " else if (a.level == AiMonitor.Level.AMBER) "\u25B2 CAUTION • " else "INFO • ") + a.title, 14f, color)
            title.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            card.addView(title); card.addView(label(a.detail, 12f, Theme.TEXT), lp(MATCH, WRAP, 0f, dp(4)))
            box.addView(card, lp(MATCH, WRAP, 0f, dp(4)))
            if (animateIn) {
                card.alpha = 0f
                card.translationX = dp(28).toFloat()
                card.animate().alpha(1f).translationX(0f).setStartDelay(idx * 70L).setDuration(260).start()
            }
        }
        if (redCards.isNotEmpty()) {
            val pa = ValueAnimator.ofFloat(0.3f, 1f)
            pa.duration = 400
            pa.repeatMode = ValueAnimator.REVERSE
            pa.repeatCount = ValueAnimator.INFINITE
            pa.addUpdateListener {
                val f = it.animatedValue as Float
                for (g in redCards) g.setStroke(dp(2), Color.argb((255 * f).toInt(), 239, 68, 68))
            }
            pa.start()
            cardPulse = pa
        }
    }

    private fun showGeminiKeyDialog() {
        val input = EditText(this)
        input.setSingleLine(true)
        input.inputType = 129 // text password
        input.hint = "Paste Gemini API key"
        input.setTextColor(Theme.TEXT)
        input.setText(geminiApiKey)
        val wrap = LinearLayout(this); wrap.orientation = LinearLayout.VERTICAL; wrap.setPadding(dp(16), 0, dp(16), 0)
        wrap.addView(input)
        AlertDialog.Builder(this).setTitle("Gemini API key")
            .setMessage("The key is kept in memory only until the app closes. It is not written into the APK or saved to preferences. For a production/public app, use your own authenticated backend instead.")
            .setView(wrap).setNegativeButton("Cancel", null)
            .setNeutralButton("Clear key") { _, _ -> geminiApiKey = ""; geminiOutput?.text = "Gemini key cleared." }
            .setPositiveButton("Use key") { _, _ ->
                geminiApiKey = input.text.toString().trim()
                geminiOutput?.text = if (geminiApiKey.isBlank()) "No Gemini key set." else "Gemini key loaded for this app session. Tap Analyse Live Data when ECU data is current."
            }.show()
    }

    private fun askGemini() {
        val sample = last
        if (sample == null || aiMonitor.lastSampleAt == 0L || System.currentTimeMillis() - aiMonitor.lastSampleAt > aiMonitor.staleAfterMs) {
            Toast.makeText(this, "No current ECU data. Connect and wait for live samples.", Toast.LENGTH_LONG).show()
            return
        }
        if (geminiApiKey.isBlank()) { showGeminiKeyDialog(); return }
        val key = geminiApiKey
        val obd = if (connectionMode == "OBD-II") lastObd else null
        geminiOutput?.text = if (obd != null) "Sending generic OBD-II snapshot to Gemini…" else "Sending OSE $11P ALDL snapshot to Gemini…"
        geminiBusy = true
        geminiExecutor.execute {
            try {
                val result = if (obd != null) GeminiDoctor.analyseObd(key, obd) else GeminiDoctor.analyse(key, sample, aiMonitor.alerts)
                runOnUiThread { geminiBusy = false; geminiOutput?.text = result }
            } catch (e: Exception) {
                runOnUiThread { geminiBusy = false; geminiOutput?.text = "Gemini request failed: ${e.message ?: "Unknown error"}. Check internet access, API key and Gemini API access." }
            }
        }
    }

    private fun showAiSettings() {
        val st = aiMonitor.settings
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(dp(16), 0, dp(16), 0)
        fun field(title: String, value: Double): EditText {
            box.addView(label(title, 12f, Theme.MUTED))
            val e = EditText(this); e.setSingleLine(true); e.setText(value.toString()); e.inputType = 8194; e.setTextColor(Theme.TEXT); box.addView(e)
            return e
        }
        val wc = field("Coolant warning °C", st.warningCoolantC)
        val cc = field("Coolant critical °C", st.criticalCoolantC)
        val wb = field("Boost warning psi", st.warningBoostPsi)
        val cb = field("Boost critical psi", st.criticalBoostPsi)
        val afr = field("Lean AFR alert under boost", st.boostAfrLimit)
        val wk = field("Knock retard warning °", st.warningKnockDeg)
        val ck = field("Knock retard critical °", st.criticalKnockDeg)
        AlertDialog.Builder(this).setTitle("Live alert thresholds")
            .setMessage("These are warning thresholds only, not recommended AFR, boost or ignition targets. Set them to suit verified sensors and engine limits.")
            .setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
                val values = listOf(wc,cc,wb,cb,afr,wk,ck).map { it.text.toString().toDoubleOrNull() }
                if (values.all { it != null } && values[0]!! < values[1]!! && values[2]!! < values[3]!! && values[5]!! < values[6]!! && values[4]!! in 10.0..18.0) {
                    st.warningCoolantC=values[0]!!; st.criticalCoolantC=values[1]!!; st.warningBoostPsi=values[2]!!; st.criticalBoostPsi=values[3]!!; st.boostAfrLimit=values[4]!!; st.warningKnockDeg=values[5]!!; st.criticalKnockDeg=values[6]!!
                    getSharedPreferences("ai_monitor", MODE_PRIVATE).edit().putString("thresholds", values.joinToString(",")).apply()
                    Toast.makeText(this, "Alert thresholds saved", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this, "Invalid thresholds or warning must be below critical", Toast.LENGTH_LONG).show()
                refreshAi()
            }.show()
    }

    private fun loadAiSettings() {
        val raw = getSharedPreferences("ai_monitor", MODE_PRIVATE).getString("thresholds", null) ?: return
        val v = raw.split(",").mapNotNull { it.toDoubleOrNull() }
        if (v.size == 7 && v[0] < v[1] && v[2] < v[3] && v[5] < v[6] && v[4] in 10.0..18.0) {
            aiMonitor.settings = AiMonitor.Settings(v[0],v[1],v[2],v[3],v[4],v[5],v[6])
        }
    }

    private fun refreshDyno() {
        dynoChart?.runs = dynoRuns.toList()
        if (dynoActive) dynoInfo?.text = "ROAD RUN RECORDING • ${roadDyno?.finish("live")?.points?.size ?: 0} valid samples • ${String.format(Locale.US, "%.0f", dynoMassKg)} kg"
    }

    private fun addMeasuredDyno() {
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(dp(12),0,dp(12),0)
        val name = EditText(this); name.hint = "Run name (e.g. Dynojet 4th gear)"; name.setTextColor(Theme.TEXT)
        val hp = EditText(this); hp.hint = "Measured peak horsepower (HP)"; hp.inputType = 8194; hp.setTextColor(Theme.TEXT)
        val tq = EditText(this); tq.hint = "Measured peak torque (Nm)"; tq.inputType = 8194; tq.setTextColor(Theme.TEXT)
        val rpm = EditText(this); rpm.hint = "RPM at peak horsepower"; rpm.inputType = 2; rpm.setTextColor(Theme.TEXT)
        box.addView(name); box.addView(hp); box.addView(tq); box.addView(rpm)
        AlertDialog.Builder(this).setTitle("Add measured dyno result").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val h=hp.text.toString().toDoubleOrNull(); val t=tq.text.toString().toDoubleOrNull(); val r=rpm.text.toString().toDoubleOrNull()
                if (h == null || t == null || r == null || h !in 1.0..3000.0 || t !in 1.0..5000.0 || r !in 500.0..12000.0) Toast.makeText(this,"Enter valid HP, Nm and RPM",Toast.LENGTH_LONG).show()
                else { dynoRuns.add(DynoRun(name.text.toString().ifBlank { "Measured ${dynoRuns.size+1}" },"MEASURED DYNO", listOf(DynoPoint(r,h,t,0.0)))); DynoStore.save(this,dynoRuns); refreshDyno() }
            }.setNegativeButton("Cancel",null).show()
    }

    private fun exportDyno() {
        if (dynoRuns.isEmpty()) { Toast.makeText(this,"No dyno runs saved",Toast.LENGTH_SHORT).show(); return }
        val sb=StringBuilder("Run,Mode,RPM,HP,Torque_Nm,Speed_kph\n")
        for (run in dynoRuns) for (p in run.points) sb.append("\"${run.name.replace("\"","'")}\",${run.mode},${p.rpm},${p.hp},${p.torque},${p.speed}\n")
        pendingBytes=sb.toString().toByteArray(); saveCsv.launch("OSE11P_Dyno_Runs.csv")
    }

    private fun renderVe() {
        val v = ve ?: return
        val r = v.compute(chkFill.isChecked, if (chkSmooth.isChecked) 0.5 else 0.0)
        for (ti in Cfg.VE_TABLES.indices) {
            val t = Cfg.VE_TABLES[ti]
            val cells = ArrayList<GridCell>()
            for (i in Cfg.RPM_VE.indices) for (j in t.mapAxis.indices) {
                val x = r.values[ti][i][j]
                cells.add(GridCell(String.format(Locale.US, "%.1f", x), x.toFloat(), r.measured[ti][i][j], false, !r.touched[ti][i][j]))
            }
            veGrids[ti].setData(
                Cfg.RPM_VE.map { String.format(Locale.US, "%.0f", it) },
                t.mapAxis.map { String.format(Locale.US, "%.0f", it) },
                cells
            )
        }
    }

    private fun renderSp() {
        val v = sp ?: return
        for (ti in Cfg.SPARK_TABLES.indices) {
            val t = Cfg.SPARK_TABLES[ti]
            val cells = ArrayList<GridCell>()
            for (i in Cfg.RPM_SPARK.indices) for (j in t.mapAxis.indices) {
                val cur = v.current(ti, i, j)
                val red = v.reduction(ti, i, j)
                if (red > 0) {
                    cells.add(GridCell(String.format(Locale.US, "%.1f\n-%.1f", cur, red), cur.toFloat(), false, true, false))
                } else {
                    cells.add(GridCell(String.format(Locale.US, "%.1f", cur), cur.toFloat(), false, false, false))
                }
            }
            spGrids[ti].setData(
                Cfg.RPM_SPARK.map { String.format(Locale.US, "%.0f", it) },
                t.mapAxis.map { String.format(Locale.US, "%.0f", it) },
                cells
            )
        }
    }
}