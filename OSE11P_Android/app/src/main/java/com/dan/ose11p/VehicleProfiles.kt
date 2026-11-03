package com.dan.ose11p

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class VehicleProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val make: String = "",
    val model: String = "",
    val year: Int? = null,
    val engine: String = "",
    val ecu: String = "",
    val protocol: String = "AUTO",
    val fuel: String = "",
    val massKg: Double? = null,
    val notes: String = "",
    val modifications: List<String> = emptyList()
)

object VehicleProfileStore {
    private const val FILE = "vehicle_profiles.json"
    fun load(context: Context): MutableList<VehicleProfile> {
        val out = mutableListOf<VehicleProfile>()
        return try {
            val a = JSONArray(File(context.filesDir, FILE).readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val mods = o.optJSONArray("modifications") ?: JSONArray()
                out += VehicleProfile(
                    id = o.optString("id", UUID.randomUUID().toString()), name = o.optString("name", "Vehicle"),
                    make = o.optString("make"), model = o.optString("model"), year = if (o.isNull("year")) null else o.optInt("year"),
                    engine = o.optString("engine"), ecu = o.optString("ecu"), protocol = o.optString("protocol", "AUTO"),
                    fuel = o.optString("fuel"), massKg = if (o.isNull("massKg")) null else o.optDouble("massKg"),
                    notes = o.optString("notes"), modifications = (0 until mods.length()).map { mods.optString(it) }
                )
            }
            out
        } catch (_: Exception) { out }
    }
    fun save(context: Context, profiles: List<VehicleProfile>) {
        val a = JSONArray()
        profiles.forEach { p ->
            val mods = JSONArray(); p.modifications.forEach { mods.put(it) }
            a.put(JSONObject().put("id", p.id).put("name", p.name).put("make", p.make).put("model", p.model)
                .put("year", p.year ?: JSONObject.NULL).put("engine", p.engine).put("ecu", p.ecu)
                .put("protocol", p.protocol).put("fuel", p.fuel).put("massKg", p.massKg ?: JSONObject.NULL)
                .put("notes", p.notes).put("modifications", mods))
        }
        File(context.filesDir, FILE).writeText(a.toString(2))
    }
}