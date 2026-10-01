package com.marfeel.compass.cdp

/**
 * The well-known identity types for `Cdp.setIdentity` / `Cdp.deleteIdentity`. Mirrors
 * `cdp-core/pkg/identity/identity.go`. The set is **open** — a site can register its
 * own — so this is documentation, never a validation list.
 *
 * The first group is **stable**: linking one makes the user registered and their data
 * permanent. The second is **device-bound** and leaves them anonymous, aged out 180
 * days after the last write. Prefer a stable type when your CRM owns the identifier;
 * `isDeterministic = true` forces a device-bound one to registered.
 *
 * `email_hash` and `phone_hash` are deliberately absent: the server validates nothing,
 * so they silently create a parallel user that never merges with `*_sha256`. Hash with
 * [Cdp.hashEmail] / [Cdp.hashPhone] and send [EMAIL_SHA256] / [PHONE_SHA256] instead.
 */
object CdpIdentityTypes {
	// Stable → registered user, data permanent.
	const val EMAIL = "email"
	const val EMAIL_SHA256 = "email_sha256"
	const val PHONE = "phone"
	const val PHONE_SHA256 = "phone_sha256"
	const val EXTERNAL_ID = "external_id"
	const val CUSTOMER_ID = "customer_id"
	const val REGISTERED_USER_ID = "registered_user_id"

	// Device-bound → anonymous, aged out 180 days after the last write.
	const val LOGIN_ID = "login_id"
	const val CRM_ID = "crm_id"
	const val COOKIE = "cookie"
	const val DEVICE_ID = "device_id"
	const val MAID = "maid"
	const val IDFA = "idfa"
	const val IDFV = "idfv"
	const val RAMPID = "rampid"
	const val PUSH_TOKEN = "push_token"

	val STABLE: Set<String> = setOf(
		EMAIL, EMAIL_SHA256, PHONE, PHONE_SHA256, EXTERNAL_ID, CUSTOMER_ID, REGISTERED_USER_ID
	)

	val DEVICE_BOUND: Set<String> = setOf(
		LOGIN_ID, CRM_ID, COOKIE, DEVICE_ID, MAID, IDFA, IDFV, RAMPID, PUSH_TOKEN
	)
}
