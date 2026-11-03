package com.dan.ose11p

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max

/** Road-dyno estimate: longitudinal kinetic power from vehicle-speed change. */
data class DynoPoint(val rpm: Double, val hp: Double, val torque: Double, val speed: Double)
data class DynoRun(val name: String, val mode: String, val points: List<DynoPoint>) {
    val peakHp get() = points.maxOfOrNull { it.hp } ?: 0.0
    val peakTorque get() = points.maxOfOrNull { it.torque } ?: 0.0
}

class RoadDyno(private val massKg: Double) {
    private val samples = ArrayDeque<Pair<Long, Double>>()
    private val points = ArrayList<DynoPoint>()
    fun add(s: Sample) {
        val now = System.currentTimeMillis()
        if (s.rpm < 1200 || s.speed < 10) return
        samples.addLast(Pair(now, s.speed / 3.6))
        while (samples.isNotEmpty() && now - samples.first().first > 2500L) samples.removeFirst()
        if (samples.size < 4) return
        // Linear-regression slope over a rolling window reduces quantisation noise in ALDL km/h.
        val t0 = samples.first().first.toDouble()
        val xs = samples.map { (it.first - t0) / 1000.0 }
        val ys = samples.map { it.second }
        val xm = xs.average(); val ym = ys.average()
        var numerator = 0.0; var denominator = 0.0
        for (i in xs.indices) { numerator += (xs[i]-xm)*(ys[i]-ym); denominator += (xs[i]-xm)*(xs[i]-xm) }
        if (denominator <= 0.0) return
        val accel = numerator / denominator
        if (accel <= 0.05 || accel > 8.0) return
        val watts = massKg * accel * ym
        val hp = watts / 745.7
        if (hp in 0.0..2000.0) points.add(DynoPoint(s.rpm, hp, hp * 7121.0 / s.rpm, s.speed))
    }
    fun finish(name: String) = DynoRun(name, "ROAD ESTIMATE", points.toList())
}

object DynoStore {
    private const val FILE = "dyno_runs.json"
    fun load(c: Context): MutableList<DynoRun> {
        val out = mutableListOf<DynoRun>()
        try {
            val a = JSONArray(File(c.filesDir, FILE).readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i); val p = o.getJSONArray("points"); val rows = mutableListOf<DynoPoint>()
                for (j in 0 until p.length()) { val q=p.getJSONObject(j); rows.add(DynoPoint(q.getDouble("rpm"),q.getDouble("hp"),q.getDouble("torque"),q.getDouble("speed"))) }
                out.add(DynoRun(o.getString("name"),o.getString("mode"),rows))
            }
        } catch (_: Exception) { }
        return out
    }
    fun save(c: Context, runs: List<DynoRun>) {
        val a=JSONArray(); for (r in runs) { val o=JSONObject().put("name",r.name).put("mode",r.mode); val p=JSONArray(); for (x in r.points) p.put(JSONObject().put("rpm",x.rpm).put("hp",x.hp).put("torque",x.torque).put("speed",x.speed)); o.put("points",p); a.put(o) }
        File(c.filesDir, FILE).writeText(a.toString())
    }
}

class DynoGraph(context: Context) : View(context) {
    var runs: List<DynoRun> = emptyList(); set(v) { field=v; invalidate() }
    private val paint=Paint(3)
    override fun onDraw(c: Canvas) {
        super.onDraw(c); val l=paddingLeft+48f; val r=width-paddingRight-12f; val t=paddingTop+18f; val b=height-paddingBottom-38f
        paint.color=0xff82909f.toInt(); paint.strokeWidth=1f; paint.style=Paint.Style.STROKE
        for(i in 0..4){val y=t+(b-t)*i/4f;c.drawLine(l,y,r,y,paint)}
        paint.style=Paint.Style.FILL; paint.textSize=26f; c.drawText("POWER / TORQUE vs RPM",l,t-2,paint); paint.textSize=20f
        c.drawText("RPM",(l+r)/2,b+30,paint); c.save(); c.rotate(-90f,16f,(t+b)/2); c.drawText("HP / Nm (separate scaled curves)",16f,(t+b)/2,paint); c.restore()
        val maxR=max(6500.0,runs.flatMap{it.points}.maxOfOrNull{it.rpm}?:6500.0); val maxHp=max(100.0,runs.flatMap{it.points}.maxOfOrNull{it.hp}?:100.0); val maxTq=max(100.0,runs.flatMap{it.points}.maxOfOrNull{it.torque}?:100.0)
        val palette=intArrayOf(0xff22d3ee.toInt(),0xffffb020.toInt(),0xffa78bfa.toInt(),0xff4ade80.toInt())
        runs.forEachIndexed { index,run ->
            for (series in 0..1) { val path=Path(); var started=false; val colour=palette[index%palette.size]; paint.color=if(series==0) colour else colour xor 0x00404040; paint.strokeWidth=if(series==0) 4f else 2.5f; paint.style=Paint.Style.STROKE
                run.points.forEach { p -> val value=if(series==0)p.hp else p.torque; val x=l+(r-l)*(p.rpm/maxR).toFloat(); val y=b-(b-t)*(value/(if(series==0) maxHp else maxTq)).toFloat(); if(!started){path.moveTo(x,y);started=true}else path.lineTo(x,y); if(run.points.size == 1) c.drawCircle(x,y,5f,paint) }; c.drawPath(path,paint)
            }
        }
        paint.style=Paint.Style.FILL; paint.textSize=18f; runs.take(4).forEachIndexed{i,run->paint.color=palette[i];c.drawText("${run.name}  ${"%.0f".format(run.peakHp)} HP / ${"%.0f".format(run.peakTorque)} Nm",l,t+25f+i*22f,paint)}
    }
}