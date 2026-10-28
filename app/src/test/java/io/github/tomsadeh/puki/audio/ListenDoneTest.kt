package io.github.tomsadeh.puki.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenDoneTest {
    private fun done(t: Long, lastLoud: Long, max: Long = 4000) = Companion.listenDone(t, lastLoud, max)

    @Test fun `waits for her to start, however long it takes`() {
        assertFalse(done(500, -1))
        assertFalse(done(3900, -1))
        assertTrue(done(4000, -1))  // ...up to the most it listens
    }

    @Test fun `stops once she has spoken and gone quiet`() {
        assertFalse(done(1200, 900))   // still within the pause
        assertTrue(done(1700, 900))    // 0.8 s quiet after the last loud moment
    }

    @Test fun `a pause mid-phrase doesn't cut her off`() {
        assertFalse(done(1500, 1000))  // half a second between words
    }
}
