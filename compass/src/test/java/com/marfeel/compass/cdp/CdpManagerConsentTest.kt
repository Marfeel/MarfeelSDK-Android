package com.marfeel.compass.cdp

import com.marfeel.compass.cdp.model.CdpCachedIdentity
import com.marfeel.compass.cdp.model.CdpConsent
import com.marfeel.compass.cdp.model.CdpConsentCatalogItem
import com.marfeel.compass.cdp.model.CdpConsentCatalogVersionItem
import com.marfeel.compass.cdp.model.CdpConsentCheckParams
import com.marfeel.compass.cdp.model.CdpConsentCheckResponse
import com.marfeel.compass.cdp.model.CdpConsentQuery
import com.marfeel.compass.cdp.model.CdpConsentRecordParams
import com.marfeel.compass.cdp.model.CdpConsentRecordResponse
import com.marfeel.compass.cdp.model.CdpConsentRef
import com.marfeel.compass.cdp.model.CdpConsentShowPolicy
import com.marfeel.compass.cdp.model.CdpConsentStatus
import com.marfeel.compass.cdp.model.CdpRememberedConsentDecision
import com.marfeel.compass.cdp.model.CdpRfv
import com.marfeel.compass.cdp.store.CdpConsentMemoryStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

private const val FOO_BAR_SHA256 = "0c7e6a405862e402eb76a70f8a26fc732d07c32931e9fae9ab1582911d2e8a3b"

internal class CdpManagerConsentTest {
	private lateinit var env: CdpTestEnv
	private val manager get() = env.manager
	private val api get() = env.api
	private val account get() = env.account

	private val recorded = CdpConsentRecordResponse(
		masterId = null, consentId = "privacy", consentVersionId = "3", status = "accept", recorded = true, stored = true
	)

	private fun decision(
		consentId: String = "privacy",
		versionId: String = "3",
		status: CdpConsentStatus = CdpConsentStatus.ACCEPTED,
		metadata: Map<String, String>? = null,
		email: String? = null
	) = CdpConsent(consentId, versionId, status, metadata, email)

	@Before
	fun setUp() {
		env = CdpTestEnv()
	}

	// region record

	@Test
	fun `record POSTs the fixed body with the resolved master`() = runBlocking {
		env.masterId = UUID_A
		coEvery { api.recordConsent(any()) } returns recorded.copy(masterId = UUID_A)

		val result = manager.trackCdpConsent(decision(metadata = mapOf("source" to "footer")))

		val params = slot<CdpConsentRecordParams>()
		coVerify { api.recordConsent(capture(params)) }
		with(params.captured) {
			assertEquals(456L, siteId)
			assertEquals(UUID_A, masterId)
			assertEquals("privacy", consentId)
			assertEquals("3", consentVersionId)
			assertEquals(CdpConsentStatus.ACCEPTED, status)
			assertEquals(mapOf("source" to "footer"), metadata)
			assertEquals("Europe/Madrid", timezone)
			assertNull(idType)
			assertNull(idValue)
		}
		assertTrue(result!!.recorded)
	}

	@Test
	fun `record is not gated on CMP consent`() = runBlocking {
		env.consent = false
		coEvery { api.recordConsent(any()) } returns recorded
		manager.trackCdpConsent(decision())
		coVerify(exactly = 1) { api.recordConsent(any()) }
	}

	@Test
	fun `record sends master_id null when identity was never resolved`() = runBlocking {
		coEvery { api.recordConsent(any()) } returns recorded
		manager.trackCdpConsent(decision())
		val params = slot<CdpConsentRecordParams>()
		coVerify { api.recordConsent(capture(params)) }
		assertNull(params.captured.masterId)
	}

	@Test
	fun `record defaults metadata to an empty map and sends a null zone when the device reports none`() = runBlocking {
		env.timezone = null
		coEvery { api.recordConsent(any()) } returns recorded
		manager.trackCdpConsent(decision())
		val params = slot<CdpConsentRecordParams>()
		coVerify { api.recordConsent(capture(params)) }
		assertEquals(emptyMap<String, String>(), params.captured.metadata)
		assertNull(params.captured.timezone)
	}

	@Test
	fun `record sends every call including an accept following an accept`() = runBlocking {
		coEvery { api.recordConsent(any()) } returns recorded
		manager.trackCdpConsent(decision())
		manager.trackCdpConsent(decision())
		coVerify(exactly = 2) { api.recordConsent(any()) }
	}

	@Test
	fun `record accepts versionId 0`() = runBlocking {
		coEvery { api.recordConsent(any()) } returns recorded
		manager.trackCdpConsent(decision(versionId = "0"))
		val params = slot<CdpConsentRecordParams>()
		coVerify { api.recordConsent(capture(params)) }
		assertEquals("0", params.captured.consentVersionId)
	}

	@Test
	fun `record skips the POST when the consent id is missing`() = runBlocking {
		assertNull(manager.trackCdpConsent(decision(consentId = "")))
		coVerify(exactly = 0) { api.recordConsent(any()) }
	}

	@Test
	fun `record returns null and skips the POST when CDP is disabled`() = runBlocking {
		env.enabled = false
		assertNull(manager.trackCdpConsent(decision()))
		coVerify(exactly = 0) { api.recordConsent(any()) }
	}

	@Test
	fun `record returns null on a transport failure`() = runBlocking {
		coEvery { api.recordConsent(any()) } returns null
		assertNull(manager.trackCdpConsent(decision()))
	}

	// endregion

	// region subject

	@Test
	fun `subject - hashed normalised email when there is no master`() = runBlocking {
		coEvery { api.recordConsent(any()) } returns recorded
		manager.trackCdpConsent(decision(email = "  Foo@Bar.com "))
		val params = slot<CdpConsentRecordParams>()
		coVerify { api.recordConsent(capture(params)) }
		assertNull(params.captured.masterId)
		assertEquals("email_sha256", params.captured.idType)
		assertEquals(FOO_BAR_SHA256, params.captured.idValue)
	}

	@Test
	fun `subject - master and hashed email when both exist`() = runBlocking {
		env.masterId = UUID_A
		coEvery { api.recordConsent(any()) } returns recorded.copy(masterId = UUID_A)
		manager.trackCdpConsent(decision(email = "foo@bar.com"))
		val params = slot<CdpConsentRecordParams>()
		coVerify { api.recordConsent(capture(params)) }
		assertEquals(UUID_A, params.captured.masterId)
		assertEquals("email_sha256", params.captured.idType)
		assertEquals(FOO_BAR_SHA256, params.captured.idValue)
	}

	@Test
	fun `subject - no id_type or id_value with a master and no email`() = runBlocking {
		env.masterId = UUID_A
		coEvery { api.recordConsent(any()) } returns recorded.copy(masterId = UUID_A)
		manager.trackCdpConsent(decision())
		val params = slot<CdpConsentRecordParams>()
		coVerify { api.recordConsent(capture(params)) }
		assertNull(params.captured.idType)
		assertNull(params.captured.idValue)
	}

	@Test
	fun `subject - an empty email is treated as absent`() = runBlocking {
		coEvery { api.recordConsent(any()) } returns recorded
		manager.trackCdpConsent(decision(email = ""))
		val params = slot<CdpConsentRecordParams>()
		coVerify { api.recordConsent(capture(params)) }
		assertNull(params.captured.idType)
	}

	// endregion

	// region canonical master

	@Test
	fun `canonical master - adopts a different UUID through updateState`() = runBlocking {
		env.masterId = UUID_A
		env.cached = CdpCachedIdentity(CdpRfv(5, 1, 1, 1), listOf(3))
		env.cachedSession = env.sessionId
		coEvery { api.recordConsent(any()) } returns recorded.copy(masterId = UUID_B)

		manager.trackCdpConsent(decision())

		assertEquals(UUID_B, env.masterId)
		assertEquals(listOf<Pair<String?, String>>(UUID_A to UUID_B), env.masterIdChanges)
		// cached rfv/cohorts are kept until the next resolve refreshes them
		assertEquals(5, env.cached?.rfv?.rfv)
		assertEquals(listOf(3), env.cached?.cohorts)
	}

	@Test
	fun `canonical master - leaves the master alone when echoed, absent or not a UUID`() = runBlocking {
		env.masterId = UUID_A
		coEvery { api.recordConsent(any()) } returns recorded.copy(masterId = UUID_A)
		manager.trackCdpConsent(decision())
		coEvery { api.recordConsent(any()) } returns recorded.copy(masterId = null)
		manager.trackCdpConsent(decision())
		coEvery { api.recordConsent(any()) } returns recorded.copy(masterId = "")
		manager.trackCdpConsent(decision())
		coEvery { api.recordConsent(any()) } returns recorded.copy(masterId = "not-a-uuid")
		manager.trackCdpConsent(decision())

		assertEquals(UUID_A, env.masterId)
		assertTrue(env.masterIdChanges.isEmpty())
	}

	@Test
	fun `canonical master - never adopts when no master was sent, the decision is remembered instead`() = runBlocking {
		coEvery { api.recordConsent(any()) } returns recorded.copy(masterId = UUID_B)
		manager.trackCdpConsent(decision())
		assertNull(env.masterId)
		assertEquals("3", env.consentMemory.getRemembered(account)["privacy"]?.versionId)
	}

	@Test
	fun `canonical master - a record that started before clearIdentity never adopts the winner`() = runBlocking {
		env.masterId = UUID_A
		var release: (() -> Unit)? = null
		coEvery { api.recordConsent(any()) } coAnswers {
			kotlinx.coroutines.suspendCancellableCoroutine { cont ->
				release = { cont.resume(recorded.copy(masterId = UUID_B), null) }
			}
		}
		val record = GlobalScope.async { manager.trackCdpConsent(decision()) }
		while (release == null) delay(5)

		manager.clearIdentity()
		release!!.invoke()
		record.await()

		assertNull(env.masterId)
		assertTrue(env.masterIdChanges.isEmpty())
	}

	// endregion

	// region local memory

	@Test
	fun `memory - remembers a recorded decision when no master was sent`() = runBlocking {
		env.now = 777L
		coEvery { api.recordConsent(any()) } returns recorded
		manager.trackCdpConsent(decision(status = CdpConsentStatus.REJECTED))
		val entry = env.consentMemory.getRemembered(account)["privacy"]!!
		assertEquals("3", entry.versionId)
		assertEquals(CdpConsentStatus.REJECTED, entry.status)
		assertEquals(777L, entry.ts)
	}

	@Test
	fun `memory - does not remember when a master was the subject`() = runBlocking {
		env.masterId = UUID_A
		coEvery { api.recordConsent(any()) } returns recorded.copy(masterId = UUID_A)
		manager.trackCdpConsent(decision())
		assertTrue(env.consentMemory.getRemembered(account).isEmpty())
	}

	@Test
	fun `memory - does not remember a decision the server did not record`() = runBlocking {
		coEvery { api.recordConsent(any()) } returns recorded.copy(recorded = false)
		manager.trackCdpConsent(decision())
		assertTrue(env.consentMemory.getRemembered(account).isEmpty())
	}

	@Test
	fun `memory - a storage failure while remembering does not fail the record`() = runBlocking {
		val failing = mockk<CdpConsentMemoryStore>()
		every { failing.remember(any(), any(), any()) } throws IllegalStateException("quota")
		env = CdpTestEnv(consentMemory = failing)
		coEvery { env.api.recordConsent(any()) } returns recorded

		val result = env.manager.trackCdpConsent(decision())

		assertTrue(result!!.recorded)
	}

	// endregion

	// region catalog

	private fun catalogItem(showPolicy: String? = "if-not-accepted", acceptMethod: String = "check-box") =
		CdpConsentCatalogItem(
			consentId = "privacy",
			name = "Privacy policy",
			purpose = "legal",
			mandatory = true,
			acceptMethod = acceptMethod,
			showPolicy = showPolicy,
			version = CdpConsentCatalogVersionItem("3", "v3", "2026-01-01", "Please accept", "You must accept", mapOf("k" to "v"))
		)

	@Test
	fun `catalog - queries by site and consent, version as given, omitted when absent`() = runBlocking {
		coEvery { api.fetchConsentCatalog(456L, "privacy", "3") } returns listOf(catalogItem())
		coEvery { api.fetchConsentCatalog(456L, "privacy", null) } returns listOf(catalogItem())

		manager.getCdpConsent(CdpConsentRef("privacy", "3"))
		manager.getCdpConsent(CdpConsentRef("privacy"))

		coVerify(exactly = 1) { api.fetchConsentCatalog(456L, "privacy", "3") }
		coVerify(exactly = 1) { api.fetchConsentCatalog(456L, "privacy", null) }
	}

	@Test
	fun `catalog - maps the item to the definition`() = runBlocking {
		coEvery { api.fetchConsentCatalog(any(), any(), any()) } returns listOf(catalogItem())

		val definition = manager.getCdpConsent(CdpConsentRef("privacy", "3"))!!

		assertEquals("privacy", definition.consentId)
		assertEquals("Privacy policy", definition.name)
		assertEquals("legal", definition.purpose)
		assertTrue(definition.mandatory)
		assertEquals("check-box", definition.acceptMethod)
		assertEquals(CdpConsentShowPolicy.IF_NOT_ACCEPTED, definition.showPolicy)
		assertEquals("3", definition.version?.versionId)
		assertEquals("v3", definition.version?.label)
		assertEquals("2026-01-01", definition.version?.date)
		assertEquals("Please accept", definition.version?.displayPrompt)
		assertEquals("You must accept", definition.version?.errorMessage)
		assertEquals(mapOf("k" to "v"), definition.version?.metadata)
	}

	@Test
	fun `catalog - accept_method is carried through verbatim for each value`() = runBlocking {
		for (method in listOf("check-box", "pre-checked", "form-submit", "something-new")) {
			coEvery { api.fetchConsentCatalog(any(), any(), any()) } returns listOf(catalogItem(acceptMethod = method))
			assertEquals(method, manager.getCdpConsent(CdpConsentRef("privacy"))!!.acceptMethod)
		}
	}

	@Test
	fun `catalog - show_policy folds everything but the exact if-not-accepted to always`() = runBlocking {
		val cases = mapOf(
			"always" to CdpConsentShowPolicy.ALWAYS,
			"if-not-accepted" to CdpConsentShowPolicy.IF_NOT_ACCEPTED,
			null to CdpConsentShowPolicy.ALWAYS,
			"never" to CdpConsentShowPolicy.ALWAYS,
			"If-Not-Accepted" to CdpConsentShowPolicy.ALWAYS
		)
		for ((wire, expected) in cases) {
			coEvery { api.fetchConsentCatalog(any(), any(), any()) } returns listOf(catalogItem(showPolicy = wire))
			assertEquals("show_policy=$wire", expected, manager.getCdpConsent(CdpConsentRef("privacy"))!!.showPolicy)
		}
	}

	@Test
	fun `catalog - version is null when the consent has none`() = runBlocking {
		coEvery { api.fetchConsentCatalog(any(), any(), any()) } returns listOf(catalogItem().copy(version = null))
		assertNull(manager.getCdpConsent(CdpConsentRef("privacy"))!!.version)
	}

	@Test
	fun `catalog - null on an empty list, a transport error, a missing consentId or when disabled`() = runBlocking {
		coEvery { api.fetchConsentCatalog(any(), any(), any()) } returns emptyList()
		assertNull(manager.getCdpConsent(CdpConsentRef("privacy", "99")))

		coEvery { api.fetchConsentCatalog(any(), any(), any()) } returns null
		assertNull(manager.getCdpConsent(CdpConsentRef("privacy")))

		assertNull(manager.getCdpConsent(CdpConsentRef("")))

		env.enabled = false
		coEvery { api.fetchConsentCatalog(any(), any(), any()) } returns listOf(catalogItem())
		assertNull(manager.getCdpConsent(CdpConsentRef("privacy")))
		coVerify(exactly = 2) { api.fetchConsentCatalog(any(), any(), any()) }
	}

	@Test
	fun `catalog - is not gated on CMP consent`() = runBlocking {
		env.consent = false
		coEvery { api.fetchConsentCatalog(any(), any(), any()) } returns listOf(catalogItem())
		assertEquals("privacy", manager.getCdpConsent(CdpConsentRef("privacy"))!!.consentId)
	}

	// endregion

	// region check

	private fun checkResponse(
		granted: Boolean = true,
		answered: Boolean = true,
		status: String? = "accepted",
		versionId: String? = "3",
		answeredVersionId: String? = "3"
	) = CdpConsentCheckResponse(UUID_A, "privacy", versionId, granted, answered, status, answeredVersionId)

	@Test
	fun `check - sends only master_id when the device has one and no email is given`() = runBlocking {
		env.masterId = UUID_A
		coEvery { api.fetchConsentStatus(any()) } returns checkResponse()
		manager.consentCheck(CdpConsentQuery("privacy", "3"))
		val params = slot<CdpConsentCheckParams>()
		coVerify { api.fetchConsentStatus(capture(params)) }
		assertEquals(UUID_A, params.captured.masterId)
		assertEquals("3", params.captured.consentVersionId)
		assertNull(params.captured.idType)
		assertNull(params.captured.idValue)
	}

	@Test
	fun `check - sends the hashed normalised email when there is no master`() = runBlocking {
		coEvery { api.fetchConsentStatus(any()) } returns checkResponse()
		manager.consentCheck(CdpConsentQuery("privacy", email = " FOO@bar.com"))
		val params = slot<CdpConsentCheckParams>()
		coVerify { api.fetchConsentStatus(capture(params)) }
		assertNull(params.captured.masterId)
		assertNull(params.captured.consentVersionId)
		assertEquals("email_sha256", params.captured.idType)
		assertEquals(FOO_BAR_SHA256, params.captured.idValue)
	}

	@Test
	fun `check - sends the hashed email alongside the master when both exist`() = runBlocking {
		env.masterId = UUID_A
		coEvery { api.fetchConsentStatus(any()) } returns checkResponse()
		manager.consentCheck(CdpConsentQuery("privacy", email = "foo@bar.com"))
		val params = slot<CdpConsentCheckParams>()
		coVerify { api.fetchConsentStatus(capture(params)) }
		assertEquals(UUID_A, params.captured.masterId)
		assertEquals(FOO_BAR_SHA256, params.captured.idValue)
	}

	@Test
	fun `check - with neither, answers from local memory and never calls the network`() = runBlocking {
		env.consentMemory.remember(account, "privacy", CdpRememberedConsentDecision("3", CdpConsentStatus.ACCEPTED, 1L))
		val status = manager.consentCheck(CdpConsentQuery("privacy", "3"))!!
		assertTrue(status.granted)
		assertNull(status.masterId)
		coVerify(exactly = 0) { api.fetchConsentStatus(any()) }
		coVerify(exactly = 0) { api.fetchConsentCatalog(any(), any(), any()) }
	}

	@Test
	fun `check - maps the server answer`() = runBlocking {
		env.masterId = UUID_A
		coEvery { api.fetchConsentStatus(any()) } returns checkResponse()
		val status = manager.consentCheck(CdpConsentQuery("privacy", "3"))!!
		assertEquals(UUID_A, status.masterId)
		assertEquals("privacy", status.consentId)
		assertEquals("3", status.versionId)
		assertTrue(status.granted)
		assertTrue(status.answered)
		assertEquals(CdpConsentStatus.ACCEPTED, status.status)
		assertEquals("3", status.answeredVersionId)
	}

	@Test
	fun `check - leaves status and answeredVersionId out when unanswered`() = runBlocking {
		env.masterId = UUID_A
		coEvery { api.fetchConsentStatus(any()) } returns checkResponse(granted = false, answered = false, status = null, answeredVersionId = null)
		val status = manager.consentCheck(CdpConsentQuery("privacy", "3"))!!
		assertFalse(status.granted)
		assertFalse(status.answered)
		assertNull(status.status)
		assertNull(status.answeredVersionId)
	}

	@Test
	fun `check - a rejection on an older version is answered but not granted`() = runBlocking {
		env.masterId = UUID_A
		coEvery { api.fetchConsentStatus(any()) } returns checkResponse(granted = false, answered = true, status = "reject", answeredVersionId = "2")
		val status = manager.consentCheck(CdpConsentQuery("privacy", "3"))!!
		assertFalse(status.granted)
		assertTrue(status.answered)
		assertEquals(CdpConsentStatus.REJECTED, status.status)
		assertEquals("2", status.answeredVersionId)
	}

	@Test
	fun `check - null on a transport error, missing consentId or disabled, and not CMP gated`() = runBlocking {
		env.masterId = UUID_A
		env.consent = false
		coEvery { api.fetchConsentStatus(any()) } returns null
		assertNull(manager.consentCheck(CdpConsentQuery("privacy")))
		coVerify(exactly = 1) { api.fetchConsentStatus(any()) }

		assertNull(manager.consentCheck(CdpConsentQuery("")))

		env.enabled = false
		assertNull(manager.consentCheck(CdpConsentQuery("privacy")))
		coVerify(exactly = 1) { api.fetchConsentStatus(any()) }
	}

	// endregion

	// region local memory answers

	@Test
	fun `memory answer - unanswered when nothing remembered, echoing the requested version or empty`() = runBlocking {
		val withVersion = manager.consentCheck(CdpConsentQuery("privacy", "3"))!!
		assertFalse(withVersion.answered)
		assertFalse(withVersion.granted)
		assertEquals("3", withVersion.versionId)

		val withoutVersion = manager.consentCheck(CdpConsentQuery("privacy"))!!
		assertEquals("", withoutVersion.versionId)
	}

	@Test
	fun `memory answer - granted only on an exact version match`() = runBlocking {
		env.consentMemory.remember(account, "privacy", CdpRememberedConsentDecision("3", CdpConsentStatus.ACCEPTED, 1L))

		val same = manager.consentCheck(CdpConsentQuery("privacy", "3"))!!
		assertTrue(same.granted)
		assertTrue(same.answered)
		assertEquals(CdpConsentStatus.ACCEPTED, same.status)
		assertEquals("3", same.answeredVersionId)

		val other = manager.consentCheck(CdpConsentQuery("privacy", "4"))!!
		assertFalse(other.granted)
		assertTrue(other.answered)
		assertEquals("4", other.versionId)
		assertEquals("3", other.answeredVersionId)
	}

	@Test
	fun `memory answer - a rejection is answered but not granted`() = runBlocking {
		env.consentMemory.remember(account, "privacy", CdpRememberedConsentDecision("3", CdpConsentStatus.REJECTED, 1L))
		val status = manager.consentCheck(CdpConsentQuery("privacy", "3"))!!
		assertTrue(status.answered)
		assertFalse(status.granted)
		assertEquals(CdpConsentStatus.REJECTED, status.status)

		assertFalse(manager.consentCheck(CdpConsentQuery("privacy"))!!.granted)
	}

	@Test
	fun `memory answer - without a requested version any accept grants and echoes its version`() = runBlocking {
		env.consentMemory.remember(account, "privacy", CdpRememberedConsentDecision("2", CdpConsentStatus.ACCEPTED, 1L))
		val status = manager.consentCheck(CdpConsentQuery("privacy"))!!
		assertTrue(status.granted)
		assertEquals("2", status.versionId)
		assertEquals("2", status.answeredVersionId)
	}

	@Test
	fun `memory answer - a storage failure reads as unanswered`() = runBlocking {
		val failing = mockk<CdpConsentMemoryStore>()
		every { failing.getRemembered(any()) } throws IllegalStateException("corrupt")
		env = CdpTestEnv(consentMemory = failing)
		val status = env.manager.consentCheck(CdpConsentQuery("privacy", "3"))!!
		assertFalse(status.answered)
		assertFalse(status.granted)
	}

	// endregion

	// region hasCdpConsent

	@Test
	fun `hasConsent - true only when granted, false on not granted, transport error and disabled`() = runBlocking {
		env.masterId = UUID_A
		coEvery { api.fetchConsentStatus(any()) } returns checkResponse(granted = true)
		assertTrue(manager.hasCdpConsent(CdpConsentQuery("privacy", "3")))

		coEvery { api.fetchConsentStatus(any()) } returns checkResponse(granted = false)
		assertFalse(manager.hasCdpConsent(CdpConsentQuery("privacy", "3")))

		coEvery { api.fetchConsentStatus(any()) } returns null
		assertFalse(manager.hasCdpConsent(CdpConsentQuery("privacy", "3")))

		env.enabled = false
		assertFalse(manager.hasCdpConsent(CdpConsentQuery("privacy", "3")))
		coVerify(exactly = 3) { api.fetchConsentStatus(any()) }
	}

	@Test
	fun `hasConsent - answers from memory for an anonymous visitor`() = runBlocking {
		env.consentMemory.remember(account, "privacy", CdpRememberedConsentDecision("3", CdpConsentStatus.ACCEPTED, 1L))
		assertTrue(manager.hasCdpConsent(CdpConsentQuery("privacy", "3")))
		assertFalse(manager.hasCdpConsent(CdpConsentQuery("newsletter")))
		coVerify(exactly = 0) { api.fetchConsentStatus(any()) }
	}

	// endregion

	// region replay

	@Test
	fun `replay - re-records each remembered decision once under the master and nothing else`() = runBlocking {
		env.masterId = UUID_A
		env.consentMemory.remember(account, "privacy", CdpRememberedConsentDecision("3", CdpConsentStatus.ACCEPTED, 1L))
		env.consentMemory.remember(account, "newsletter", CdpRememberedConsentDecision("1", CdpConsentStatus.REJECTED, 2L))
		val sent = mutableListOf<CdpConsentRecordParams>()
		coEvery { api.recordConsent(capture(sent)) } returns recorded

		manager.replayConsentDecisions()

		assertEquals(2, sent.size)
		val privacy = sent.first { it.consentId == "privacy" }
		assertEquals(UUID_A, privacy.masterId)
		assertEquals("3", privacy.consentVersionId)
		assertEquals(CdpConsentStatus.ACCEPTED, privacy.status)
		assertNull(privacy.metadata)
		assertNull(privacy.timezone)
		assertNull(privacy.idType)
		assertEquals(CdpConsentStatus.REJECTED, sent.first { it.consentId == "newsletter" }.status)
		assertTrue(env.consentMemory.getRemembered(account).isEmpty())
	}

	@Test
	fun `replay - forgets only the decisions the server recorded`() = runBlocking {
		env.masterId = UUID_A
		env.consentMemory.remember(account, "privacy", CdpRememberedConsentDecision("3", CdpConsentStatus.ACCEPTED, 1L))
		env.consentMemory.remember(account, "newsletter", CdpRememberedConsentDecision("1", CdpConsentStatus.ACCEPTED, 2L))
		coEvery { api.recordConsent(match { it.consentId == "privacy" }) } returns recorded
		coEvery { api.recordConsent(match { it.consentId == "newsletter" }) } returns recorded.copy(recorded = false)

		manager.replayConsentDecisions()

		assertEquals(setOf("newsletter"), env.consentMemory.getRemembered(account).keys)
	}

	@Test
	fun `replay - is a no-op with nothing remembered, without a master, or when disabled`() = runBlocking {
		env.masterId = UUID_A
		manager.replayConsentDecisions()

		env.consentMemory.remember(account, "privacy", CdpRememberedConsentDecision("3", CdpConsentStatus.ACCEPTED, 1L))
		env.masterId = null
		manager.replayConsentDecisions()

		env.masterId = UUID_A
		env.enabled = false
		manager.replayConsentDecisions()

		coVerify(exactly = 0) { api.recordConsent(any()) }
		assertEquals(setOf("privacy"), env.consentMemory.getRemembered(account).keys)
	}

	@Test
	fun `replay - does not double-fire while in flight and re-arms once it settles`() = runBlocking {
		env.masterId = UUID_A
		env.consentMemory.remember(account, "privacy", CdpRememberedConsentDecision("3", CdpConsentStatus.ACCEPTED, 1L))
		val gate = CompletableDeferred<CdpConsentRecordResponse>()
		coEvery { api.recordConsent(any()) } coAnswers { gate.await() }

		val first = GlobalScope.async { manager.replayConsentDecisions() }
		val second = GlobalScope.async { manager.replayConsentDecisions() }
		delay(50)
		gate.complete(recorded.copy(recorded = false))
		first.await()
		second.await()
		coVerify(exactly = 1) { api.recordConsent(any()) }

		coEvery { api.recordConsent(any()) } returns recorded
		manager.replayConsentDecisions()
		coVerify(exactly = 2) { api.recordConsent(any()) }
	}

	@Test
	fun `replay - storage failures neither throw nor POST`() = runBlocking {
		val failing = mockk<CdpConsentMemoryStore>()
		every { failing.getRemembered(any()) } throws IllegalStateException("corrupt")
		env = CdpTestEnv(consentMemory = failing)
		env.masterId = UUID_A

		env.manager.replayConsentDecisions()

		coVerify(exactly = 0) { env.api.recordConsent(any()) }
	}

	// endregion
}
