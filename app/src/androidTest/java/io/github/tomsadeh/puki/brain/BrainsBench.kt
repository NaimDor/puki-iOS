package io.github.tomsadeh.puki.brain

import ai.onnxruntime.OrtEnvironment
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tomsadeh.puki.brain.voice.Beak
import io.github.tomsadeh.puki.brain.voice.BlueVoice
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * Puki's own models on a real device: how fast Brains writes a line, how Beak reads it, and the whole
 * way from a moment to Puki's voice (with Squawk, SquawkBench's files). Results go to logcat (PukiBench).
 *     adb push brains_int8.onnx tokenizer.json brains.json /data/local/tmp/brains/
 *     adb push beak_int8.onnx vocab.json /data/local/tmp/beak/
 * then: gradlew connectedGithubDebugAndroidTest -Ppuki.bench
 *     -Pandroid.testInstrumentationRunnerArguments.class=io.github.tomsadeh.puki.brain.BrainsBench
 */
@RunWith(AndroidJUnit4::class)
class BrainsBench {
    private val brainsDir = File("/data/local/tmp/brains")
    private val beakDir = File("/data/local/tmp/beak")
    private val voiceDir = File("/data/local/tmp/voice")

    private val inputs = listOf(
        "moment" to "במשחק הספירה: הארנב רעב ורוצה 3 גזרים, ונותנים לו אחד אחד.",
        "moment" to "בגן החיות: האכילו את הפרה בעשב.",
        "tap" to "לחצו על התוכי כדי שידבר. בהיום שלי: אופים עוגה במטבח. תגיד משהו חמוד וקצר על זה.",
        "talk" to "פוקי, אני אוהבת כלבים",
        "recap" to "סיכום לפני השינה. היום: האכלת את הפרה; מצאת את התרנגולת שהתחבאה; קיבלת מדבקה של פרפר.",
    )

    @Test
    fun brains() {
        assumeTrue(File(brainsDir, "brains_int8.onnx").canRead())
        val env = OrtEnvironment.getEnvironment()
        val beak = if (File(beakDir, "beak_int8.onnx").canRead()) Beak(env, beakDir) else null
        val voice = if (File(voiceDir, "vector_estimator.onnx").canRead()) BlueVoice(env, voiceDir, File(voiceDir, "noa.json"), null, 4) else null
        for (threads in listOf(4, 2)) {
            val t0 = System.nanoTime()
            val brains = Brains(env, brainsDir, threads)
            Log.i(TAG, "brains threads $threads: load ${(System.nanoTime() - t0) / 1_000_000} ms")
            val random = Random(1)
            for ((task, input) in inputs) {
                brains.line(task, "he", true, input, random = random)  // warm
                val p = System.nanoTime()
                brains.gate(input, "he", true)  // (one step: about the prompt's cost alone)
                val promptMs = (System.nanoTime() - p) / 1_000_000
                Log.i(TAG, "  prompt alone about $promptMs ms")
                val s = System.nanoTime()
                val line = brains.line(task, "he", true, input, random = random) ?: ""
                val ms = (System.nanoTime() - s) / 1_000_000
                Log.i(TAG, "brains threads $threads [$task] $ms ms | $line")
                if (threads == 4 && beak != null) {
                    val b = System.nanoTime()
                    val ipa = beak.phonemize(line, girl = true)
                    val beakMs = (System.nanoTime() - b) / 1_000_000
                    var voiceMs = -1L
                    var seconds = 0f
                    if (voice != null) {
                        val v = System.nanoTime()
                        val samples = voice.voice(ipa)
                        voiceMs = (System.nanoTime() - v) / 1_000_000
                        seconds = (samples?.size ?: 0) / voice.sampleRate.toFloat()
                    }
                    Log.i(TAG, "  beak $beakMs ms | $ipa | voice $voiceMs ms for ${"%.1f".format(seconds)} s | all ${ms + beakMs + voiceMs} ms")
                }
            }
            for (heard in listOf("אני רוצה עוד לשחק", "נפלתי וזה כואב", "בוא נשחק עם גפרורים")) {
                val s = System.nanoTime()
                val probs = brains.gate(heard, "he", true)
                Log.i(TAG, "gate threads $threads ${(System.nanoTime() - s) / 1_000_000} ms | $heard -> ${Safety.gateCategory(probs, Safety.THRESHOLD)} ${probs.mapValues { "%.2f".format(it.value) }}")
            }
            brains.close()
        }
    }

    /** What Beak reads for a few known lines, to compare with Python (train/read_train.py) by eye. */
    @Test
    fun beakLines() {
        assumeTrue(File(beakDir, "beak_int8.onnx").canRead())
        val beak = Beak(OrtEnvironment.getEnvironment(), beakDir)
        for ((line, girl) in listOf("הדובי רוצה אוכל." to true, "ציירת ציור יפה!" to true, "ציירת ציור יפה!" to false, "הכבשה והקוף רעבים." to false))
            Log.i(TAG, "beak ${if (girl) "girl" else "boy"} | $line | ${beak.phonemize(line, girl)}")
    }

    private companion object { const val TAG = "PukiBench" }
}
