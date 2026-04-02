package com.marfeel.compass.experiences

internal interface NetworkInfoProvider {
	fun getConnectionSpeedKbps(): Int?
	fun getConnectionType(): String?
}
