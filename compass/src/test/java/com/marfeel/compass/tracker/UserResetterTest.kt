package com.marfeel.compass.tracker

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * The reset algorithm, with the two halves stubbed: the local rotation is a counter
 * that mints a new "user id", the remote tail is whatever each test needs it to be.
 */
class UserResetterTest {
	private var userId = UUID.randomUUID().toString()
	private val rotations = AtomicInteger(0)
	private val remoteCalls = AtomicInteger(0)
	private val userIdSeenByRemote = mutableListOf<String>()

	private fun rotate() {
		rotations.incrementAndGet()
		userId = UUID.randomUUID().toString()
	}

	private fun resetter(
		remote: suspend () -> Unit = { remoteCalls.incrementAndGet(); userIdSeenByRemote.add(userId) },
		timeoutMs: Long = 5_000L
	) = UserResetter(rotateLocalUser = ::rotate, clearRemoteState = remote, remoteTimeoutMs = timeoutMs)

	@Test
	fun `rotates synchronously, before the first suspension`() {
		val before = userId
		val resetter = resetter()

		resetter.start() // not awaited

		assertFalse(before == userId)
		assertEquals(1, rotations.get())
	}

	@Test
	fun `rotates before asking the server`() = runBlocking {
		val before = userId
		resetter().reset()
		assertEquals(1, remoteCalls.get())
		assertFalse(before == userIdSeenByRemote.single())
		assertEquals(userId, userIdSeenByRemote.single())
	}

	@Test
	fun `concurrent resets share one run`() = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val resetter = resetter(remote = { remoteCalls.incrementAndGet(); gate.await() })

		val first = resetter.start()
		val second = resetter.start()
		assertTrue(first === second)
		gate.complete(Unit)
		first.await()

		assertEquals(1, rotations.get())
		assertEquals(1, remoteCalls.get())
	}

	@Test
	fun `resets again once the first run settled`() = runBlocking {
		val resetter = resetter()
		resetter.reset()
		resetter.reset()
		assertEquals(2, rotations.get())
		assertEquals(2, remoteCalls.get())
	}

	@Test
	fun `resolves even when the remote cleanup never settles`() = runBlocking {
		val resetter = resetter(remote = { CompletableDeferred<Unit>().await() }, timeoutMs = 100L)
		withTimeout(2_000L) { resetter.reset() }
		assertEquals(1, rotations.get())
	}

	@Test
	fun `resolves when the remote cleanup throws`() = runBlocking {
		val resetter = resetter(remote = { throw IllegalStateException("network down") })
		resetter.reset()
		assertEquals(1, rotations.get())
	}

	@Test
	fun `resolves when the local rotation throws and still runs the remote tail`() = runBlocking {
		val resetter = UserResetter(
			rotateLocalUser = { throw IllegalStateException("storage") },
			clearRemoteState = { remoteCalls.incrementAndGet() }
		)
		resetter.reset()
		assertEquals(1, remoteCalls.get())
	}

	@Test
	fun `a second caller awaits the in-flight run instead of resolving early`() = runBlocking {
		val gate = CompletableDeferred<Unit>()
		val resetter = resetter(remote = { gate.await() })
		val first = GlobalScope.async { resetter.reset() }
		delay(50)
		val second = GlobalScope.async { resetter.reset() }
		delay(50)
		assertFalse(second.isCompleted)
		gate.complete(Unit)
		first.await()
		second.await()
		assertEquals(1, rotations.get())
	}
}
