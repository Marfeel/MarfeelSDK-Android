package com.marfeel.compass.experiences

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal class AndroidNetworkInfoProvider(private val context: Context) : NetworkInfoProvider {
	override fun getConnectionSpeedKbps(): Int? {
		val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
			?: return null
		val network = cm.activeNetwork ?: return null
		val capabilities = cm.getNetworkCapabilities(network) ?: return null
		return capabilities.getLinkDownstreamBandwidthKbps()
	}

	override fun getConnectionType(): String? {
		val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
			?: return null
		val network = cm.activeNetwork ?: return null
		val capabilities = cm.getNetworkCapabilities(network) ?: return null
		return when {
			capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
			capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
			capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
			else -> null
		}
	}
}
