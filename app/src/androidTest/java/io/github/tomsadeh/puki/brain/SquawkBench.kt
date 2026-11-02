package io.github.tomsadeh.puki.brain

import ai.onnxruntime.OrtEnvironment
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tomsadeh.puki.brain.voice.BlueVoice
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Random

/**
 * How fast Puki's voice (BlueTTS with Noa) speaks on a real device, from phonemes as the reading
 * model will hand them over; results go to logcat (tag PukiBench). Push the voice first:
 *     adb push <BlueTTS2.5 onnx bundle> noa.json /data/local/tmp/voice/
 * then: gradlew connectedGithubDebugAndroidTest -Ppuki.bench
 *     -Pandroid.testInstrumentationRunnerArguments.class=io.github.tomsadeh.puki.brain.SquawkBench
 * Each setting's WAV of the middle line (same noise every time, so they compare) lands in the bench
 * app's files folder: adb pull /sdcard/Android/data/io.github.tomsadeh.puki.bench/files/squawk
 */
@RunWith(AndroidJUnit4::class)
class SquawkBench {
    // (-Pandroid.testInstrumentationRunnerArguments.voiceDir=... for another build of the voice, e.g. int8)
    private val dir = File(InstrumentationRegistry.getArguments().getString("voiceDir") ?: "/data/local/tmp/voice")
    private val lines = listOf(
        "haχazˈiʁ ʁaʔˈev.",
        "hatsˈel niʁʔˈa kmˈo pˈil ʔanˈak!",
        "haχajˈa hazˈot ʔosˈa hˈop! vehˈop! bˈoʔi navˈi lˈo ʔˈoχel, hˈu mamˈaʃ ʁaʔˈev.",
    )

    /** Flow steps and how many of them are guided: BlueTTS's 8 and 5, then fewer and less guidance. */
    @Test
    fun stepsAndGuidance() {
        assumeTrue(File(dir, "vector_estimator.onnx").canRead())
        val out = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "squawk").apply { mkdirs() }
        val voice = BlueVoice(OrtEnvironment.getEnvironment(), dir, File(dir, "noa.json"), null, 4)
        for ((steps, guided) in listOf(8 to 8, 5 to 5, 5 to 3, 5 to 2, 5 to 1, 4 to 4, 4 to 2)) {
            voice.guidedSteps = guided
            for ((i, line) in lines.withIndex()) {
                voice.noise = seeded()
                voice.voice(line, totalStep = steps)  // warm
                voice.noise = seeded()
                val s = System.nanoTime()
                val samples = voice.voice(line, totalStep = steps)!!
                val ms = (System.nanoTime() - s) / 1_000_000
                val audio = samples.size / voice.sampleRate.toFloat()
                Log.i(TAG, "steps $steps guided $guided (${"%.1f".format(audio)} s): $ms ms, ${"%.2f".format(ms / 1000f / audio)}x real time " +
                    "| duration ${voice.lastTimings[0]}, encoder ${voice.lastTimings[1]}, flow ${voice.lastTimings[2]}, vocoder ${voice.lastTimings[3]} ms")
                if (i == 1) File(out, "steps${steps}_guided$guided.wav").writeBytes(voice.wav(samples))
            }
        }
        voice.close()
    }

    /** Threads, on BlueTTS's own default of 5 guided steps. */
    @Test
    fun threads() {
        assumeTrue(File(dir, "vector_estimator.onnx").canRead())
        val env = OrtEnvironment.getEnvironment()
        for (threads in listOf(3, 4, 6, 8)) {
            val t0 = System.nanoTime()
            val voice = BlueVoice(env, dir, File(dir, "noa.json"), null, threads)
            val load = (System.nanoTime() - t0) / 1_000_000
            val report = lines.joinToString { line ->
                voice.voice(line, totalStep = 5)  // warm
                val s = System.nanoTime()
                val samples = voice.voice(line, totalStep = 5)!!
                val ms = (System.nanoTime() - s) / 1_000_000
                "${"%.1f".format(samples.size / voice.sampleRate.toFloat())} s in $ms ms"
            }
            Log.i(TAG, "voice threads $threads, 5 steps: load $load ms | $report")
            voice.close()
        }
    }

    private fun seeded(): (Int) -> FloatArray = { n -> val r = Random(7); FloatArray(n) { r.nextGaussian().toFloat() } }

    private companion object { const val TAG = "PukiBench" }
}
