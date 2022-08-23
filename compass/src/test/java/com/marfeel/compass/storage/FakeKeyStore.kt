package com.marfeel.compass.storage

import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.*
import javax.crypto.KeyGenerator
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey

val keyStoreProvider = object : Provider("AndroidKeyStore", 1.0, "") {
	init {
		put("KeyStore.AndroidKeyStore", FakeKeyStore::class.java.name)
		put("KeyGenerator.AES", FakeAesKeyGenerator::class.java.name)
	}
}

class FakeKeyStore : KeyStoreSpi() {
	private val wrapped =
		KeyStore.getInstance(KeyStore.getDefaultType())

	override fun engineIsKeyEntry(alias: String?) =
		wrapped.isKeyEntry(alias)

	override fun engineIsCertificateEntry(alias: String?) =
		wrapped.isCertificateEntry(alias)

	override fun engineGetCertificateAlias(certificate: Certificate?): String =
		wrapped.getCertificateAlias(certificate)

	override fun engineStore(stream: OutputStream?, password: CharArray?) =
		wrapped.store(stream, password)

	override fun engineLoad(stream: InputStream?, password: CharArray?) =
		wrapped.load(stream, password)

	override fun engineGetKey(alias: String?, password: CharArray?) =
		wrapped.getKey(alias, password)

	override fun engineGetCertificateChain(alias: String?): Array<Certificate> =
		wrapped.getCertificateChain(alias)

	override fun engineGetCertificate(alias: String?): Certificate = wrapped.getCertificate(alias)

	override fun engineGetCreationDate(alias: String?): Date = wrapped.getCreationDate(alias)

	override fun engineSetKeyEntry(
		alias: String?,
		key: Key?,
		password: CharArray?,
		chain: Array<out Certificate>?
	) = wrapped.setKeyEntry(alias, key, password, chain)

	override fun engineSetKeyEntry(
		alias: String?,
		key: ByteArray?,
		chain: Array<out Certificate>?
	) = wrapped.setKeyEntry(alias, key, chain)

	override fun engineSetCertificateEntry(alias: String?, certificate: Certificate?) =
		wrapped.setCertificateEntry(alias, certificate)

	override fun engineDeleteEntry(alias: String?) = wrapped.deleteEntry(alias)

	override fun engineAliases(): Enumeration<String> = wrapped.aliases()

	override fun engineContainsAlias(alias: String?): Boolean = wrapped.containsAlias(alias)

	override fun engineSize(): Int = wrapped.size()
}

class FakeAesKeyGenerator : KeyGeneratorSpi() {
	private val wrapped = KeyGenerator.getInstance("AES")

	override fun engineInit(random: SecureRandom?) = Unit
	override fun engineInit(
		params: AlgorithmParameterSpec?,
		random: SecureRandom?
	) = Unit

	override fun engineInit(keysize: Int, random: SecureRandom?) =
		Unit

	override fun engineGenerateKey(): SecretKey =
		wrapped.generateKey()
}
