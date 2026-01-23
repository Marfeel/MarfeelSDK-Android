package com.marfeel.compass.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.marfeel.compass.core.model.compass.Session
import java.lang.reflect.Type
import com.marfeel.compass.core.model.compass.UserType
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.*

internal class Storage(
	private val context: Context
) {
	companion object {
		private const val encryptedStorageName = "EncryptedStorage"
		private const val fallbackStorageName = "FallbackStorage"
		private const val rawStorageName = "RawStorage"
		private const val originalUserIdKey = "originalUserId_key"
		private const val registeredUserIdKey = "registeredUserId_key"
		private const val userTypeKey = "userType_key"
		private const val firstSessionTimeStampKey = "firstSessionTimeStamp_key"
		private const val previousSessionLastPingTimeStampKey =
			"previousSessionLastPingTimeStamp_key"
		private const val lastPingTimeStampKey = "lastPingTimeStamp_key"
		private const val userVarsKey = "userVars_key"
		private const val userSegmentsKey = "userSegments_key"
		private const val userConsent = "userConsent_key"
		private const val sessionKey = "session_key"
		private const val sessionVarsKey = "sessionVars_key";
		private const val landingPageKey = "landingPage_key"
	}

	private val gson:Gson by lazy { Gson() }

	private val persistentPreferences: SharedPreferences by lazy {
		context.getSharedPreferences(rawStorageName, Context.MODE_PRIVATE)
	}

	private val legacyPrefs: SharedPreferences? by lazy {
		try {
			runBlocking {
				withTimeout(2000L) {
					val masterKey = MasterKey.Builder(context)
						.setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
						.build()

					EncryptedSharedPreferences.create(
						context,
						encryptedStorageName,
						masterKey,
						EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
						EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
					)
				}
			}
		} catch (_: Exception) {
			context.getSharedPreferences(fallbackStorageName, Context.MODE_PRIVATE)
		}
	}

	private fun migrateLegacyPrefsIfNeeded() {
		if (persistentPreferences.all.isEmpty()) {
			legacyPrefs?.let { legacy ->
				persistentPreferences.edit {
					legacy.all.forEach { (key, value) ->
						when (value) {
							is String -> putString(key, value)
							is Int -> putInt(key, value)
							is Boolean -> putBoolean(key, value)
							is Float -> putFloat(key, value)
							is Long -> putLong(key, value)
						}
					}
				}
			}
		}
	}

	private val inMemoryPreferences: SharedPreferences by lazy { MockSharedPreference() }

	private var preferences: SharedPreferences

	init {
        clearAllPreferences()
		migrateLegacyPrefsIfNeeded()
		preferences = togglePreferences(persistentPreferences.getBoolean(userConsent, true))
	}

	private fun togglePreferences(hasConsent: Boolean, sync: Boolean = false): SharedPreferences {
		val oldPreferences = preferences
		preferences = if(hasConsent) persistentPreferences else inMemoryPreferences

		if (sync && oldPreferences != preferences) {
			preferences.edit {
				oldPreferences.all.forEach { (t, any) ->
					if (any is String) {
						putString(t, any)
					} else if (any is Int) {
						putInt(t, any)
					} else if (any is Long) {
						putLong(t, any)
					} else if (any is Float) {
						putFloat(t, any)
					} else if (any is Boolean) {
						putBoolean(t, any)
					} else if (any is Set<*>) {
						putStringSet(t, any as MutableSet<String>?)
					}
				}

				val oldPreferencesEditor = oldPreferences.edit()

				oldPreferencesEditor.clear()
				oldPreferencesEditor.apply()
			}
		}

		return preferences
	}

	fun updateFirstSessionTimeStamp(firstSessionTimeStamp: Long) {
		setFirstSessionTimeStamp(firstSessionTimeStamp)
	}

	fun readFirstSessionTimeStamp(): Long =
		getFirstSessionTimeStamp()?.toLong() ?: trackFirstSession()

	private fun trackFirstSession(): Long {
		val timeStamp = currentTimeStampInSeconds()
		setFirstSessionTimeStamp(timeStamp)
		return timeStamp
	}

	private fun setFirstSessionTimeStamp(firstSessionTimeStamp: Long) =
		preferences.edit {
			putString(firstSessionTimeStampKey, firstSessionTimeStamp.toString())
		}

	private fun getFirstSessionTimeStamp(): String? =
		preferences.getString(firstSessionTimeStampKey, null)

	fun updateUserId(userId: String) {
		setRegisteredUserId(userId)
	}

	fun readUserId(): String =
		getRegisteredUserId() ?: getOriginalUserId()

	fun readRegisteredUserId(): String? =
		getRegisteredUserId()

	fun readOriginalUserId(): String =
		getOriginalUserId()

	private fun getRegisteredUserId(): String? =
		preferences.getString(registeredUserIdKey, null)

	private fun setRegisteredUserId(userId: String) {
		preferences.edit {
			putString(registeredUserIdKey, userId)
		}
	}

	private fun getOriginalUserId(): String {
		val originalUserId = preferences.getString(originalUserIdKey, null)
		return if (originalUserId != null) {
			originalUserId
		} else {
			val newId = UUID.randomUUID().toString()
			setOriginalUserId(newId)
			newId
		}
	}

	private fun setOriginalUserId(userId: String) {
		preferences.edit {
			putString(originalUserIdKey, userId)
		}
	}

	fun updateUserType(userType: UserType) {
		setUserType(userType)
	}

	private fun setUserType(userType: UserType) {
		preferences.edit {
			putString(userTypeKey, userType.numericValue.toString())
		}
	}

	fun readUserType(): UserType =
		getUserType()

	private fun getUserType(): UserType =
		when (val type = preferences.getString(userTypeKey, null)?.toInt()) {
			null,
			UserType.Anonymous.numericValue -> UserType.Anonymous
			UserType.Logged.numericValue -> UserType.Logged
			UserType.Paid.numericValue -> UserType.Paid
			else -> UserType.Custom(type)
		}

	fun updateLastPingTimeStamp(timeStamp: Long) {
		setLastPingTimeStamp(timeStamp)
	}

	private fun setLastPingTimeStamp(timeStamp: Long) =
		preferences.edit {
			putLong(lastPingTimeStampKey, timeStamp)
		}

	fun readLastPingTimeStamp(): Long? {
		val lastPingTimeStamp = getLastPingTimeStamp()
		return if (lastPingTimeStamp == 0L) {
			null
		} else {
			lastPingTimeStamp
		}
	}

	private fun getLastPingTimeStamp(): Long =
		preferences.getLong(lastPingTimeStampKey, 0L)


	fun updatePreviousSessionLastPingTimeStamp(timeStamp: Long) =
		setPreviousSessionLastPingTimeStamp(timeStamp)

	private fun setPreviousSessionLastPingTimeStamp(timeStamp: Long) =
		preferences.edit {
			putLong(previousSessionLastPingTimeStampKey, timeStamp)
		}

	fun readPreviousSessionLastPingTimeStamp(): Long? {
		val lastTimeStamp = getPreviousSessionLastPingTimeStamp()
		return if (lastTimeStamp == 0L) {
			null
		} else {
			lastTimeStamp
		}
	}

	private fun getPreviousSessionLastPingTimeStamp(): Long =
		preferences.getLong(previousSessionLastPingTimeStampKey, 0L)

	fun setUserVar(name: String, value: String) {
		val vars = getUserVars().toMutableMap()
		vars[name] = value
		setUserVars(vars)
	}

	private fun setUserVars(vars: Map<String, String>) {
		preferences.edit {
			putString(userVarsKey, gson.toJson(vars).toString())
		}
	}

	fun readUserVars(): Map<String, String> =
		getUserVars()

	private fun getUserVars(): Map<String, String> {
		val mapType: Type = object : TypeToken<Map<String, String>>() {}.type

		return gson.fromJson(preferences.getString(userVarsKey, "{}"), mapType)
	}


	fun setUserSegment(name: String) {
		val userSegments = getUserSegments().toMutableList()
		if (!userSegments.contains(name)) {
			userSegments.add(name)
			setUserSegments(userSegments)
		}
	}

	fun setUserSegment(segments: List<String>) {
		setUserSegments(segments)
	}

	fun removeUserSegment(name: String) {
		val userSegments = getUserSegments().toMutableList()
		userSegments.remove(name)
		setUserSegments(userSegments)
	}

	fun clearUserSegments() {
		setUserSegments(listOf())
	}

	private fun setUserSegments(vars: List<String>) {
		preferences.edit {
			putString(userSegmentsKey, gson.toJson(vars).toString())
		}
	}

	fun readUserSegments(): List<String> =
		getUserSegments()

	private fun getUserSegments(): List<String> {
		val mapType: Type = object : TypeToken<List<String>>() {}.type

		return gson.fromJson(preferences.getString(userSegmentsKey, "[]"), mapType)
	}

	fun updateUserConsent(hasConsent: Boolean) {
		val previousUserConsent = getUserConsent()

		if (previousUserConsent != hasConsent) {
			togglePreferences(hasConsent, previousUserConsent != null)
		}

		setUserConsent(hasConsent)
	}

	private fun setUserConsent(hasConsent: Boolean) {
		preferences.edit {
			putBoolean(userConsent, hasConsent)
		}
	}

	fun readUserConsent(): Boolean? =
		getUserConsent()

	private fun getUserConsent(): Boolean? =
		if (preferences.contains(userConsent)) preferences.getBoolean(userConsent, false) else null

	fun setSession(session: Session) {
		saveSession(session)
	}

	private fun saveSession(session: Session) {
		preferences.edit {
			putString(sessionKey, gson.toJson(session).toString())
		}
	}

	fun readSession(): Session? =
		readAndParseSession()

	private fun readAndParseSession(): Session? {
		val json = preferences.getString(sessionKey, null) ?: return null

		return gson.fromJson(json, Session::class.java)
	}

	fun setSessionVar(name: String, value: String) {
		val vars = getSessionVars().toMutableMap()
		vars[name] = value
		setSessionVars(vars)
	}

	private fun setSessionVars(vars: Map<String, String>) {
		preferences.edit {
			putString(sessionVarsKey, gson.toJson(vars).toString())
		}
	}

	fun readSessionVars(): Map<String, String> =
		getSessionVars()

	private fun getSessionVars(): Map<String, String> {
		val mapType: Type = object : TypeToken<Map<String, String>>() {}.type

		return gson.fromJson(preferences.getString(sessionVarsKey, "{}"), mapType)
	}

	fun clearSessionVars() {
		setSessionVars(mapOf())
	}

	fun setLandingPage(url: String?) {
		updateLandingPage(url)
	}

	private fun updateLandingPage(url: String?) {
		preferences.edit {
			putString(landingPageKey, url)
		}
	}

	fun readLadingPage(): String? =
		getLandingPage()

	private fun getLandingPage(): String? =
		preferences.getString(landingPageKey, null)

    fun clearAllPreferences() {
        val names = listOf("RawStorage", "EncryptedStorage", "FallbackStorage")
        names.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
        }

        // Also clear in-memory prefs if needed
        inMemoryPreferences.edit().clear().apply()
    }
}
