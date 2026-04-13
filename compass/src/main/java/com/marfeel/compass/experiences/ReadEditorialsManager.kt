package com.marfeel.compass.experiences

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds

internal class ReadEditorialsManager(private val preferences: SharedPreferences) {
    private val gson = Gson()
    private val lock = Any()

    companion object {
        private const val STORAGE_KEY = "experiences_read_editorials"
        private const val MAX_ENTRIES = 100
        private const val TTL_SECONDS = 30L * 24L * 60L * 60L
    }

    internal data class Entry(val id: String, val ts: Long)

    fun add(editorialId: String) = synchronized(lock) {
        if (editorialId.isBlank()) return@synchronized
        val now = currentTimeStampInSeconds()
        val entries = readEntries()
            .filter { it.id != editorialId }
            .toMutableList()
        entries.add(Entry(editorialId, now))
        val pruned = prune(entries, now)
        writeEntries(pruned)
    }

    fun getIds(): List<String> = synchronized(lock) {
        val now = currentTimeStampInSeconds()
        prune(readEntries(), now).map { it.id }
    }

    fun buildRedParam(): String = synchronized(lock) {
        val ids = getIds().mapNotNull { it.toLongOrNull() }.sorted()
        if (ids.isEmpty()) return ""
        val parts = mutableListOf<String>()
        var previous = 0L
        for (id in ids) {
            parts.add((id - previous).toString())
            previous = id
        }
        parts.joinToString(",")
    }

    fun clear() = synchronized(lock) {
        preferences.edit { remove(STORAGE_KEY) }
    }

    private fun prune(entries: List<Entry>, now: Long): List<Entry> {
        val fresh = entries.filter { now - it.ts < TTL_SECONDS }
        return if (fresh.size <= MAX_ENTRIES) fresh
        else fresh.takeLast(MAX_ENTRIES)
    }

    private fun readEntries(): List<Entry> {
        val json = preferences.getString(STORAGE_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<Entry>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun writeEntries(entries: List<Entry>) {
        preferences.edit { putString(STORAGE_KEY, gson.toJson(entries)) }
    }
}
