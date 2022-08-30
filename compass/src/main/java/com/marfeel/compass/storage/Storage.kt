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
		private const val userIdKey = "userId_key"
		private const val userTypeKey = "userType_key"
		private const val firstSessionTimeStampKey = "firstSessionTimeStamp_key"
		private const val previousSessionTimeStampKey = "previousSessionTimestampTimeStamp_key"
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

	fun updatePreviousSessionTimeStamp(previousSessionTimeStamp: Long) {
		storageScope.launch {
			setPreviousSessionTimeStamp(previousSessionTimeStamp)
		}
	}

	fun readPreviousSessionTimeStamp(): Long? =
		runBlocking(storageScope.coroutineContext) {
			getPreviousSessionTimeStamp()?.toLong()
		}

	private fun setPreviousSessionTimeStamp(previousSessionTimeStamp: Long) =
		preferences.edit {
			putString(previousSessionTimeStampKey, previousSessionTimeStamp.toString())
		}

	private fun getPreviousSessionTimeStamp(): String? =
		preferences.getString(previousSessionTimeStampKey, null)

	fun updateUserId(userId: String) {
		storageScope.launch {
			setUserId(userId)
		}
	}

	fun readUserId(): String =
		runBlocking(storageScope.coroutineContext) {
			val userID = getUserId()
			if (userID == null) {
				val newId = UUID.randomUUID().toString()
				setUserId(newId)
				newId
			} else {
				userID
			}
		}

	private fun getUserId(): String? =
		preferences.getString(userIdKey, null)

	private fun setUserId(userId: String) {
		preferences.edit {
			putString(userIdKey, userId)
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
}
