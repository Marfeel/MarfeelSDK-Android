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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.*
import kotlin.coroutines.CoroutineContext

internal class Storage(
	private val context: Context,
	coroutineContext: CoroutineContext
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

	private val storageScope: CoroutineScope = CoroutineScope(coroutineContext)
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
		storageScope.launch {
			setFirstSessionTimeStamp(firstSessionTimeStamp)
		}
	}

	fun readFirstSessionTimeStamp(): Long =
		runBlocking(storageScope.coroutineContext) {
			getFirstSessionTimeStamp()?.toLong() ?: trackFirstSession()
		}

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
		storageScope.launch {
			setRegisteredUserId(userId)
		}
	}

	fun readUserId(): String =
		runBlocking(storageScope.coroutineContext) {
			getRegisteredUserId() ?: getOriginalUserId()
		}

	fun readRegisteredUserId(): String? =
		runBlocking(storageScope.coroutineContext) {
			getRegisteredUserId()
		}

	fun readOriginalUserId(): String =
		runBlocking(storageScope.coroutineContext) {
			getOriginalUserId()
		}

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
		storageScope.launch {
			setUserType(userType)
		}
	}

	private fun setUserType(userType: UserType) {
		preferences.edit {
			putString(userTypeKey, userType.numericValue.toString())
		}
	}

	fun readUserType(): UserType =
		runBlocking {
			getUserType()
		}

	private fun getUserType(): UserType =
		when (val type = preferences.getString(userTypeKey, null)?.toInt()) {
			null,
			UserType.Anonymous.numericValue -> UserType.Anonymous
			UserType.Logged.numericValue -> UserType.Logged
			UserType.Paid.numericValue -> UserType.Paid
			else -> UserType.Custom(type)
		}

	fun updateLastPingTimeStamp(timeStamp: Long) {
		storageScope.launch {
			setLastPingTimeStamp(timeStamp)
		}
	}

	private fun setLastPingTimeStamp(timeStamp: Long) =
		preferences.edit {
			putLong(lastPingTimeStampKey, timeStamp)
		}

	fun readLastPingTimeStamp(): Long? =
		runBlocking {
			val lastPingTimeStamp = getLastPingTimeStamp()
			if (lastPingTimeStamp == 0L) {
				null
			} else {
				lastPingTimeStamp
			}
		}

	private fun getLastPingTimeStamp(): Long =
		preferences.getLong(lastPingTimeStampKey, 0L)


	fun updatePreviousSessionLastPingTimeStamp(timeStamp: Long) =
		storageScope.launch {
			setPreviousSessionLastPingTimeStamp(timeStamp)
		}

	private fun setPreviousSessionLastPingTimeStamp(timeStamp: Long) =
		preferences.edit {
			putLong(previousSessionLastPingTimeStampKey, timeStamp)
		}

	fun readPreviousSessionLastPingTimeStamp(): Long? =
		runBlocking {
			val lastTimeStamp = getPreviousSessionLastPingTimeStamp()
			if (lastTimeStamp == 0L) {
				null
			} else {
				lastTimeStamp
			}
		}

	private fun getPreviousSessionLastPingTimeStamp(): Long =
		preferences.getLong(previousSessionLastPingTimeStampKey, 0L)

	fun setUserVar(name: String, value: String) {
		val vars = getUserVars().toMutableMap()

		storageScope.launch {
			vars[name] = value
			setUserVars(vars)
		}
	}

	private fun setUserVars(vars: Map<String, String>) {
		preferences.edit {
			putString(userVarsKey, gson.toJson(vars).toString())
		}
	}

	fun readUserVars(): Map<String, String> =
		runBlocking {
			getUserVars()
		}

	private fun getUserVars(): Map<String, String> {
		val mapType: Type = object : TypeToken<Map<String, String>>() {}.type

		return gson.fromJson(preferences.getString(userVarsKey, "{}"), mapType)
	}


	fun setUserSegment(name: String) {
		val userSegments = getUserSegments().toMutableList()

		storageScope.launch {
			if (!userSegments.contains(name)) {
				userSegments.add(name)
				setUserSegments(userSegments)
			}
		}
	}

	fun setUserSegment(segments: List<String>) {
		storageScope.launch {
			setUserSegments(segments)
		}
	}

	fun removeUserSegment(name: String) {
		val userSegments = getUserSegments().toMutableList()

		storageScope.launch {
			userSegments.remove(name)
			setUserSegments(userSegments)
		}
	}

	fun clearUserSegments() {
		storageScope.launch {
			setUserSegments(listOf())
		}
	}

	private fun setUserSegments(vars: List<String>) {
		preferences.edit {
			putString(userSegmentsKey, gson.toJson(vars).toString())
		}
	}

	fun readUserSegments(): List<String> =
		runBlocking {
			getUserSegments()
		}

	private fun getUserSegments(): List<String> {
		val mapType: Type = object : TypeToken<List<String>>() {}.type

		return gson.fromJson(preferences.getString(userSegmentsKey, "[]"), mapType)
	}

	fun updateUserConsent(hasConsent: Boolean) {
		val previousUserConsent = getUserConsent()

		if (previousUserConsent != hasConsent) {
			togglePreferences(hasConsent, previousUserConsent != null)
		}

		storageScope.launch {
			setUserConsent(hasConsent)
		}
	}

	private fun setUserConsent(hasConsent: Boolean) {
		preferences.edit {
			putBoolean(userConsent, hasConsent)
		}
	}

	fun readUserConsent(): Boolean? =
		runBlocking {
			getUserConsent()
		}

	private fun getUserConsent(): Boolean? =
		if (preferences.contains(userConsent)) preferences.getBoolean(userConsent, false) else null

	fun setSession(session: Session) {
		storageScope.launch {
			saveSession(session)
		}
	}

	private fun saveSession(session: Session) {
		preferences.edit {
			putString(sessionKey, gson.toJson(session).toString())
		}
	}

	fun readSession(): Session? =
		runBlocking {
			readAndParseSession()
		}

	private fun readAndParseSession(): Session? {
		val json = preferences.getString(sessionKey, null) ?: return null

		return gson.fromJson(json, Session::class.java)
	}

	fun setSessionVar(name: String, value: String) {
		val vars = getSessionVars().toMutableMap()

		storageScope.launch {
			vars[name] = value
			setSessionVars(vars)
		}
	}

	private fun setSessionVars(vars: Map<String, String>) {
		preferences.edit {
			putString(sessionVarsKey, gson.toJson(vars).toString())
		}
	}

	fun readSessionVars(): Map<String, String> =
		runBlocking {
			getSessionVars()
		}

	private fun getSessionVars(): Map<String, String> {
		val mapType: Type = object : TypeToken<Map<String, String>>() {}.type

		return gson.fromJson(preferences.getString(sessionVarsKey, "{}"), mapType)
	}

	fun clearSessionVars() {
		storageScope.launch {
			setSessionVars(mapOf())
		}
	}

	fun setLandingPage(url: String?) {
		storageScope.launch {
			updateLandingPage(url)
		}
	}

	private fun updateLandingPage(url: String?) {
		preferences.edit {
			putString(landingPageKey, url)
		}
	}

	fun readLadingPage(): String? =
		runBlocking {
			getLandingPage()
		}

	private fun getLandingPage(): String? =
		preferences.getString(landingPageKey, null)
}
