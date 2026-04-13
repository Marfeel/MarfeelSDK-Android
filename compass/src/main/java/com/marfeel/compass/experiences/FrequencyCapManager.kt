package com.marfeel.compass.experiences

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import java.util.TimeZone

internal class FrequencyCapManager(
    private val preferences: SharedPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeZone: TimeZone = TimeZone.getDefault(),
) {
    private val gson = Gson()
    private val lock = Any()

    @Volatile
    private var lastConfig: Map<String, List<String>> = emptyMap()

    companion object {
        private const val COUNTS_KEY = "experiences_frequency_caps"
    }

    internal data class EventCounter(var impression: Long = 0L, var close: Long = 0L) {
        operator fun plusAssign(other: EventCounter) {
            impression += other.impression
            close += other.close
        }
    }

    internal data class ExperienceCounter(
        val total: EventCounter = EventCounter(),
        val last: EventCounter = EventCounter(),
        val buckets: MutableMap<Int, MutableMap<Int, MutableMap<Int, EventCounter>>> = mutableMapOf(),
    )

    fun trackImpression(experienceId: String) = bump(experienceId) { leaf, entry, now ->
        leaf.impression++
        entry.total.impression++
        entry.last.impression = now
    }

    fun trackClose(experienceId: String) = bump(experienceId) { leaf, entry, now ->
        leaf.close++
        entry.total.close++
        entry.last.close = now
    }

    fun getCounts(experienceId: String): Map<String, Long> = synchronized(lock) {
        val entry = loadAll()[experienceId] ?: ExperienceCounter()
        computeCounts(entry)
    }

    fun buildUexp(): String = synchronized(lock) {
        loadAll()
            .mapNotNull { (id, entry) -> encode(id, computeCounts(entry)) }
            .joinToString(";")
    }

    fun applyResponseConfig(config: Map<String, List<String>>) = synchronized(lock) {
        lastConfig = config
        if (config.isEmpty()) {
            preferences.edit { remove(COUNTS_KEY) }
            return@synchronized
        }
        saveAll(loadAll().filterKeys { it in config.keys })
    }

    fun getConfig(): Map<String, List<String>> = lastConfig

    fun clear() = synchronized(lock) {
        lastConfig = emptyMap()
        preferences.edit { remove(COUNTS_KEY) }
    }

    private inline fun bump(id: String, mutate: (EventCounter, ExperienceCounter, Long) -> Unit) =
        synchronized(lock) {
            if (id !in lastConfig.keys) return@synchronized
            val all = loadAll()
            val entry = all.getOrPut(id) { ExperienceCounter() }
            val now = clock()
            mutate(leafForWrite(entry, now), entry, now)
            saveAll(all)
        }

    private fun computeCounts(entry: ExperienceCounter): Map<String, Long> {
        val now = clock()
        val cal = calendarAt(now)
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)

        val today = readLeaf(entry, year, month, day) ?: EventCounter()
        val thisMonth = sumMonth(entry, year, month)
        val thisWeek = sumWeek(entry, now)

        return mapOf(
            "l" to entry.total.impression,
            "cl" to entry.total.close,
            "m" to thisMonth.impression,
            "cm" to thisMonth.close,
            "w" to thisWeek.impression,
            "cw" to thisWeek.close,
            "d" to today.impression,
            "cd" to today.close,
            "ls" to secondsSince(entry.last.impression, now),
            "cls" to secondsSince(entry.last.close, now),
        )
    }

    private fun sumMonth(entry: ExperienceCounter, year: Int, month: Int): EventCounter {
        val sum = EventCounter()
        entry.buckets[year]?.get(month)?.values?.forEach { sum += it }
        return sum
    }

    private fun sumWeek(entry: ExperienceCounter, nowMillis: Long): EventCounter {
        val cal = isoCalendarAt(nowMillis)
        cal.add(Calendar.DAY_OF_MONTH, -((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7))
        val sum = EventCounter()
        repeat(7) {
            readLeaf(entry, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
                ?.let { sum += it }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return sum
    }

    private fun leafForWrite(entry: ExperienceCounter, nowMillis: Long): EventCounter {
        val cal = calendarAt(nowMillis)
        return entry.buckets
            .getOrPut(cal.get(Calendar.YEAR)) { mutableMapOf() }
            .getOrPut(cal.get(Calendar.MONTH) + 1) { mutableMapOf() }
            .getOrPut(cal.get(Calendar.DAY_OF_MONTH)) { EventCounter() }
    }

    private fun readLeaf(entry: ExperienceCounter, year: Int, month: Int, day: Int): EventCounter? =
        entry.buckets[year]?.get(month)?.get(day)

    private fun secondsSince(stamp: Long, now: Long): Long =
        if (stamp > 0L) (now - stamp) / 1000L else 0L

    private fun encode(id: String, counts: Map<String, Long>): String? {
        val parts = counts.asSequence()
            .filter { it.value > 0L }
            .flatMap { sequenceOf(it.key, it.value.toString()) }
            .toList()
        return if (parts.isEmpty()) null else "$id,${parts.joinToString("|")}"
    }

    private fun calendarAt(millis: Long): Calendar =
        Calendar.getInstance(timeZone).apply { timeInMillis = millis }

    private fun isoCalendarAt(millis: Long): Calendar =
        Calendar.getInstance(timeZone).apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            timeInMillis = millis
        }

    private fun loadAll(): MutableMap<String, ExperienceCounter> {
        val json = preferences.getString(COUNTS_KEY, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, ExperienceCounter>>() {}.type
            gson.fromJson<MutableMap<String, ExperienceCounter>>(json, type) ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveAll(all: Map<String, ExperienceCounter>) {
        preferences.edit { putString(COUNTS_KEY, gson.toJson(all)) }
    }
}
