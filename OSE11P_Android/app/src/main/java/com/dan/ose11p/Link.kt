package com.dan.ose11p

import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** Polls Mode 1 Message 0 over an FTDI USB serial cable (8192 baud 8N1) or simulates data. */
class AldlLink(
    private val usb: UsbManager?,
    private val simulate: Boolean,
    private val onSample: (Sample) -> Unit,
    private val onStatus: (String) -> Unit
) : Thread() {

    @Volatile
    var stopFlag = false

    override fun run() {
        try {
            if (simulate) runSim() else runUsb()
        } catch (e: Exception) {
            onStatus("error: " + (e.message ?: e.javaClass.simpleName))
        }
        onStatus("disconnected")
    }

    private fun runUsb() {
        val m = usb ?: throw IllegalStateException("no USB service")
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(m).firstOrNull()
            ?: throw IllegalStateException("no USB serial device found")
        val conn = m.openDevice(driver.device)
            ?: throw IllegalStateException("USB permission not granted")
        val port = driver.ports[0]
        port.open(conn)
        try {
            port.setParameters(Aldl.BAUD, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            try { port.purgeHwBuffers(true, true) } catch (ignored: Exception) {}
            port.write(Aldl.CHATTER_OFF, 200)
            sleep(150)
            onStatus("connected")
            val buf = ByteArray(512)
            val tmp = ByteArray(64)
            var misses = 0
            while (!stopFlag) {
                try { port.purgeHwBuffers(true, true) } catch (ignored: Exception) {}
                var n = 0
                port.write(Aldl.REQ_ENGINE, 200)
                val t0 = System.currentTimeMillis()
                var frame: ByteArray? = null
                while (System.currentTimeMillis() - t0 < 400 && !stopFlag) {
                    val r = port.read(tmp, 50)
                    if (r > 0 && n + r <= buf.size) {
                        System.arraycopy(tmp, 0, buf, n, r)
                        n += r
                        frame = Aldl.findFrame(buf, n)
                        if (frame != null) break
                    }
                }
                if (frame != null) {
                    misses = 0
                    onSample(Aldl.decode(frame))
                } else {
                    misses++
                    onStatus("no frame (timeout)")
                    if (misses >= 5) {                       // re-sync: flush and repeat chatter-off
                        try { port.purgeHwBuffers(true, true) } catch (ignored: Exception) {}
                        port.write(Aldl.CHATTER_OFF, 200)
                        sleep(150)
                        misses = 0
                        onStatus("re-syncing")
                    }
                }
            }
            try { port.write(Aldl.CHATTER_ON, 200) } catch (ignored: Exception) {}
        } finally {
            try { port.close() } catch (ignored: Exception) {}
        }
    }

    private fun runSim() {
        onStatus("simulating")
        var t = 0.0
        while (!stopFlag) {
            t += 0.1
            val rpm = (3000 + 2500 * abs(sin(t / 4))).toInt()
            val d = ByteArray(56)
            d[0] = (rpm shr 8).toByte(); d[1] = (rpm and 0xFF).toByte()
            d[0x05] = ((85 + 40) / 0.75).toInt().toByte()
            d[0x0A] = ((101 - 10.354) / 0.369).toInt().toByte()
            d[0x0D] = 120
            d[0x16] = 147.toByte()
            d[0x21] = 80
            d[0x22] = ((60 + 20 * abs(sin(t / 3))) * 2.56).toInt().toByte()
            d[0x23] = (Random.nextDouble(13.5, 15.5) * 10).toInt().toByte()
            d[0x35] = ((130 + 60 * abs(sin(t / 4)) - 2.2) / 1.22).toInt().toByte()
            val sp = (20 / 0.351565).toInt()
            d[0x1F] = ((sp shr 8) and 0xFF).toByte(); d[0x20] = (sp and 0xFF).toByte()
            val frame = Aldl.withCs(byteArrayOf(0xF4.toByte(), Aldl.LEN_BYTE.toByte(), 0x01) + d)
            onSample(Aldl.decode(frame))
            sleep(100)
        }
    }
}