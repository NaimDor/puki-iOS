package io.github.tomsadeh.puki.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The tablet's copy of the approved lines: it follows the PC's pack, and picks a line for a moment. */
class LinePackTest {
    @get:Rule val tmp = TemporaryFolder()

    private val cow = "במשחק החיות: הפרה מצאה את האוכל שלה."
    private var served = mutableMapOf<String, ByteArray>()

    private fun manifest(version: String, vararg lines: Pair<String, String>) =
        """{"version": "$version", "lines": [${lines.joinToString(",") { (id, text) ->
            """{"id": "$id", "situation": "$cow", "lang": "he", "gender": "girl", "text": "$text"}""" }}]}""".toByteArray()

    private fun pack(dir: File) = LinePack(dir) { path -> served[path] }.also { it.load() }

    @Test
    fun syncsLinesAndAudioThenPicksForTheMoment() {
        served["/pack"] = manifest("v1", "aaaaaaaaaaaa" to "יש! הפרה אוכלת!", "bbbbbbbbbbbb" to "מממ, טעים!")
        served["/pack/audio?id=aaaaaaaaaaaa"] = "OggS-a".toByteArray()
        served["/pack/audio?id=bbbbbbbbbbbb"] = "OggS-b".toByteArray()
        val p = pack(tmp.root)
        p.sync()
        assertEquals(2, p.size)
        val first = p.pick(cow, "he", girl = true)!!
        val second = p.pick(cow, "he", girl = true)!!
        assertNotEquals(first.first.id, second.first.id)  // not the same line twice in a row
        assertTrue(second.second.exists())
        assertNull(p.pick(cow, "en", girl = true))
        assertNull(p.pick(cow, "he", girl = false))
        assertNull(p.pick("another moment", "he", girl = true))
    }

    @Test
    fun keepsWorkingWithoutThePcAndForgetsWhatTheParentTookBack() {
        served["/pack"] = manifest("v1", "aaaaaaaaaaaa" to "יש!", "bbbbbbbbbbbb" to "מממ!")
        served["/pack/audio?id=aaaaaaaaaaaa"] = "OggS-a".toByteArray()
        served["/pack/audio?id=bbbbbbbbbbbb"] = "OggS-b".toByteArray()
        pack(tmp.root).sync()
        served.clear()  // the PC is off
        val offline = pack(tmp.root)
        assertEquals(2, offline.size)
        offline.sync()
        assertEquals(2, offline.size)
        served["/pack"] = manifest("v2", "aaaaaaaaaaaa" to "יש!")  // one line was taken back
        offline.sync()
        assertEquals(1, offline.size)
        assertFalse(File(tmp.root, "pack/bbbbbbbbbbbb.ogg").exists())
    }

    @Test
    fun aLineWhoseAudioDidntComeIsLeftOut() {
        served["/pack"] = manifest("v1", "aaaaaaaaaaaa" to "יש!", "cccccccccccc" to "אין קול")
        served["/pack/audio?id=aaaaaaaaaaaa"] = "OggS-a".toByteArray()
        val p = pack(tmp.root)
        p.sync()
        assertEquals(1, p.size)
        served["/pack/audio?id=cccccccccccc"] = "OggS-c".toByteArray()
        p.sync()  // tried again, since the last sync wasn't complete
        assertEquals(2, p.size)
    }

    @Test
    fun malformedIdsAreIgnored() {
        served["/pack"] = manifest("v1", "../../evil" to "x")
        val p = pack(tmp.root)
        p.sync()
        assertEquals(0, p.size)
    }
}
