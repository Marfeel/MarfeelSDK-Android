package com.marfeel.compass.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
			getFirstSessionTimeStamp()
		}

	private fun setFirstSessionTimeStamp(firstSessionTimeStamp: Long) =
		preferences.edit {
			putLong(firstSessionTimeStampKey, firstSessionTimeStamp)
		}

	private fun getFirstSessionTimeStamp(): Long =
		preferences.getLong(firstSessionTimeStampKey, System.currentTimeMillis())

	fun updatePreviousSessionTimeStamp(previousSessionTimeStamp: Long) {
		storageScope.launch {
			setPreviousSessionTimeStamp(previousSessionTimeStamp)
		}
	}

	fun readPreviousSessionTimeStamp(): Long =
		runBlocking(storageScope.coroutineContext) {
			getPreviousSessionTimeStamp()
		}

	private fun setPreviousSessionTimeStamp(previousSessionTimeStamp: Long) =
		preferences.edit {
			putLong(previousSessionTimeStampKey, previousSessionTimeStamp)
		}

	private fun getPreviousSessionTimeStamp(): Long =
		preferences.getLong(previousSessionTimeStampKey, getFirstSessionTimeStamp())

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

}
