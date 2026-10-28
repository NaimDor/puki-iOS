package io.github.tomsadeh.puki.brain

import ai.onnxruntime.OrtEnvironment
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Measurements on a real device, not checks; results go to logcat (tag PukiBench). Push the ears'
 * files (train/kolstream_export.py's release folder) and a few 16 kHz WAVs first:
 *     adb push encoder.onnx step.onnx tokens.txt *.wav /data/local/tmp/kol/
 * then: gradlew connectedGithubDebugAndroidTest -Ppuki.bench (a separate app beside the real one).
 */
@RunWith(AndroidJUnit4::class)
class KolEarsBench {
    private val dir = File("/data/local/tmp/kol")

    @Test
    fun kolEarsSpeed() {
        assumeTrue(File(dir, "encoder.onnx").canRead())
        val env = OrtEnvironment.getEnvironment()
        val wavs = dir.listFiles { f -> f.name.endsWith(".wav") }!!.sortedBy { it.name }
        for (threads in listOf(4, 2)) {
            val t0 = System.nanoTime()
            val ears = KolEars(env, dir, threads)
            Log.i(TAG, "kol threads $threads: load ${(System.nanoTime() - t0) / 1_000_000} ms")
            for (w in wavs) {
                val audio = pcm16(w.readBytes())
                ears.transcribe(audio)  // warm
                val times = (1..3).map { val s = System.nanoTime(); val text = ears.transcribe(audio); (System.nanoTime() - s) / 1_000_000 to text }
                Log.i(TAG, "kol threads $threads ${w.name} (${"%.1f".format(audio.size / 16000f)} s): ${times.joinToString { "${it.first}" }} ms | ${times.last().second}")
            }
            ears.close()
        }
    }

    /** Whether Google's own on-device recognizer has Hebrew (Android 13 and later can say). */
    @Test
    fun googleOnDeviceHebrew() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        Log.i(TAG, "google on-device recognizer available: ${SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)}")
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) || android.os.Build.VERSION.SDK_INT < 33) return
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            r.checkRecognitionSupport(intent, ctx.mainExecutor, object : RecognitionSupportCallback {
                override fun onSupportResult(s: RecognitionSupport) {
                    Log.i(TAG, "google installed=${s.installedOnDeviceLanguages} pending=${s.pendingOnDeviceLanguages} " +
                        "supported=${s.supportedOnDeviceLanguages} online=${s.onlineLanguages}")
                    r.destroy(); done.countDown()
                }
                override fun onError(error: Int) {
                    Log.i(TAG, "google support check error $error"); r.destroy(); done.countDown()
                }
            })
        }
        done.await(20, TimeUnit.SECONDS)
    }

    private companion object { const val TAG = "PukiBench" }
}
