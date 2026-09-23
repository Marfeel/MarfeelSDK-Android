package com.marfeel.compass.cdp

import java.security.MessageDigest

/**
 * Normalisation + SHA-256 for the hashed identity types. The rules match the server
 * (`cdp-core/pkg/identity/normalize.go`) exactly — a divergence here produces a digest
 * the CDP will never match against its own.
 */
internal object CdpHash {
	/** `trim` + lower-case: the server's own rule for emails. */
	fun normalizeEmail(email: String): String = email.trim().lowercase()

	/**
	 * Trimmed but **never** case-folded, matching `canonicalPhone`. No further
	 * canonicalisation either: `+34600111222` and `600111222` are two users.
	 */
	fun normalizePhone(phone: String): String = phone.trim()

	fun hashEmail(email: String): String = sha256Hex(normalizeEmail(email))

	fun hashPhone(phone: String): String = sha256Hex(normalizePhone(phone))

	/** 64 lowercase hex chars. Throws only if the platform lacks SHA-256, which Android never does. */
	fun sha256Hex(value: String): String {
		val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
		val out = StringBuilder(digest.size * 2)
		for (byte in digest) {
			val v = byte.toInt() and 0xFF
			out.append(HEX[v ushr 4])
			out.append(HEX[v and 0x0F])
		}
		return out.toString()
	}

	private val HEX = "0123456789abcdef".toCharArray()
}

/** The email as a consent subject: `id_type` + `id_value`. */
internal data class CdpConsentSubject(val idType: String, val idValue: String)

/**
 * Reduces an email to its consent subject: normalised and, when hashing is available,
 * its SHA-256 hex digest under `email_sha256`; the normalised plain address under
 * `email` otherwise — the server then hashes it. **Never throws.**
 */
internal fun consentEmailIdentity(email: String): CdpConsentSubject =
	try {
		CdpConsentSubject(CdpIdentityTypes.EMAIL_SHA256, CdpHash.hashEmail(email))
	} catch (_: Exception) {
		CdpConsentSubject(CdpIdentityTypes.EMAIL, CdpHash.normalizeEmail(email))
	}
