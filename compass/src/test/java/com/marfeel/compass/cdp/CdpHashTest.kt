package com.marfeel.compass.cdp

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class CdpHashTest {
	private val fooBar = "0c7e6a405862e402eb76a70f8a26fc732d07c32931e9fae9ab1582911d2e8a3b"
	private val phone = "cb24629d1dbeb6ee24e7c20610896274e8102e67aa6efc2f3a1be2893c38008b"

	@Test
	fun `normalizeEmail trims and lower-cases`() {
		assertEquals("foo@bar.com", CdpHash.normalizeEmail("  Foo@BAR.com "))
	}

	@Test
	fun `normalizePhone trims but leaves case alone`() {
		assertEquals("+34600111222", CdpHash.normalizePhone(" +34600111222 "))
		assertEquals("ABC", CdpHash.normalizePhone("ABC"))
	}

	@Test
	fun `hashEmail renders the normalised email as 64 lowercase hex chars with the known digest`() {
		val digest = CdpHash.hashEmail(" Foo@Bar.com ")
		assertEquals(fooBar, digest)
		assertEquals(64, digest.length)
		assertTrue(digest.matches(Regex("^[0-9a-f]{64}$")))
	}

	@Test
	fun `an already-normalised email hashes to the same digest`() {
		assertEquals(CdpHash.hashEmail("foo@bar.com"), CdpHash.hashEmail("FOO@BAR.COM"))
	}

	@Test
	fun `hashPhone renders the trimmed phone with the known digest`() {
		assertEquals(phone, CdpHash.hashPhone(" +34600111222"))
	}

	@Test
	fun `a differently formatted phone is a different digest`() {
		assertFalse(CdpHash.hashPhone("+34600111222") == CdpHash.hashPhone("600111222"))
	}

	@Test
	fun `consentEmailIdentity hashes under email_sha256`() {
		val subject = consentEmailIdentity(" Foo@Bar.com ")
		assertEquals(CdpIdentityTypes.EMAIL_SHA256, subject.idType)
		assertEquals(fooBar, subject.idValue)
	}

	@Test
	fun `identity types offer the stable and device-bound split and no phantom types`() {
		assertTrue(CdpIdentityTypes.STABLE.contains(CdpIdentityTypes.EMAIL_SHA256))
		assertTrue(CdpIdentityTypes.STABLE.contains(CdpIdentityTypes.REGISTERED_USER_ID))
		assertTrue(CdpIdentityTypes.DEVICE_BOUND.contains(CdpIdentityTypes.CRM_ID))
		assertTrue(CdpIdentityTypes.DEVICE_BOUND.contains(CdpIdentityTypes.PUSH_TOKEN))
		assertEquals(16, CdpIdentityTypes.STABLE.size + CdpIdentityTypes.DEVICE_BOUND.size)
		val all = CdpIdentityTypes.STABLE + CdpIdentityTypes.DEVICE_BOUND
		assertFalse(all.contains("email_hash"))
		assertFalse(all.contains("phone_hash"))
	}

	@Test
	fun `the public Cdp surface delegates the hash helpers`() {
		val cdp: Cdp = CdpTracker
		assertEquals(fooBar, cdp.hashEmail("Foo@Bar.com"))
		assertEquals(phone, cdp.hashPhone("+34600111222"))
		assertEquals("foo@bar.com", cdp.normalizeEmail(" Foo@Bar.com"))
		assertEquals("+1", cdp.normalizePhone(" +1 "))
		assertEquals(CdpIdentityTypes, cdp.identityTypes)
	}
}
