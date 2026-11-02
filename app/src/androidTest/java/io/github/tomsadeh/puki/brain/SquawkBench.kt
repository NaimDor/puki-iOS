package io.github.tomsadeh.puki.brain

import ai.onnxruntime.OrtEnvironment
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tomsadeh.puki.brain.voice.BlueVoice
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * How fast Puki's voice (BlueTTS with Noa) speaks on a real device, from phonemes as the reading
 * model will hand them over; results go to logcat (tag PukiBench). Push the voice first:
 *     adb push <BlueTTS2.5 onnx bundle> noa.json /data/local/tmp/voice/
 * then: gradlew connectedGithubDebugAndroidTest -Ppuki.bench
 *     -Pandroid.testInstrumentationRunnerArguments.class=io.github.tomsadeh.puki.brain.SquawkBench
 */
@RunWith(AndroidJUnit4::class)
class SquawkBench {
    private val dir = File("/data/local/tmp/voice")

    @Test
    fun voiceSpeed() {
        assumeTrue(File(dir, "vector_estimator.onnx").canRead())
        val env = OrtEnvironment.getEnvironment()
        val lines = listOf(
            "haχazˈiʁ ʁaʔˈev.",
            "hatsˈel niʁʔˈa kmˈo pˈil ʔanˈak!",
            "haχajˈa hazˈot ʔosˈa hˈop! vehˈop! bˈoʔi navˈi lˈo ʔˈoχel, hˈu mamˈaʃ ʁaʔˈev.",
        )
        for (threads in listOf(2, 4)) {
            val t0 = System.nanoTime()
            val voice = BlueVoice(env, dir, File(dir, "noa.json"), null, threads)
            Log.i(TAG, "voice threads $threads: load ${(System.nanoTime() - t0) / 1_000_000} ms")
            for (line in lines) {
                voice.voice(line)  // warm
                val runs = (1..3).map {
                    val s = System.nanoTime()
                    val samples = voice.voice(line)!!
                    (System.nanoTime() - s) / 1_000_000 to samples.size / voice.sampleRate.toFloat()
                }
                val audio = runs.last().second
                Log.i(TAG, "voice threads $threads (${"%.1f".format(audio)} s of speech): " +
                    "${runs.joinToString { "${it.first}" }} ms, ${"%.2f".format(runs.minOf { it.first } / 1000f / audio)}x real time | $line")
            }
        }
    }

    private companion object { const val TAG = "PukiBench" }
}
