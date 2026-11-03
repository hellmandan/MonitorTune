package com.dan.ose11p

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** OSE $11P V104 3 bar: offsets/scaling from 'OSE_$11P V104 3Bar.adx' (Mode 1 Message 0, 8192 baud). */
object Aldl {
    const val BAUD = 8192
    const val FRAME_LEN = 60                      // F4 8E 01 + 56 data + checksum
    const val LEN_BYTE = 0x55 + (FRAME_LEN - 3)   // 0x8E

    fun withCs(b: ByteArray): ByteArray {
        var s = 0
        for (x in b) s += x.toInt() and 0xFF
        val out = b.copyOf(b.size + 1)
        out[b.size] = ((-s) and 0xFF).toByte()
        return out
    }

    val REQ_ENGINE: ByteArray = withCs(byteArrayOf(0xF4.toByte(), 0x57, 0x01, 0x00))
    val CHATTER_OFF: ByteArray = withCs(byteArrayOf(0xF4.toByte(), 0x56, 0x08))
    val CHATTER_ON: ByteArray = withCs(byteArrayOf(0xF4.toByte(), 0x56, 0x09))

    /** Find a valid engine frame in buf[0 until n]; skips the echo of our own request. */
    fun findFrame(buf: ByteArray, n: Int): ByteArray? {
        var i = 0
        while (i + FRAME_LEN <= n) {
            if ((buf[i].toInt() and 0xFF) == 0xF4 &&
                (buf[i + 1].toInt() and 0xFF) == LEN_BYTE &&
                buf[i + 2].toInt() == 1
            ) {
                var s = 0
                for (k in 0 until FRAME_LEN) s += buf[i + k].toInt() and 0xFF
                if ((s and 0xFF) == 0) return buf.copyOfRange(i, i + FRAME_LEN)
            }
            i++
        }
        return null
    }

    private fun u8(f: ByteArray, o: Int): Int = f[3 + o].toInt() and 0xFF
    private fun u16(f: ByteArray, o: Int): Int = (u8(f, o) shl 8) or u8(f, o + 1)
    private fun s16(f: ByteArray, o: Int): Int {
        val v = u16(f, o)
        return if ((v and 0x8000) != 0) v - 65536 else v
    }

    fun decode(f: ByteArray): Sample {
        val baro = u8(f, 0x0A) * 0.369 + 10.354
        val map = u8(f, 0x35) * 1.22 + 2.2                 // 3 bar MAP
        val cmd = u8(f, 0x16) * 0.1                        // ADX "Target AFR" = commanded AFR
        val wb = u8(f, 0x23) / 10.0
        val cur = u8(f, 0x22) / 2.56
        val boostKpa = map - baro
        return Sample(
            rpm = u16(f, 0x00).toDouble(),
            speed = u8(f, 0x21).toDouble(),
            coolant = u8(f, 0x05) * 0.75 - 40,
            baro = baro,
            map = map,
            baroCompMap = u8(f, 0x09) / 3.2 + 20,
            boostKpa = boostKpa,
            boostPsi = boostKpa * 0.145038,
            tps = u8(f, 0x0D) * 0.390625,
            spark = s16(f, 0x1F) * 0.351565,               // signed 16-bit (inferred from ADX range)
            currentVe = cur,
            calcVe = if (cmd > 0) wb / cmd * cur else 0.0, // ADX: Y / X * Z
            wbAfr = wb,
            cmdAfr = cmd,
            knockRetard = u8(f, 0x26) * 0.351565,
            knockCounts = u8(f, 0x19),
            wbRaw = u8(f, 0x23)
        )
    }
}

class Sample(
    val rpm: Double, val speed: Double, val coolant: Double, val baro: Double, val map: Double,
    val baroCompMap: Double, val boostKpa: Double, val boostPsi: Double, val tps: Double,
    val spark: Double, val currentVe: Double, val calcVe: Double, val wbAfr: Double,
    val cmdAfr: Double, val knockRetard: Double, val knockCounts: Int, val wbRaw: Int
) {
    var knockDelta: Int = 0

    fun row(): DoubleArray = doubleArrayOf(
        rpm, speed, coolant, baro, map, baroCompMap, boostKpa, boostPsi, tps, spark,
        currentVe, calcVe, wbAfr, cmdAfr, knockRetard, knockCounts.toDouble(), knockDelta.toDouble(), wbRaw.toDouble()
    )

    companion object {
        val COLUMNS = listOf(
            "RPM", "Speed_kph", "Coolant_C", "Baro_kPa", "MAP_kPa", "BaroCompMAP_kPa", "BoostKPa", "BoostPSI",
            "TPS_pct", "Spark_deg", "CurrentVE", "CalculatedVE", "WidebandAFR", "CommandedAFR",
            "KnockRetard_deg", "KnockCounts", "KnockDelta", "WBRaw"
        )
    }
}

class TableDef(val name: String, val address: Int, val mapAxis: DoubleArray, val mapMax: Double, val address2: Int = -1)

object Cfg {
    const val BIN_SIZE = 65536
    const val VE_SCALE = 0.390625
    const val SPARK_SCALE = 0.351565
    const val WB_MIN = 11.0                     // capture (log + VE sampling) only while wideband AFR is inside
    const val WB_MAX = 18.0                     // this window
    const val MIN_COOLANT = 70.0
    const val MIN_SAMPLES = 5
    const val MIN_KNOCK_FRAMES = 2
    const val MAX_REDUCE_DEG = 5.0

    // VE rows: 400-2200 step 200, then 2400-8000 step 400 (25 rows)
    val RPM_VE: DoubleArray =
        ((400..2200 step 200) + (2400..8000 step 400)).map { it.toDouble() }.toDoubleArray()
    // Spark rows: 400-8000 step 400 (20 rows)
    val RPM_SPARK: DoubleArray = (400..8000 step 400).map { it.toDouble() }.toDoubleArray()

    private val BOOST_AXIS: DoubleArray = (100..300 step 20).map { it.toDouble() }.toDoubleArray()

    val VE_TABLES = listOf(
        TableDef("20-100kPa VE", 0x6E06,
            doubleArrayOf(20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 75.0, 80.0, 85.0, 90.0, 95.0, 100.0), 100.0),
        TableDef("Boost VE", 0x6F35, BOOST_AXIS, 9999.0)
    )
    val SPARK_TABLES = listOf(
        TableDef("20-100kPa Spark", 0x7534,
            doubleArrayOf(20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 85.0, 90.0, 95.0, 100.0), 100.0, 0x7613),
        TableDef("Boost Spark", 0x76F2, BOOST_AXIS, 9999.0, 0x77D1)
    )
}

fun nearest(ax: DoubleArray, v: Double): Int {
    if (v < ax[0] - (ax[1] - ax[0]) / 2 || v > ax[ax.size - 1] + (ax[ax.size - 1] - ax[ax.size - 2]) / 2) return -1
    var best = 0
    for (i in ax.indices) if (abs(ax[i] - v) < abs(ax[best] - v)) best = i
    return best
}

private fun pickTable(tables: List<TableDef>, m: Double): Int {
    for (k in tables.indices) if (m <= tables[k].mapMax) return k
    return -1
}

/** Averages Calculated VE per cell; writes exact values (VE% = raw x 0.390625) into the BIN. */
class VeFill(private val bin: ByteArray) {
    private val acc = HashMap<Int, DoubleArray>()      // key = ti*100000 + i*100 + j -> [sum, n]
    var minHits = 3                                    // hits needed before a cell itself is rewritten

    init {
        require(bin.size == Cfg.BIN_SIZE) { "BIN size ${bin.size} != ${Cfg.BIN_SIZE} (use the 64 KB BIN, not stacked)" }
        for (t in Cfg.VE_TABLES) {
            for (i in Cfg.RPM_VE.indices) for (j in t.mapAxis.indices) {
                val v = (bin[t.address + i * t.mapAxis.size + j].toInt() and 0xFF) * Cfg.VE_SCALE
                require(v in 20.0..160.0) { "'${t.name}' values look wrong at this address - wrong BIN?" }
            }
        }
    }

    private fun key(ti: Int, i: Int, j: Int) = ti * 100000 + i * 100 + j

    fun add(s: Sample) {
        if (s.rpm <= 0 || s.wbAfr < Cfg.WB_MIN || s.wbAfr > Cfg.WB_MAX || s.currentVe <= 0 || s.coolant < Cfg.MIN_COOLANT) return
        val ti = pickTable(Cfg.VE_TABLES, s.map)
        if (ti < 0) return
        val i = nearest(Cfg.RPM_VE, s.rpm)
        val j = nearest(Cfg.VE_TABLES[ti].mapAxis, s.map)
        if (i < 0 || j < 0) return
        val a = acc.getOrPut(key(ti, i, j)) { DoubleArray(2) }
        a[0] += s.calcVe
        a[1] += 1.0
    }

    fun current(ti: Int, i: Int, j: Int): Double {
        val t = Cfg.VE_TABLES[ti]
        return (bin[t.address + i * t.mapAxis.size + j].toInt() and 0xFF) * Cfg.VE_SCALE
    }

    /** Returns (avg, n) or null. */
    fun cell(ti: Int, i: Int, j: Int): Pair<Double, Int>? {
        val a = acc[key(ti, i, j)] ?: return null
        return Pair(a[0] / a[1], a[1].toInt())
    }

    fun clear() = acc.clear()

    /** Proposed values per table [ti][rpmRow][mapCol]; measured = enough samples; touched = will be written. */
    class Result(val values: Array<Array<DoubleArray>>, val measured: Array<Array<BooleanArray>>,
                 val touched: Array<Array<BooleanArray>>)

    fun compute(interp: Boolean, smooth: Double, passes: Int = 2): Result {
        val nr = Cfg.RPM_VE.size
        val tabs = Cfg.VE_TABLES
        val cur = Array(tabs.size) { ti -> Array(nr) { i -> DoubleArray(tabs[ti].mapAxis.size) { j -> current(ti, i, j) } } }
        val nw = Array(tabs.size) { ti -> Array(nr) { i -> cur[ti][i].copyOf() } }
        val meas = Array(tabs.size) { ti -> Array(nr) { BooleanArray(tabs[ti].mapAxis.size) } }
        val touched = Array(tabs.size) { ti -> Array(nr) { BooleanArray(tabs[ti].mapAxis.size) } }
        val ratios = ArrayList<DoubleArray>()          // [ti, i, j, ratio, weight multiplier]
        for ((k, a) in acc) {
            if (a[1] < 1.0) continue
            val ti = k / 100000
            val i = (k % 100000) / 100
            val j = k % 100
            val avg = a[0] / a[1]
            if (a[1] >= minHits) {
                nw[ti][i][j] = avg
                meas[ti][i][j] = true
                touched[ti][i][j] = true
                ratios.add(doubleArrayOf(ti.toDouble(), i.toDouble(), j.toDouble(), avg / cur[ti][i][j], 1.0))
            } else {                                    // too few hits to rewrite the cell, but it still informs neighbours
                ratios.add(doubleArrayOf(ti.toDouble(), i.toDouble(), j.toDouble(), avg / cur[ti][i][j], a[1] / minHits))
            }
        }
        if (ratios.isEmpty()) return Result(nw, meas, touched)
        if (interp) {                                   // inverse-distance-weighted correction ratio, radius 3 cells
            for (ti in tabs.indices) for (i in 0 until nr) for (j in tabs[ti].mapAxis.indices) {
                if (meas[ti][i][j]) continue
                var num = 0.0
                var den = 0.0
                for (r in ratios) {
                    if (r[0].toInt() != ti) continue
                    val di = i - r[1].toInt()
                    val dj = j - r[2].toInt()
                    if (maxOf(abs(di), abs(dj)) <= 3) {
                        val w = r[4] / maxOf((di * di + dj * dj).toDouble(), 0.25)
                        num += w * r[3]
                        den += w
                    }
                }
                if (den > 0) {
                    val f = den / (den + 0.25)
                    nw[ti][i][j] = cur[ti][i][j] * (1 + (num / den - 1) * f)
                    touched[ti][i][j] = true
                }
            }
        }
        if (smooth > 0 && tabs.size == 2) {             // smooth both tables as one grid so the 100 kPa seam has no step
            val n0 = tabs[0].mapAxis.size
            val n1 = tabs[1].mapAxis.size
            val w = n0 + n1 - 1
            val u = Array(nr) { DoubleArray(w) }
            val tu = Array(nr) { BooleanArray(w) }
            val mu = Array(nr) { BooleanArray(w) }
            for (i in 0 until nr) {
                for (j in 0 until n0 - 1) {
                    u[i][j] = nw[0][i][j]; tu[i][j] = touched[0][i][j]; mu[i][j] = meas[0][i][j]
                }
                u[i][n0 - 1] = (nw[0][i][n0 - 1] + nw[1][i][0]) / 2.0
                tu[i][n0 - 1] = touched[0][i][n0 - 1] || touched[1][i][0]
                mu[i][n0 - 1] = meas[0][i][n0 - 1] || meas[1][i][0]
                for (j in 1 until n1) {
                    u[i][n0 - 1 + j] = nw[1][i][j]; tu[i][n0 - 1 + j] = touched[1][i][j]; mu[i][n0 - 1 + j] = meas[1][i][j]
                }
            }
            val k1 = intArrayOf(1, 2, 1)
            repeat(passes) {
                val b = Array(nr) { DoubleArray(w) }
                for (i in 0 until nr) for (j in 0 until w) {
                    var tot = 0.0
                    for (di in -1..1) {
                        val ii = min(nr - 1, max(0, i + di))
                        for (dj in -1..1) {
                            val jj = min(w - 1, max(0, j + dj))
                            tot += u[ii][jj] * k1[di + 1] * k1[dj + 1]
                        }
                    }
                    b[i][j] = tot / 16.0
                }
                for (i in 0 until nr) for (j in 0 until w) {
                    if (tu[i][j]) {
                        val sw = smooth * (if (mu[i][j]) 0.5 else 1.0)
                        u[i][j] = (1 - sw) * u[i][j] + sw * b[i][j]
                    }
                }
            }
            for (i in 0 until nr) {
                for (j in 0 until n0 - 1) nw[0][i][j] = u[i][j]
                nw[0][i][n0 - 1] = u[i][n0 - 1]
                nw[1][i][0] = u[i][n0 - 1]
                touched[0][i][n0 - 1] = tu[i][n0 - 1]
                touched[1][i][0] = tu[i][n0 - 1]
                for (j in 1 until n1) nw[1][i][j] = u[i][n0 - 1 + j]
            }
        }
        return Result(nw, meas, touched)
    }

    /** Returns tuned BIN copy and number of bytes changed. */
    fun write(interp: Boolean, smooth: Double): Pair<ByteArray, Int> {
        val out = bin.copyOf()
        var n = 0
        val r = compute(interp, smooth)
        for (ti in Cfg.VE_TABLES.indices) {
            val t = Cfg.VE_TABLES[ti]
            for (i in Cfg.RPM_VE.indices) for (j in t.mapAxis.indices) {
                if (!r.touched[ti][i][j]) continue
                val raw = max(0, min(255, (r.values[ti][i][j] / Cfg.VE_SCALE).roundToInt()))
                val p = t.address + i * t.mapAxis.size + j
                if ((out[p].toInt() and 0xFF) != raw) {
                    out[p] = raw.toByte()
                    n++
                }
            }
        }
        return Pair(out, n)
    }
}

/** Retard-only spark correction from logged knock retard, applied to both Econ and Power copies. Never adds advance. */
class SparkKnock(private val bin: ByteArray) {
    private val acc = HashMap<Int, DoubleArray>()      // [samples, knockFrames, maxRetard]

    init {
        require(bin.size == Cfg.BIN_SIZE) { "BIN size ${bin.size} != ${Cfg.BIN_SIZE} (use the 64 KB BIN)" }
        for (t in Cfg.SPARK_TABLES) {
            for (addr in intArrayOf(t.address, t.address2))
                for (i in Cfg.RPM_SPARK.indices) for (j in t.mapAxis.indices) {
                    val v = (bin[addr + i * t.mapAxis.size + j].toInt() and 0xFF) * Cfg.SPARK_SCALE
                    require(v in 0.0..60.0) { "'${t.name}' values look wrong at 0x${addr.toString(16)} - wrong BIN?" }
                }
        }
    }

    private fun key(ti: Int, i: Int, j: Int) = ti * 100000 + i * 100 + j

    fun add(s: Sample) {
        if (s.rpm <= 0 || s.coolant < Cfg.MIN_COOLANT) return
        val ti = pickTable(Cfg.SPARK_TABLES, s.map)
        if (ti < 0) return
        val i = nearest(Cfg.RPM_SPARK, s.rpm)
        val j = nearest(Cfg.SPARK_TABLES[ti].mapAxis, s.map)
        if (i < 0 || j < 0) return
        val a = acc.getOrPut(key(ti, i, j)) { DoubleArray(3) }
        a[0] += 1.0
        if (s.knockRetard > 0 || s.knockDelta > 0) a[1] += 1.0
        a[2] = max(a[2], s.knockRetard)
    }

    fun current(ti: Int, i: Int, j: Int): Double {
        val t = Cfg.SPARK_TABLES[ti]
        return (bin[t.address + i * t.mapAxis.size + j].toInt() and 0xFF) * Cfg.SPARK_SCALE
    }

    fun reduction(ti: Int, i: Int, j: Int): Double {
        val a = acc[key(ti, i, j)] ?: return 0.0
        if (a[0] < Cfg.MIN_SAMPLES || a[1] < Cfg.MIN_KNOCK_FRAMES) return 0.0
        return min(a[2], Cfg.MAX_REDUCE_DEG)
    }

    fun clear() = acc.clear()

    fun write(): Pair<ByteArray, Int> {
        val out = bin.copyOf()
        var n = 0
        for (k in acc.keys) {
            val ti = k / 100000
            val i = (k % 100000) / 100
            val j = k % 100
            val red = reduction(ti, i, j)
            if (red <= 0) continue
            val t = Cfg.SPARK_TABLES[ti]
            for (addr in intArrayOf(t.address, t.address2)) {          // [Econ] and [Power] copies
                val p = addr + i * t.mapAxis.size + j
                val cur = bin[p].toInt() and 0xFF
                val raw = max(0, ((cur * Cfg.SPARK_SCALE - red) / Cfg.SPARK_SCALE).roundToInt())
                out[p] = raw.toByte()
            }
            n++
        }
        return Pair(out, n)
    }
}