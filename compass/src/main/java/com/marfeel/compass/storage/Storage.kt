package com.marfeel.compass.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import com.marfeel.compass.core.model.compass.UserType
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.coroutines.CoroutineContext


internal class Storage(
	private val context: Context,
	coroutineContext: CoroutineContext
) {
	companion object {
		private const val encryptedStorageName = "EncryptedStorage"
		private const val fallbackStorageName = "FallbackStorage"
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
	}

	private val storageScope: CoroutineScope = CoroutineScope(coroutineContext)
	private val gson:Gson by lazy { Gson() }

	/**
	 * EncryptedSharedPreferences support is kind of buggy, some devices do not implement properly
	 * KeyStore. Applying fallback to plain text when encryption explodes, same strategy applied by
	 * google: https://github.com/google/tink/blob/master/java_src/src/main/java/com/google/crypto/tink/integration/android/AndroidKeysetManager.java#L101.
	 */
	private val preferences: SharedPreferences by lazy {
		try {
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
		} catch (_: Exception) {
			context.getSharedPreferences(fallbackStorageName, Context.MODE_PRIVATE)
		}
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
}
