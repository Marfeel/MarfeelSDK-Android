package com.marfeel.compass.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.marfeel.compass.core.UserType
import com.marfeel.compass.core.currentTimeStampInSeconds
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
		private const val storageName = "EncryptedStorage"
		private const val originalUserIdKey = "originalUserId_key"
		private const val registeredUserIdKey = "registeredUserId_key"
		private const val userTypeKey = "userType_key"
		private const val firstSessionTimeStampKey = "firstSessionTimeStamp_key"
		private const val currentSessionTimeStampKey = "currentSessionTimestampTimeStamp_key"
		private const val previousSessionLastPingTimeStampKey =
			"previousSessionLastPingTimeStamp_key"
		private const val lastPingTimeStampKey = "lastPingTimeStamp_key"
	}

	private val storageScope: CoroutineScope = CoroutineScope(coroutineContext)

	private val preferences: SharedPreferences by lazy {
		val masterKey = MasterKey.Builder(context)
			.setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
			.build()
		EncryptedSharedPreferences.create(
			context,
			storageName,
			masterKey,
			EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
			EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
		)
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
			else -> UserType.CustomUserJourney(type)
		}

	fun updateCurrentSessionTimeStamp(newSessionTimeStamp: Long) {
		storageScope.launch {
			val lastPingTimeStamp = getLastPingTimeStamp()

			setCurrentSessionTimeStamp(newSessionTimeStamp)

			if (newSessionTimeStamp > lastPingTimeStamp) {
				setPreviousSessionLastPingTimeStamp(lastPingTimeStamp)
			}
		}
	}

	private fun setCurrentSessionTimeStamp(timeStamp: Long) {
		preferences.edit {
			putLong(currentSessionTimeStampKey, timeStamp)
		}
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

	private fun getLastPingTimeStamp(): Long =
		preferences.getLong(lastPingTimeStampKey, 0L)

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
}
