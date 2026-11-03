package io.github.tomsadeh.puki.data

import android.content.Context
import io.github.tomsadeh.puki.data.content.Moment
import org.json.JSONArray
import java.io.File

/**
 * The game moments Puki can reach back to ("like the cow before!", ReachBack). In memory only, so closing the
 * app forgets them; unless the parent turned on "Puki remembers between sessions" ([keep]), and then the last
 * three days' moments are kept in a small file on the device, so he can link to yesterday's (turning it off
 * deletes it). No names and nothing she said: just what happened in a game, as the game told Puki.
 */
object MomentMemory {
    private var file: File? = null
    private val moments = mutableListOf<Moment>()
    private var keep = false

    fun init(context: Context) {
        file = File(context.filesDir, "moments.json")
    }

    /** The parent's setting: keep the moments in a file (and read back what's there), or not (and delete it). */
    @Synchronized
    fun keep(on: Boolean) {
        if (!on) {
            file?.delete()  // (always: a file left from before, too)
            moments.removeAll { it.t < sessionStart }  // (what came back from the file goes too)
            keep = false
            return
        }
        if (keep) return
        keep = true
        runCatching {
            val a = JSONArray(file!!.readText())
            val kept = (0 until a.length()).map { a.getJSONArray(it).let { m -> Moment(m.getLong(0), m.getString(1), m.getString(2), m.optString(3)) } }
            moments.addAll(0, kept.filter { it.t < sessionStart && it.t > System.currentTimeMillis() - KEEP_MS })
        }
        save()
    }

    @Synchronized
    fun record(he: String, en: String, game: String) {
        val now = System.currentTimeMillis()
        moments += Moment(now, he.take(200), en.take(200), game)
        moments.removeAll { it.t < now - KEEP_MS }
        while (moments.size > MAX) moments.removeAt(0)
        save()
    }

    @Synchronized
    fun history(): List<Moment> = moments.toList()

    private fun save() {
        if (keep) runCatching { file?.writeText(JSONArray(moments.map { JSONArray(listOf(it.t, it.he, it.en, it.game)) }).toString()) }
    }

    private val sessionStart = System.currentTimeMillis()
    private const val KEEP_MS = 3 * 24 * 3600_000L
    private const val MAX = 400
}
