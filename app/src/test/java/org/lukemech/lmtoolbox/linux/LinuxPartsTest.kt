package org.lukemech.lmtoolbox.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxPartsTest {
    // As the subsystem's release writes it (rootfs/release.py)
    private val json = """
        {
          "release": "26.10.21",
          "parts": {
            "core": {
              "version": "26.10.14", "hash": "a", "requires": [], "lib": "libpackage-core.tar.xz.so",
              "abis": {
                "arm64-v8a": { "size": 5000, "archive": 2000, "components": ["dash 0.5.12"] },
                "x86_64": { "size": 5100, "archive": 2100, "components": ["dash 0.5.12"] }
              }
            },
            "python": {
              "version": "26.10.14", "hash": "b", "requires": [], "lib": "libpackage-python.tar.xz.so",
              "abis": { "arm64-v8a": { "size": 60000, "archive": 15000, "components": ["python 3.13.7"] } }
            },
            "qrcode": {
              "version": "26.10.21", "hash": "c", "requires": ["python"], "lib": "libpackage-qrcode.tar.xz.so",
              "abis": { "arm64-v8a": { "size": 3000, "archive": 1000, "components": ["qrcode 8.0"] } }
            }
          }
        }
    """.trimIndent()

    @Test
    fun `reads each part's version and sizes for the device's ABI`() {
        val parts = LinuxParts.parseParts(json, listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals(listOf("core", "python", "qrcode"), parts.keys.toList())
        assertEquals(
            LinuxExtension("qrcode", listOf("python"), 3000, listOf("qrcode 8.0"), "26.10.21", 1000),
            parts["qrcode"]
        )
        assertEquals("26.10.14", parts["core"]?.version)
    }

    @Test
    fun `takes the first ABI the device supports that the part has`() {
        val parts = LinuxParts.parseParts(json, listOf("x86_64", "arm64-v8a"))
        assertEquals(5100L, parts["core"]?.size)
        // Only built for arm64-v8a: that one
        assertEquals(60000L, parts["python"]?.size)
    }

    @Test
    fun `leaves out a part built for none of the device's ABIs`() {
        val parts = LinuxParts.parseParts(json, listOf("x86_64"))
        assertEquals(listOf("core"), parts.keys.toList())
    }

    @Test
    fun `reads nothing from something else`() {
        assertTrue(LinuxParts.parseParts("{}", listOf("arm64-v8a")).isEmpty())
        assertTrue(LinuxParts.parseParts("not json", listOf("arm64-v8a")).isEmpty())
    }
}
