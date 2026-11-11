package com.marfeel.compass.experiences

import com.google.gson.JsonObject

/**
 * A test group whose variant comes from a user var the client sets, instead of our hash
 * (web SDK: croupier `experiments/userVar.ts`). The only reader of the `source`,
 * `userVarKey`, `userVarMapping` and `fallbackVariantId` fields of a group.
 */
internal object UserVarGroup {
	private const val SOURCE_USER_VAR = "USER_VAR"

	fun isUserVar(group: JsonObject): Boolean = group.string("source") == SOURCE_USER_VAR

	/**
	 * The variant mapped to the current value of the user var, else the fallback variant.
	 * Values are compared exactly. Null when the reader is out of the test: no value, an
	 * unmapped value without fallback, or a variant that is not in the group.
	 */
	fun resolveVariant(group: JsonObject, userVars: Map<String, String>): String? {
		val value = group.string("userVarKey")?.let { userVars[it] }
		val mapped = value?.let { current ->
			group.objects("userVarMapping")
				.firstOrNull { it.string("value") == current }
				?.string("variantId")
		}
		val variantId = mapped ?: group.string("fallbackVariantId")

		return variantId?.takeIf { id -> group.objects("variants").any { it.string("id") == id } }
	}

	private fun JsonObject.string(key: String): String? =
		get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

	private fun JsonObject.objects(key: String): List<JsonObject> =
		get(key)?.takeIf { it.isJsonArray }?.asJsonArray
			?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
			.orEmpty()
}
