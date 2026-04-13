package com.marfeel.compass.experiences

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds

internal class FrequencyCapManager(private val preferences: SharedPreferences) {
    private val gson = Gson()
    private val lock = Any()

    companion object {
        private const val COUNTS_KEY = "experiences_frequency_caps"
        private const val CONFIG_KEY = "experiences_frequency_cap_config"
        private const val ONE_HOUR_SECONDS = 3600L
        private val DEFAULT_FREQS = listOf("d", "cd", "w", "cw", "m", "cm", "l", "cl", "ls", "cls")
    }

    fun trackImpression(experienceId: String) = synchronized(lock) {
        val counts = getAllCounts().toMutableMap()
        val entry = counts.getOrDefault(experienceId, defaultCounts()).toMutableMap()
        entry["l"] = (entry["l"] ?: 0) + 1
        entry["m"] = (entry["m"] ?: 0) + 1
        entry["w"] = (entry["w"] ?: 0) + 1
        entry["d"] = (entry["d"] ?: 0) + 1
        entry["ls"] = currentTimeStampInSeconds()
        counts[experienceId] = entry
        saveCounts(counts)
    }

    fun trackClose(experienceId: String) = synchronized(lock) {
        val counts = getAllCounts().toMutableMap()
        val entry = counts.getOrDefault(experienceId, defaultCounts()).toMutableMap()
        entry["cl"] = (entry["cl"] ?: 0) + 1
        entry["cm"] = (entry["cm"] ?: 0) + 1
        entry["cw"] = (entry["cw"] ?: 0) + 1
        entry["cd"] = (entry["cd"] ?: 0) + 1
        entry["cls"] = currentTimeStampInSeconds()
        counts[experienceId] = entry
        saveCounts(counts)
    }

    fun getCounts(experienceId: String): Map<String, Long> = synchronized(lock) {
        val all = getAllCounts()
        all[experienceId] ?: defaultCounts()
    }

    fun buildUexp(): String = synchronized(lock) {
        val allCounts = getAllCounts()
        val config = getFrequencyCapConfig()
        val now = currentTimeStampInSeconds()

        val experienceIds = mutableSetOf<String>()
        experienceIds.addAll(config.keys)
        for ((id, counts) in allCounts) {
            val ls = counts["ls"] ?: 0L
            if (ls > 0L && now - ls < ONE_HOUR_SECONDS) {
                experienceIds.add(id)
            }
        }

        if (experienceIds.isEmpty()) return ""

        val entries = experienceIds.mapNotNull { id ->
            val counts = allCounts[id] ?: defaultCounts()
            val keys = config[id] ?: DEFAULT_FREQS
            val parts = mutableListOf<String>()
            for (key in keys) {
                val v = counts[key] ?: 0L
                if (v > 0L) {
                    parts.add(key)
                    parts.add(v.toString())
                }
            }
            if (parts.isEmpty()) null else "$id,${parts.joinToString("|")}"
        }

        entries.joinToString(";")
    }

    fun clear() = synchronized(lock) {
        preferences.edit {
            remove(COUNTS_KEY)
            remove(CONFIG_KEY)
        }
    }

    fun updateFrequencyCapConfig(config: Map<String, List<String>>) {
        preferences.edit {
            putString(CONFIG_KEY, gson.toJson(config))
        }
    }

    private fun getFrequencyCapConfig(): Map<String, List<String>> {
        val json = preferences.getString(CONFIG_KEY, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, List<String>>>() {}.type
        return gson.fromJson(json, type)
    }

    private fun getAllCounts(): Map<String, Map<String, Long>> {
        val json = preferences.getString(COUNTS_KEY, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, Map<String, Double>>>() {}.type
        val raw: Map<String, Map<String, Double>> = gson.fromJson(json, type)
        return raw.mapValues { (_, inner) -> inner.mapValues { (_, v) -> v.toLong() } }
    }

    private fun saveCounts(counts: Map<String, Map<String, Long>>) {
        preferences.edit {
            putString(COUNTS_KEY, gson.toJson(counts))
        }
    }

    private fun defaultCounts(): Map<String, Long> = mapOf(
        "l" to 0L, "cl" to 0L,
        "m" to 0L, "cm" to 0L,
        "w" to 0L, "cw" to 0L,
        "d" to 0L, "cd" to 0L,
        "ls" to 0L, "cls" to 0L
    )
}
