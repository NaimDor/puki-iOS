package io.github.tomsadeh.puki.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.math.sin
import kotlin.math.sqrt

class KolEarsTest {
    private fun tone(amp: Float, n: Int = 16000) = FloatArray(n) { amp * sin(it * 0.1f) }
    private fun rms(x: FloatArray) = sqrt(x.sumOf { (it * it).toDouble() } / x.size).toFloat()

    @Test
    fun quietSpeechIsRaisedTowardsTheTrainingLevel() {
        val out = KolEars.louder(tone(0.02f))
        assertEquals(0.1f, rms(out), 0.01f)
    }

    @Test
    fun loudAudioAndSilenceAreLeftAlone() {
        val loud = tone(0.5f)
        assertSame(loud, KolEars.louder(loud))
        val silence = FloatArray(16000)
        assertSame(silence, KolEars.louder(silence))
    }

    @Test
    fun theBoostStopsAtTenTimes() {
        val out = KolEars.louder(tone(0.001f))
        assertEquals(10 * rms(tone(0.001f)), rms(out), 1e-4f)
    }
}
