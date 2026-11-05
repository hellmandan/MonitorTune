package com.dan.ose11p

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException
import java.util.UUID

/** Raw serial transports for a compatible ALDL interface. This is not an ELM327 AT-command transport. */
class V4UsbTransport(
    context: Context,
    private val baud: Int,
    private val dataBits: Int,
    private val stopBits: Int,
    private val parity: String
) : V4RawTransport {
    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val driver = UsbSerialProber.getDefaultProber().findAllDrivers(manager).firstOrNull()
        ?: throw IOException("No USB serial device found. Check OTG and the ALDL interface.")
    private val connection = manager.openDevice(driver.device)
        ?: throw SecurityException("USB permission is not granted for this serial device.")
    private val port: UsbSerialPort = driver.ports.firstOrNull()
        ?: throw IOException("USB serial driver exposes no ports.")

    init {
        try {
            port.open(connection)
            val p = when (parity.uppercase()) {
                "ODD" -> UsbSerialPort.PARITY_ODD
                "EVEN" -> UsbSerialPort.PARITY_EVEN
                "MARK" -> UsbSerialPort.PARITY_MARK
                "SPACE" -> UsbSerialPort.PARITY_SPACE
                else -> UsbSerialPort.PARITY_NONE
            }
            val stop = if (stopBits == 2) UsbSerialPort.STOPBITS_2 else UsbSerialPort.STOPBITS_1
            port.setParameters(baud, dataBits, stop, p)
            try { port.purgeHwBuffers(true, true) } catch (_: Exception) {}
        } catch (e: Exception) {
            try { port.close() } catch (_: Exception) {}
            try { connection.close() } catch (_: Exception) {}
            throw e
        }
    }

    @Synchronized override fun write(bytes: ByteArray) {
        check(bytes.isNotEmpty()) { "Cannot send an empty frame." }
        port.write(bytes, 1000)
    }

    @Synchronized override fun read(timeoutMs: Int): ByteArray {
        require(timeoutMs in 1..10000)
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(256)
        val started = System.currentTimeMillis()
        var lastByte = 0L
        while (System.currentTimeMillis() - started < timeoutMs) {
            val n = port.read(buffer, 35)
            if (n > 0) {
                out.write(buffer, 0, n)
                lastByte = System.currentTimeMillis()
            } else if (out.size() > 0 && System.currentTimeMillis() - lastByte >= 55) {
                break
            }
        }
        return out.toByteArray()
    }

    @Synchronized override fun close() {
        try { port.close() } finally { try { connection.close() } catch (_: Exception) {} }
    }
}

/** Bluetooth Classic SPP bridge for raw serial ALDL hardware only; not a generic ELM327. */
class V4BluetoothTransport(
    adapter: BluetoothAdapter,
    device: BluetoothDevice
) : V4RawTransport {
    private val socket: BluetoothSocket
    private val input: java.io.InputStream
    private val output: java.io.OutputStream
    private val lock = Any()
    private val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    init {
        adapter.cancelDiscovery()
        socket = device.createRfcommSocketToServiceRecord(spp)
        try {
            socket.connect()
            input = socket.inputStream
            output = socket.outputStream
        } catch (e: Exception) {
            try { socket.close() } catch (_: Exception) {}
            throw e
        }
    }

    override fun write(bytes: ByteArray) = synchronized(lock) {
        check(bytes.isNotEmpty()) { "Cannot send an empty frame." }
        output.write(bytes); output.flush()
    }

    override fun read(timeoutMs: Int): ByteArray = synchronized(lock) {
        require(timeoutMs in 1..10000)
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(256)
        val started = System.currentTimeMillis()
        var lastByte = 0L
        while (System.currentTimeMillis() - started < timeoutMs) {
            val available = input.available()
            if (available > 0) {
                val n = input.read(buffer, 0, minOf(buffer.size, available))
                if (n < 0) break
                if (n > 0) { out.write(buffer, 0, n); lastByte = System.currentTimeMillis() }
            } else {
                if (out.size() > 0 && System.currentTimeMillis() - lastByte >= 60) break
                Thread.sleep(5)
            }
        }
        out.toByteArray()
    }

    override fun close() { try { socket.close() } catch (_: Exception) {} }
}
