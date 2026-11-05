package com.dan.ose11p

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stores user-authored V4-style macros separately from the imported vehicle definition. */
object V4MacroStore {
    private fun key(definition: V4VehicleDefinition) = "macros_${definition.id}"

    fun merge(context: Context, definition: V4VehicleDefinition): V4VehicleDefinition {
        val requestIds = definition.requests.map { it.id }.toSet()
        val stored = load(context, definition).filter { macro -> macro.steps.isNotEmpty() && macro.steps.all { it.requestId in requestIds } }
        val imported = definition.macros.filterNot { it.id.startsWith("user_") }
        return definition.copy(macros = imported + stored)
    }

    fun save(context: Context, definition: V4VehicleDefinition) {
        val arr = JSONArray()
        definition.macros.filter { it.id.startsWith("user_") }.forEach { macro ->
            val steps = JSONArray()
            macro.steps.forEach { step ->
                steps.put(JSONObject().put("requestId", step.requestId).put("delayMs", step.delayMs))
            }
            arr.put(JSONObject().put("id", macro.id).put("name", macro.name)
                .put("requiresConfirmation", macro.requiresConfirmation).put("steps", steps))
        }
        context.getSharedPreferences("v4_macros", Context.MODE_PRIVATE).edit()
            .putString(key(definition), arr.toString()).apply()
    }

    private fun load(context: Context, definition: V4VehicleDefinition): List<V4Macro> {
        return try {
            val raw = context.getSharedPreferences("v4_macros", Context.MODE_PRIVATE).getString(key(definition), "[]") ?: "[]"
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val item = arr.optJSONObject(i) ?: return@mapNotNull null
                val stepsJson = item.optJSONArray("steps") ?: JSONArray()
                val steps = (0 until stepsJson.length()).mapNotNull { j ->
                    val step = stepsJson.optJSONObject(j) ?: return@mapNotNull null
                    val id = step.optString("requestId")
                    val delay = step.optLong("delayMs", 100)
                    if (id.isBlank() || delay !in 0..60000) null else V4MacroStep(id, delay)
                }
                val id = item.optString("id")
                val name = item.optString("name")
                if (!id.startsWith("user_") || name.isBlank() || steps.isEmpty()) null
                else V4Macro(id, name, steps, item.optBoolean("requiresConfirmation", false))
            }
        } catch (_: Exception) { emptyList() }
    }
}
