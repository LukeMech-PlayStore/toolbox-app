package org.lukemech.lmtoolbox.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingFilterTest {
    private val filter = ReadingFilter(tolerance = 5f, minIntervalMs = 100)

    @Test
    fun `takes the first reading`() {
        assertTrue(filter.accept(10f, now = 0))
    }

    @Test
    fun `holds back changes within the tolerance`() {
        filter.accept(10f, now = 0)
        assertFalse(filter.accept(14f, now = 500))
        assertTrue(filter.accept(16f, now = 500))
    }

    @Test
    fun `holds back readings too soon after the last one`() {
        filter.accept(10f, now = 0)
        assertFalse(filter.accept(50f, now = 50))
        assertTrue(filter.accept(50f, now = 100))
    }

    @Test
    fun `compares to the last reading taken, so slow drift still gets through`() {
        filter.accept(0f, now = 0)
        assertFalse(filter.accept(3f, now = 200))
        assertTrue(filter.accept(6f, now = 400))
    }

    @Test
    fun `uses the given delta, e g the short way around for angles`() {
        val angles = ReadingFilter(5f, 100) { from, to -> ((to - from) % 360f + 540f) % 360f - 180f }
        angles.accept(358f, now = 0)
        assertFalse(angles.accept(2f, now = 200))
    }
}
