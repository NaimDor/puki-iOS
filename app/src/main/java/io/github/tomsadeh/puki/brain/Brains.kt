package io.github.tomsadeh.puki.brain

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.exp
import kotlin.random.Random

/**
 * Puki's own small model (train/: pretrained, then fine-tuned on Puki's lines), through ONNX Runtime
 * with a KV cache (train/brains_export.py): it writes one line for a task and input, in the format it
 * was trained on (train/fmt.py), and answers the safety gate with the probabilities of its digits.
 * [dir] holds brains_int8.onnx, tokenizer.json and brains.json.
 */
class Brains(private val env: OrtEnvironment, dir: File, threads: Int = 4) {
    private val session: OrtSession = env.createSession(File(dir, "brains_int8.onnx").path, OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    })
    private val bpe = Bpe(File(dir, "tokenizer.json").readText())
    private val layers: Int
    private val kvHeads: Int
    private val headDim: Int
    private val eot: Int
    private val context: Int
    private val digits: Map<String, Int>  // the gate's answers, " 0".." 3", each one token

    init {
        val cfg = JSONObject(File(dir, "brains.json").readText())
        layers = cfg.getInt("layers")
        kvHeads = cfg.getInt("kv_heads")
        headDim = cfg.getInt("head_dim")
        eot = cfg.getInt("eot")
        context = cfg.getInt("context")
        digits = "0123".associate { d -> d.toString() to bpe.encode(" $d").single() }
    }

    /** The prompt, as train/fmt.py writes it. */
    private fun prompt(task: String, lang: String, girl: Boolean, input: String) =
        "lang: $lang\nchild: ${if (girl) "girl" else "boy"}\ntask: $task\ninput: $input\npuki:"

    /** Puki's line for [input] (null if it came out empty): sampled as it was evaluated, temperature 0.7, top-p 0.9. */
    fun line(task: String, lang: String, girl: Boolean, input: String, maxNew: Int = 48, random: Random = Random): String? {
        val out = ArrayList<Int>()
        generate(bpe.encode(prompt(task, lang, girl, input)), maxNew) { logits ->
            sample(logits, 0.7f, 0.9f, random).takeIf { it != eot }?.also { out += it }
        }
        return bpe.decode(out.toIntArray()).trim().ifEmpty { null }
    }

    /** The gate's probabilities for "0".."3" (Safety.gateCategory): what she said, ordinary or careful. */
    fun gate(heard: String, lang: String, girl: Boolean): Map<String, Double> {
        var probs = emptyMap<String, Double>()
        generate(bpe.encode(prompt("gate", lang, girl, heard)), 1) { logits ->
            val p = softmax(logits, 1f)
            probs = digits.mapValues { (_, id) -> p[id].toDouble() }
            null
        }
        return probs
    }

    /** Runs the prompt, then a token at a time: [next] gets the logits and gives the token to go on with, or null to stop. */
    private fun generate(ids: IntArray, maxNew: Int, next: (FloatArray) -> Int?) {
        val start = if (ids.size > context - maxNew) ids.copyOfRange(ids.size - (context - maxNew), ids.size) else ids
        val empty = longArrayOf(layers.toLong(), kvHeads.toLong(), 0, headDim.toLong())
        var pastK: OnnxTensor = OnnxTensor.createTensor(env, FloatBuffer.allocate(0), empty)
        var pastV: OnnxTensor = OnnxTensor.createTensor(env, FloatBuffer.allocate(0), empty)
        var result: OrtSession.Result? = null
        var input = start
        try {
            repeat(maxNew) {
                val idsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(input.size) { input[it].toLong() }), longArrayOf(1, input.size.toLong()))
                val r = idsTensor.use { session.run(mapOf("ids" to it, "past_k" to pastK, "past_v" to pastV)) }
                // The cache it hands back is the next step's input; the step before can go now
                val previous = result
                if (previous == null) { pastK.close(); pastV.close() } else previous.close()
                result = r
                pastK = r.get("next_k").get() as OnnxTensor
                pastV = r.get("next_v").get() as OnnxTensor
                // (straight from the tensor's buffer: no boxed nested arrays every token)
                val buffer = (r.get("logits").get() as OnnxTensor).floatBuffer
                val logits = FloatArray(buffer.remaining()).also { buffer.get(it) }
                input = intArrayOf(next(logits) ?: return)
            }
        } finally {
            result?.close() ?: run { pastK.close(); pastV.close() }
        }
    }

    private fun softmax(logits: FloatArray, temperature: Float): FloatArray {
        val max = logits.max()
        val p = FloatArray(logits.size) { exp((logits[it] - max) / temperature) }
        val sum = p.sum()
        for (i in p.indices) p[i] /= sum
        return p
    }

    /**
     * A token from the top-p share of the distribution at [temperature]. Only tokens within a
     * ten-thousandth of the likeliest are sorted (the rest can't reach the top-p share): a few dozen,
     * not all 12k, every token.
     */
    private fun sample(logits: FloatArray, temperature: Float, topP: Float, random: Random): Int {
        var max = Float.NEGATIVE_INFINITY
        for (l in logits) if (l > max) max = l
        val floor = max + temperature * LN_1E4
        var n = 0
        for (l in logits) if (l >= floor) n++
        val ids = IntArray(n)
        val p = FloatArray(n)
        var k = 0
        var sum = 0f
        for (i in logits.indices) if (logits[i] >= floor) {
            ids[k] = i
            p[k] = exp((logits[i] - max) / temperature)
            sum += p[k]
            k++
        }
        val order = (0 until n).sortedByDescending { p[it] }
        var mass = 0f
        var cut = n
        for ((j, o) in order.withIndex()) {
            mass += p[o] / sum
            if (mass >= topP) { cut = j + 1; break }
        }
        var x = random.nextFloat() * mass
        for (j in 0 until cut) {
            x -= p[order[j]] / sum
            if (x <= 0f) return ids[order[j]]
        }
        return ids[order[cut - 1]]
    }

    fun close() = session.close()

    private companion object {
        val LN_1E4 = kotlin.math.ln(1e-4).toFloat()
    }
}
