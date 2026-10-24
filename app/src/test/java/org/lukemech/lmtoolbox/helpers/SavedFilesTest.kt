package org.lukemech.lmtoolbox.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedFilesTest {
    @Test
    fun `no path or odd characters in a file's name`() {
        assertEquals("a_b_c_d", safeFileName("a/b:c*d"))
        assertEquals("bell_", safeFileName("bell\u0007"))
        assertEquals("name", safeFileName("  .name.  "))
    }

    @Test
    fun `nothing left of it, or too long`() {
        assertEquals("video", safeFileName(" .. ", "video"))
        assertEquals(150, safeFileName("x".repeat(200)).length)
    }
}
