package io.github.tomsadeh.puki

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.tomsadeh.puki.audio.Companion
import io.github.tomsadeh.puki.audio.Connection
import io.github.tomsadeh.puki.audio.VoicePlayer
import io.github.tomsadeh.puki.brain.Persona
import io.github.tomsadeh.puki.brain.voice.VoiceText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

/** Runs the device brain's text handling on Android's own regex engine (debug builds only). */
class SelfTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tag = "PukiSelfTest"
        try {
            val p = Persona()
            fun check(what: String, got: Any?, want: Any?) =
                Log.i(tag, "${if (got == want) "ok  " else "FAIL"} $what: got [$got] want [$want]")
            check("clean he", p.clean("וואו! צהוב כמו השמש!", "he"), "וואו! צהוב כמו השמש!")
            check("compliment dropped", p.clean("את מקסימה! הפרה אוהבת עשב!", "he"), "הפרה אוהבת עשב!")
            check("banned word", p.clean("זו טעות קטנה", "he"), null)
            check("hungry is fine", p.clean("הכלב רעב!", "he"), "הכלב רעב!")
            check("english", p.clean("Yay! Green!", "en"), "Yay! Green!")
            check("word", p.wordMatch("הכלב", "כלב"), "word")
            check("close", p.wordMatch("פה", "פרה"), "close")
            check("tried", p.wordMatch("אריה", "קוף"), "tried")
            check("misread", p.forVoice("הידד! צבע אדום!"), "יש! צבע אדום!")
            check("normalize dash", VoiceText.prepareTextForSynthesis("סבתא, אבא ואמא – כולם כאן!"), "סבתא, אבא ואמא כולם כאן!")
            check("geresh", VoiceText.prepareTextForSynthesis("ג'ירפה גבוהה."), "ג׳ירפה גבוהה.")
            check("chunks", VoiceText.chunkText("ʔˈejze tsijˈuʁ jafˈe! ʃˈemeʃ.").size, 1)
            check("tags", VoiceText.stripLangTags("<he>ʃalˈom</he>"), "ʃalˈom")
            // With -e echotest 1: play a short tone through the echo parrot's voice (high and fast)
            if (intent.getStringExtra("echotest") != null) {
                val rate = io.github.tomsadeh.puki.audio.MicRecorder.RATE
                val n = rate / 2
                val pcm = java.nio.ByteBuffer.allocate(44 + n * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
                    put("RIFF".toByteArray()); putInt(36 + n * 2); put("WAVE".toByteArray()); put("fmt ".toByteArray())
                    putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
                    put("data".toByteArray()); putInt(n * 2)
                    for (i in 0 until n) putShort((8000 * kotlin.math.sin(2 * Math.PI * 330 * i / rate)).toInt().toShort())
                }.array()
                val t0 = System.currentTimeMillis()
                runBlocking { io.github.tomsadeh.puki.audio.EchoVoice.play(pcm, 0.8f) }
                check("echo played (ms, about 500/1.45 + 150)", (System.currentTimeMillis() - t0) in 300..900, true)
            }
            // With -e timelimit <minutes>: set the parent's time limit (0 = off), to see the goodbye soon
            intent.getStringExtra("timelimit")?.toIntOrNull()?.let { minutes ->
                runBlocking { (context.applicationContext as PukiApplication).settingsRepository.updateTimeLimit(minutes) }
                Log.i(tag, "time limit set to $minutes min")
            }
            // With --es host <ip:port> --es code <CODE> --es pin <hex>: the app's real connection check
            // against that PC (right pin, wrong pin, and first use), off the main thread
            val host = intent.getStringExtra("host")
            val code = intent.getStringExtra("code")
            if (host != null && code != null) {
                val pin = intent.getStringExtra("pin") ?: ""
                val app = context.applicationContext
                val companion = Companion(app, VoicePlayer(app), CoroutineScope(Dispatchers.IO))
                val pending = goAsync()
                thread {
                    try {
                        runBlocking {
                            check("https, pinned", companion.checkConnection(host, code, pin), Connection.OK)
                            check("https, other pin", companion.checkConnection(host, code, "00".repeat(32)), Connection.WRONG_PC)
                            var learned = ""
                            companion.onPinLearned = { learned = it }
                            check("https, first use", companion.checkConnection(host, code, ""), Connection.OK)
                            check("first use learns the pin", learned, pin)
                            check("wrong code", companion.checkConnection(host, "WRONGCOD", pin), Connection.WRONG_CODE)
                        }
                    } catch (e: Throwable) {
                        Log.e(tag, "CRASH", e)
                    } finally {
                        Log.i(tag, "done")
                        pending.finish()
                    }
                }
                return
            }
            Log.i(tag, "done")
        } catch (e: Throwable) {
            Log.e(tag, "CRASH", e)
        }
    }
}
