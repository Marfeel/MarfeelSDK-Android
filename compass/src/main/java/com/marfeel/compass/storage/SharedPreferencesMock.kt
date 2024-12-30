package com.marfeel.compass.storage
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import java.util.HashMap
import kotlin.collections.set

/**
 * Mock implementation of [SharedPreferences], which just saves data in memory using map.
 */
@Suppress("UNCHECKED_CAST")
internal class MockSharedPreference : SharedPreferences {

    var preferenceMap = HashMap<String, Any>()

    var uncommittedPreferenceMap = HashMap<String, Any>()

    private val preferenceEditor: MockSharedPreferenceEditor =
        MockSharedPreferenceEditor(preferenceMap, uncommittedPreferenceMap)

    private var listeners: MutableSet<OnSharedPreferenceChangeListener> = HashSet()

    override fun getAll(): Map<String, *> = preferenceMap

    override fun getString(key: String, defaultValue: String?): String? =
        preferenceMap.getOrDefault(key, defaultValue) as String?

    override fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? =
        preferenceMap.getOrDefault(key, defaultValue) as Set<String>?

    override fun getInt(key: String, defaultValue: Int): Int =
        preferenceMap[key] as Int? ?: defaultValue;

    override fun getLong(key: String, defaultValue: Long): Long =
        preferenceMap[key] as Long? ?: defaultValue;

    override fun getFloat(key: String, defaultValue: Float): Float =
        preferenceMap[key] as Float? ?: defaultValue;

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        preferenceMap[key] as Boolean? ?: defaultValue;

    override fun contains(key: String): Boolean =
        key in preferenceMap

    override fun edit(): SharedPreferences.Editor = preferenceEditor

    override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    class MockSharedPreferenceEditor(
        private val preferenceMap: MutableMap<String, Any>,
        private val uncommittedPreferenceMap: MutableMap<String, Any>,
        private var uncommittedRemoveKeys: MutableList<String> = ArrayList()
    ) : SharedPreferences.Editor {

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            if (value == null) {
                uncommittedPreferenceMap.remove(key)
            } else {
                uncommittedPreferenceMap[key] = value
            }

            return this
        }

        override fun putStringSet(key: String, value: Set<String>?): SharedPreferences.Editor {
            if (value == null) {
                uncommittedPreferenceMap.remove(key)
            } else {
                uncommittedPreferenceMap[key] = value
            }

            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            uncommittedPreferenceMap[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            uncommittedPreferenceMap[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            uncommittedPreferenceMap[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            uncommittedPreferenceMap[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            uncommittedPreferenceMap.remove(key)
            uncommittedRemoveKeys.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            uncommittedRemoveKeys.clear()
            preferenceMap.clear()
            uncommittedPreferenceMap.clear()
            return this
        }

        override fun commit(): Boolean {
            uncommittedRemoveKeys.forEach {
                preferenceMap.remove(it)
            }

            uncommittedPreferenceMap.forEach {
                preferenceMap[it.key] = it.value
            }

            uncommittedRemoveKeys.clear()
            uncommittedPreferenceMap.clear()
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
