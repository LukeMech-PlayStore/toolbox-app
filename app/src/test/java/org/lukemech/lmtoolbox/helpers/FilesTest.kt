package org.lukemech.lmtoolbox.helpers

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesTest {
    @Test
    fun deletesATreeButNotWhatLinksPointAt() {
        val root = Files.createTempDirectory("tree").toFile()
        val outside = Files.createTempDirectory("outside").toFile().apply { File(this, "keep").writeText("x") }
        File(root, "a/b").mkdirs()
        File(root, "a/b/c.txt").writeText("c")
        File(root, "d.txt").writeText("d")
        runCatching { Files.createSymbolicLink(File(root, "a/link").toPath(), outside.toPath()) }
        assertTrue(root.deleteTree())
        assertFalse(root.exists())
        assertTrue(File(outside, "keep").exists())
        outside.deleteTree()
    }

    @Test
    fun whatsGoneAlreadyIsFine() {
        val gone = File(Files.createTempDirectory("gone").toFile().apply { delete() }, "nothing")
        assertTrue(gone.deleteTree())
    }
}
