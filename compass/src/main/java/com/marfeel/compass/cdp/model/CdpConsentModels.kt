package com.marfeel.compass.cdp.model

import com.google.gson.annotations.SerializedName

/**
 * A publisher consent the visitor accepted or rejected — a privacy policy, a
 * marketing opt-in, a newsletter subscription. Unrelated to the CMP / cookie consent
 * set through `CompassTracking.setUserConsent`, which gates tracking.
 */
enum class CdpConsentStatus(val wireValue: String) {
	ACCEPTED("accepted"),
	REJECTED("rejected");

	companion object {
		/**
		 * The server answers in its short form (`accept` / `reject`); anything that
		 * starts with `accept` reads as [ACCEPTED], everything else as [REJECTED].
		 */
		internal fun fromWire(value: String?): CdpConsentStatus =
			if (value != null && value.startsWith("accept")) ACCEPTED else REJECTED
	}
}

/** Caller-facing shape of a consent decision, see `Cdp.trackConsent`. */
data class CdpConsent(
	val consentId: String,
	/** The version's id from CDP > Settings > Consents. Opaque: sent verbatim. */
	val versionId: String,
	val status: CdpConsentStatus,
	val metadata: Map<String, String>? = null,
	/**
	 * Linked to the master when one exists (as `setIdentity` would); otherwise the
	 * subject of the decision. Hashed on the device before it leaves.
	 */
	val email: String? = null
)

/** Catalog lookup, see `Cdp.getConsent`. */
data class CdpConsentRef(
	val consentId: String,
	/** Absent → the consent's default version, as configured in Compass. */
	val versionId: String? = null
)

/** Status lookup, see `Cdp.hasConsent`. */
data class CdpConsentQuery(
	val consentId: String,
	/** When given, only an accept at exactly this version counts. Absent → any accepted version. */
	val versionId: String? = null,
	/** Sent alongside the master when both exist, so an email accepted elsewhere answers before it is linked here. */
	val email: String? = null
)

/**
 * How the visitor signals consent. Carried through **verbatim** from the server
 * (`accept_method`), so the value is a plain string; these are the known ones.
 */
object CdpConsentAcceptMethod {
	const val CHECK_BOX = "check-box"
	const val PRE_CHECKED = "pre-checked"
	/** Show no box at all — submitting the form is the consent. */
	const val FORM_SUBMIT = "form-submit"
}

/** Whether a visitor who already accepted is prompted again. */
enum class CdpConsentShowPolicy(val wireValue: String) {
	ALWAYS("always"),
	IF_NOT_ACCEPTED("if-not-accepted");

	companion object {
		/**
		 * Only the exact string `if-not-accepted` survives; absent, unknown or
		 * wrong-case folds to [ALWAYS] — prompting again is recoverable, suppressing a
		 * prompt is not.
		 */
		internal fun fromWire(value: String?): CdpConsentShowPolicy =
			if (value == IF_NOT_ACCEPTED.wireValue) IF_NOT_ACCEPTED else ALWAYS
	}
}

data class CdpConsentVersion(
	val versionId: String,
	val label: String,
	val date: String?,
	val displayPrompt: String?,
	val errorMessage: String?,
	val metadata: Map<String, String>
)

data class CdpConsentDefinition(
	val consentId: String,
	val name: String,
	val purpose: String?,
	val mandatory: Boolean,
	/** Render the box accordingly — [CdpConsentAcceptMethod.FORM_SUBMIT] means show no box at all. */
	val acceptMethod: String,
	/** Pair [CdpConsentShowPolicy.IF_NOT_ACCEPTED] with `hasConsent` — this is config, not a verdict. */
	val showPolicy: CdpConsentShowPolicy,
	/** null when the consent has no default version and none was requested. */
	val version: CdpConsentVersion?
)

/** `/cdp/consents/record/` answer. */
data class CdpConsentRecordResponse(
	/**
	 * The canonical master; may differ from the SDK's after a merge, in which case the
	 * SDK adopts it. Empty or absent for an anonymous decision, which is never adopted.
	 */
	@SerializedName("master_id")
	val masterId: String?,
	@SerializedName("consent_id")
	val consentId: String?,
	@SerializedName("consent_version_id")
	val consentVersionId: String?,
	/** The server's short vocabulary: `accept` / `reject`. Known mismatch, left as-is. */
	val status: String?,
	val recorded: Boolean = false,
	val stored: Boolean = false
)

/** The richer status object behind `hasConsent`; deliberately not public. */
internal data class CdpConsentCheck(
	val masterId: String?,
	val consentId: String,
	val versionId: String,
	val granted: Boolean,
	val answered: Boolean,
	val status: CdpConsentStatus? = null,
	val answeredVersionId: String? = null
)

/** What the SDK remembers about a decision it recorded without a master. */
internal data class CdpRememberedConsentDecision(
	val versionId: String,
	val status: CdpConsentStatus,
	val ts: Long
)

// region wire params (internal)

/**
 * `/cdp/consents/record/` body. `master_id` is serialized as an explicit `null` when
 * absent; [metadata] and [timezone] are **omitted** when null (the replay form sends
 * neither); [idType] / [idValue] are omitted when null. IP, User-Agent and URL are
 * read server-side and deliberately absent here.
 */
internal data class CdpConsentRecordParams(
	val siteId: Long,
	val masterId: String?,
	val consentId: String,
	val consentVersionId: String,
	val status: CdpConsentStatus,
	val metadata: Map<String, String>? = null,
	val timezone: String? = null,
	val idType: String? = null,
	val idValue: String? = null
)

/** `/cdp/consents/check/` body — a POST so the subject never travels in a URL. */
internal data class CdpConsentCheckParams(
	@SerializedName("site_id")
	val siteId: Long,
	@SerializedName("consent_id")
	val consentId: String,
	@SerializedName("consent_version_id")
	val consentVersionId: String? = null,
	@SerializedName("master_id")
	val masterId: String? = null,
	@SerializedName("id_type")
	val idType: String? = null,
	@SerializedName("id_value")
	val idValue: String? = null
)

internal data class CdpConsentCheckResponse(
	val masterId: String?,
	val consentId: String,
	val consentVersionId: String?,
	val granted: Boolean,
	val answered: Boolean,
	val status: String?,
	val answeredVersionId: String?
)

internal data class CdpConsentCatalogVersionItem(
	val versionId: String,
	val label: String,
	val date: String?,
	val displayPrompt: String?,
	val errorMessage: String?,
	val metadata: Map<String, String>
)

internal data class CdpConsentCatalogItem(
	val consentId: String,
	val name: String,
	val purpose: String?,
	val mandatory: Boolean,
	val acceptMethod: String,
	val showPolicy: String?,
	val version: CdpConsentCatalogVersionItem?
)

// endregion
