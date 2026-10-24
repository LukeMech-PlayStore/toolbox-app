package org.lukemech.lmtoolbox.linux

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Commands share the subsystem (read); install/update need it exclusively (write).
 * Coroutine-friendly: unlike java.util.concurrent locks it isn't tied to a thread.
 */
internal class EnvLock {
    private val writer = Mutex()
    private val readersMutex = Mutex()
    private var readers = 0

    suspend fun <T> read(block: suspend () -> T): T {
        // Count only once the lock is held, so a reader canceled while waiting leaves no trace
        readersMutex.withLock {
            if (readers == 0) writer.lock()
            readers++
        }
        try {
            return block()
        } finally {
            withContext(NonCancellable) {
                readersMutex.withLock { if (--readers == 0) writer.unlock() }
            }
        }
    }

    suspend fun <T> write(block: suspend () -> T): T = writer.withLock { block() }
}
