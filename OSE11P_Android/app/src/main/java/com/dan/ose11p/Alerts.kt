package com.dan.ose11p

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

enum class Sev { NONE, CAUTION, CRITICAL }

class ActiveAlert(
    val key: String,
    val sev: Sev,
    val title: String,
    val detail: String,
    val acked: Boolean
)

/**
 * Turns the monitor's rule results into confirmed, de-bounced, acknowledgeable alerts and drives
 * audio / voice / vibration. Visuals are applied by MainActivity from the returned list.
 *
 * - An alert must persist CONFIRM_MS before it is raised (ignores single-sample glitches).
 * - It is held HOLD_MS after the condition clears (no flapping).
 * - CRITICAL: urgent triple-beep repeating every 1.5 s until cleared or silenced; spoken once on onset.
 * - CAUTION: two-tone chime on onset, repeated every 12 s while present.
 * - ECU DATA LOST: descending tone (caution level) when live data stops.
 * - Silence acknowledges everything currently active; any NEW alert sounds again.
 */
class AlertEngine(private val sounds: AlertSounds) {
    var soundOn = true
    var voiceOn = true
    var vibrateOn = true

    private class St(
        val key: String, val sev: Sev, val title: String, val first: Long, val data: Boolean
    ) {
        var last: Long = first
        var detail: String = ""
        var acked = false
        var announced = false
    }

    private val states = LinkedHashMap<String, St>()
    private var nextCrit = 0L
    private var nextCaution = 0L
    private var testUntil = 0L
    private var testSev = Sev.CAUTION

    companion object {
        const val CONFIRM_MS = 400L
        const val HOLD_MS = 1500L
        // Rev reminder is shown on the RPM gauge red zone and the AI Doctor card only (no banner/sound).
        private val QUIET_TITLES = setOf("High engine speed")
    }

    fun test(sev: Sev, now: Long = System.currentTimeMillis()) {
        testSev = sev
        testUntil = now + 4000L
        states.remove("TEST")
    }

    fun acknowledge() {
        for (s in states.values) s.acked = true
        sounds.stopSpeech()
    }

    fun evaluate(now: Long, real: List<AiMonitor.Alert>, dataLost: Boolean): List<ActiveAlert> {
        val seen = HashSet<String>()
        fun see(key: String, sev: Sev, title: String, detail: String, data: Boolean) {
            seen.add(key)
            val s = states.getOrPut(key) { St(key, sev, title, now, data) }
            s.last = now
            s.detail = detail
        }
        for (a in real) {
            if (a.title in QUIET_TITLES) continue
            when (a.level) {
                AiMonitor.Level.RED -> see("R" + a.title, Sev.CRITICAL, a.title, a.detail, false)
                AiMonitor.Level.AMBER -> see("A" + a.title, Sev.CAUTION, a.title, a.detail, false)
                AiMonitor.Level.INFO -> {}
            }
        }
        if (dataLost) see("DATA_LOST", Sev.CAUTION, "ECU DATA LOST",
            "No current samples from the ECU. Values and alerts on screen are NOT live. Check cable/adapter and connection.", true)
        if (now < testUntil) see("TEST", testSev, "TEST ALERT", "Alert test — no engine fault. Sound, voice, vibration and visuals are being checked.", false)

        val iter = states.entries.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            if (e.key !in seen && now - e.value.last > HOLD_MS) iter.remove()
        }

        val active = states.values
            .filter { now - it.first >= CONFIRM_MS }
            .sortedWith(compareBy({ -it.sev.ordinal }, { it.first }))

        // ---- audio / haptics
        val live = active.filter { !it.acked }
        val crit = live.filter { it.sev == Sev.CRITICAL }
        if (crit.isNotEmpty()) {
            val fresh = crit.filter { !it.announced }
            if (fresh.isNotEmpty()) {
                fresh.forEach { it.announced = true }
                sounds.critical(soundOn, vibrateOn)
                if (soundOn && voiceOn) sounds.speakDelayed("Warning. " + fresh[0].title, 1000L)
                nextCrit = now + (if (soundOn && voiceOn) 3800L else 1500L)
            } else if (now >= nextCrit) {
                sounds.critical(soundOn, vibrateOn)
                nextCrit = now + 1500L
            }
        } else {
            val cau = live.filter { it.sev == Sev.CAUTION }
            if (cau.isNotEmpty()) {
                val fresh = cau.filter { !it.announced }
                val play = fresh.isNotEmpty() || now >= nextCaution
                if (play) {
                    val dataKind = (if (fresh.isNotEmpty()) fresh else cau).any { it.data }
                    sounds.caution(soundOn, vibrateOn, dataKind)
                    if (fresh.any { it.data } && soundOn && voiceOn) sounds.speakDelayed("E C U data lost", 1200L)
                    fresh.forEach { it.announced = true }
                    nextCaution = now + 12000L
                }
            }
        }
        return active.map { ActiveAlert(it.key, it.sev, it.title, it.detail, it.acked) }
    }
}

/** Generates its own alarm tones (no audio assets), plus optional spoken warnings and vibration. */
class AlertSounds(private val ctx: Context) : TextToSpeech.OnInitListener {
    private val rate = 22050
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val critTrack: AudioTrack?
    private val cautionTrack: AudioTrack?
    private val dataTrack: AudioTrack?

    init {
        // Critical: urgent triple beep, 2 kHz with harmonics.
        val crit = ArrayList<Short>()
        for (k in 0 until 3) {
            crit.addAll(beep(2000.0, 110, 0.95, harsh = true).toList())
            if (k < 2) crit.addAll(silence(70).toList())
        }
        // Caution: two-tone chime with decay.
        val cau = ArrayList<Short>()
        cau.addAll(bell(880.0, 220, 0.8).toList())
        cau.addAll(silence(30).toList())
        cau.addAll(bell(1175.0, 320, 0.8).toList())
        // Data lost: three descending notes.
        val dat = ArrayList<Short>()
        dat.addAll(beep(988.0, 150, 0.85, harsh = false).toList())
        dat.addAll(silence(40).toList())
        dat.addAll(beep(784.0, 150, 0.85, harsh = false).toList())
        dat.addAll(silence(40).toList())
        dat.addAll(beep(622.0, 260, 0.85, harsh = false).toList())
        critTrack = makeTrack(crit.toShortArray())
        cautionTrack = makeTrack(cau.toShortArray())
        dataTrack = makeTrack(dat.toShortArray())
        try { tts = TextToSpeech(ctx, this) } catch (_: Exception) { tts = null }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                tts?.setAudioAttributes(attrs)
                tts?.setLanguage(Locale.getDefault())
                ttsReady = true
            } catch (_: Exception) { ttsReady = false }
        }
    }

    // ---- synthesis
    private fun silence(ms: Int) = ShortArray(rate * ms / 1000)

    private fun beep(freq: Double, ms: Int, vol: Double, harsh: Boolean): ShortArray {
        val n = rate * ms / 1000
        val out = ShortArray(n)
        val edge = rate * 8 / 1000
        for (i in 0 until n) {
            val x = 2.0 * PI * freq * i / rate
            var s = sin(x)
            if (harsh) s = (s + 0.33 * sin(3 * x) + 0.2 * sin(5 * x)) / 1.53
            val env = minOf(1.0, i.toDouble() / edge, (n - i).toDouble() / edge)
            out[i] = (s * env * vol * 32000.0).toInt().toShort()
        }
        return out
    }

    private fun bell(freq: Double, ms: Int, vol: Double): ShortArray {
        val n = rate * ms / 1000
        val out = ShortArray(n)
        val edge = rate * 4 / 1000
        for (i in 0 until n) {
            val x = 2.0 * PI * freq * i / rate
            val s = (sin(x) + 0.25 * sin(2 * x)) / 1.25
            val decay = Math.exp(-3.0 * i / n)
            val env = minOf(1.0, i.toDouble() / edge) * decay
            out[i] = (s * env * vol * 32000.0).toInt().toShort()
        }
        return out
    }

    private fun makeTrack(pcm: ShortArray): AudioTrack? = try {
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(rate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(fmt)
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, pcm.size)
        t
    } catch (_: Exception) { null }

    private fun fire(t: AudioTrack?) {
        if (t == null) return
        try {
            t.stop()
            t.reloadStaticData()
            t.play()
        } catch (_: Exception) { }
    }

    // ---- public actions
    fun critical(sound: Boolean, vibrate: Boolean) {
        if (sound) fire(critTrack)
        if (vibrate) buzz(longArrayOf(0, 120, 70, 120, 70, 120))
    }

    fun caution(sound: Boolean, vibrate: Boolean, dataLost: Boolean) {
        if (sound) fire(if (dataLost) dataTrack else cautionTrack)
        if (vibrate) buzz(longArrayOf(0, 250))
    }

    fun speakDelayed(text: String, delayMs: Long) {
        handler.postDelayed({
            if (!ttsReady) return@postDelayed
            val t = text.replace("AFR", "A F R").replace("ECU", "E C U")
            try { tts?.speak(t.lowercase(Locale.getDefault()), TextToSpeech.QUEUE_FLUSH, null, "alert") } catch (_: Exception) { }
        }, delayMs)
    }

    fun stopSpeech() {
        handler.removeCallbacksAndMessages(null)
        try { tts?.stop() } catch (_: Exception) { }
    }

    @Suppress("DEPRECATION")
    private fun buzz(pattern: LongArray) {
        try {
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            else v.vibrate(pattern, -1)
        } catch (_: Exception) { }
    }

    /** True when the alarm stream is at zero, so tones and speech would be inaudible. */
    fun alarmVolumeZero(): Boolean {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return am.getStreamVolume(AudioManager.STREAM_ALARM) == 0
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        try { critTrack?.release() } catch (_: Exception) { }
        try { cautionTrack?.release() } catch (_: Exception) { }
        try { dataTrack?.release() } catch (_: Exception) { }
        try { tts?.shutdown() } catch (_: Exception) { }
    }
}