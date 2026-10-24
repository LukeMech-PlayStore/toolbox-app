package org.lukemech.lmtoolbox.linux

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent / advanceUntilIdle
class EnvLockTest {
    private val lock = EnvLock()

    @Test
    fun `commands run in parallel`() = runTest {
        var running = 0
        var maxRunning = 0
        repeat(3) {
            launch {
                lock.read {
                    maxRunning = maxOf(maxRunning, ++running)
                    delay(100.milliseconds)
                    running--
                }
            }
        }
        advanceUntilIdle()
        assertEquals(3, maxRunning)
    }

    @Test
    fun `install waits for running commands`() = runTest {
        val release = CompletableDeferred<Unit>()
        launch { lock.read { release.await() } }
        runCurrent()

        var installed = false
        launch { lock.write { installed = true } }
        runCurrent()
        assertFalse("install must wait for the command", installed)

        release.complete(Unit)
        advanceUntilIdle()
        assertTrue(installed)
    }

    @Test
    fun `commands wait for a running install`() = runTest {
        val release = CompletableDeferred<Unit>()
        launch { lock.write { release.await() } }
        runCurrent()

        var ran = false
        launch { lock.read { ran = true } }
        runCurrent()
        assertFalse("command must wait for the install", ran)

        release.complete(Unit)
        advanceUntilIdle()
        assertTrue(ran)
    }

    @Test
    fun `command cancelled while waiting keeps later commands out of installs`() = runTest {
        val release = CompletableDeferred<Unit>()
        launch { lock.write { release.await() } }
        runCurrent()

        // Waits behind the installation, then gets canceled (e.g. the user cancels the task)
        val waiting = launch { lock.read { } }
        runCurrent()
        waiting.cancel()
        release.complete(Unit)
        advanceUntilIdle()

        // Regression: the canceled command used to stay counted as a reader, so every later
        // command skipped the lock and could run in the middle of an installation
        val release2 = CompletableDeferred<Unit>()
        launch { lock.write { release2.await() } }
        runCurrent()
        var ran = false
        launch { lock.read { ran = true } }
        runCurrent()
        assertFalse("command must wait for the install", ran)

        release2.complete(Unit)
        advanceUntilIdle()
        assertTrue(ran)
    }

    @Test
    fun `failing command releases the lock`() = runTest {
        runCatching { lock.read { error("boom") } }
        withTimeout(1_000.milliseconds) { lock.write { } }
    }
}
