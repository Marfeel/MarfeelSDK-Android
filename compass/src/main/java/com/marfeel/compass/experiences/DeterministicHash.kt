package com.marfeel.compass.experiences

import java.util.zip.CRC32

internal data class WeightedVariant(val id: String, val weight: Double)

/**
 * Must stay in lockstep with the web SDK `deterministic.ts` and Croupier `DeterministicHash`,
 * which CompassMiddleware also recomputes (`experimentFallback.ts`) for readers without a stored
 * `mrfExperiment_<groupId>` user var.
 */
internal object DeterministicHash {
	const val MIXED_HASH_VERSION = 2
	private const val UNSIGNED_INT_RANGE = 4_294_967_296.0
	private const val UNSIGNED_INT_MASK = 0xFFFFFFFFL

	fun crc32(key: String): Long =
		CRC32().apply { update(key.toByteArray(Charsets.UTF_8)) }.value

	fun fmix32(hash: Int): Int {
		var mixed = hash
		mixed = mixed xor (mixed ushr 16)
		mixed *= 0x85ebca6b.toInt()
		mixed = mixed xor (mixed ushr 13)
		mixed *= 0xc2b2ae35.toInt()
		mixed = mixed xor (mixed ushr 16)
		return mixed
	}

	/** `[0, 1)` value of [key]: plain CRC32 for hash version 1 (or none), CRC32 + `fmix32` from 2. */
	fun fraction(key: String, hashVersion: Int? = null): Double {
		val crc = crc32(key)
		val hash = if ((hashVersion ?: 1) >= MIXED_HASH_VERSION) {
			fmix32(crc.toInt()).toLong() and UNSIGNED_INT_MASK
		} else {
			crc
		}
		return hash / UNSIGNED_INT_RANGE
	}

	/** Standalone USER experiments: always hash version 1, as the web SDK does. */
	fun seededPercentage(userId: String, seed: String): Double = fraction("$seed:$userId") * 100

	/**
	 * Test-group variant for [userId], the same pick as web `selectVariant` and the
	 * CompassMiddleware fallback. Null when no variant has a positive weight.
	 */
	fun selectVariant(variants: List<WeightedVariant>, userId: String, seed: String, hashVersion: Int?): String? {
		val weighted = variants.filter { it.weight > 0 }
		val totalWeight = weighted.sumOf { it.weight }
		if (weighted.isEmpty() || totalWeight <= 0) return null

		val value = fraction("$seed:$userId", hashVersion) * totalWeight
		var accumulated = 0.0

		for (variant in weighted) {
			accumulated += variant.weight
			if (value < accumulated) return variant.id
		}

		return weighted.last().id
	}
}
