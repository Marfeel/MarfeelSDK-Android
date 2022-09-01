package com.marfeel.compass.network

internal data class PingRequest(
	val ac: String,
	val t: Long,
	val url: String,
	val c: String,
	val pp: String,
	val p: String,
	val u: String,
	val s: String,
	val a: Int,
	val n: Long,
	val ut: Int,
	val sui: String,
	val sc: Int,
	val fv: Long,
	val lv: Long?,
	val l: Int,
	val ps: Long,
	val conv: String?,
	val pageType: Int,
	val v:String
)

internal data class RfvRequest(
	val ac: String,
	val u: String,
	val sui: String?,
)
