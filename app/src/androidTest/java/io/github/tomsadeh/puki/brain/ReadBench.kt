package io.github.tomsadeh.puki.brain

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.LongBuffer
import kotlin.random.Random

/**
 * How fast the reading model (train/read_export.py) reads a line on a real device; results go to logcat
 * (tag PukiBench). Push it first:
 *     adb push read.onnx read_int8.onnx /data/local/tmp/read/
 * then: gradlew connectedGithubDebugAndroidTest -Ppuki.bench
 *     -Pandroid.testInstrumentationRunnerArguments.class=io.github.tomsadeh.puki.brain.ReadBench
 */
@RunWith(AndroidJUnit4::class)
class ReadBench {
    private val dir = File("/data/local/tmp/read")

    @Test
    fun readSpeed() {
        assumeTrue(File(dir, "read_int8.onnx").canRead())
        val env = OrtEnvironment.getEnvironment()
        val random = Random(0)
        for (name in listOf("read_int8.onnx", "read.onnx")) {
            for (threads in listOf(1, 2, 4)) {
                val t0 = System.nanoTime()
                val session = env.createSession(File(dir, name).path, OrtSession.SessionOptions().apply { setIntraOpNumThreads(threads) })
                val load = (System.nanoTime() - t0) / 1_000_000
                val report = listOf(20, 40, 80).joinToString { length ->
                    // the gender token, then letters and spaces (the ids of read_label.ALPHABET)
                    val ids = LongArray(length + 1) { if (it == 0) 41L else if (it % 6 == 0) 1L else random.nextLong(2, 29) }
                    fun once(): Long {
                        val s = System.nanoTime()
                        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong())).use { input ->
                            session.run(mapOf("ids" to input)).close()
                        }
                        return (System.nanoTime() - s) / 1000
                    }
                    repeat(3) { once() }
                    val times = (1..20).map { once() }.sorted()
                    "$length letters ${"%.1f".format(times[10] / 1000.0)} ms"
                }
                Log.i(TAG, "read $name threads $threads: load $load ms | median $report")
                session.close()
            }
        }
    }

    private companion object { const val TAG = "PukiBench" }
}
