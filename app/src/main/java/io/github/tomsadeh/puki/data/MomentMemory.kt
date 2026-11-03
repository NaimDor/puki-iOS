package io.github.tomsadeh.puki.data

import android.content.Context
import io.github.tomsadeh.puki.data.content.Moment
import org.json.JSONArray
import java.io.File

/**
 * The game moments of the last few days, so Puki can reach back to one ("like the cow yesterday!",
 * ReachBack). Kept in a small file on the device only, like everything Puki knows; older than three days
 * goes. No names and nothing she said: just what happened in a game, as the game told Puki.
 */
object MomentMemory {
    private var file: File? = null
    private val moments = mutableListOf<Moment>()

    fun init(context: Context) {
        file = File(context.filesDir, "moments.json")
        runCatching {
            val a = JSONArray(file!!.readText())
            for (i in 0 until a.length()) a.getJSONArray(i).let { moments += Moment(it.getLong(0), it.getString(1), it.getString(2), it.optString(3)) }
        }
    }

    @Synchronized
    fun record(he: String, en: String, game: String) {
        val now = System.currentTimeMillis()
        moments += Moment(now, he.take(200), en.take(200), game)
        moments.removeAll { it.t < now - KEEP_MS }
        while (moments.size > MAX) moments.removeAt(0)
        runCatching { file?.writeText(JSONArray(moments.map { JSONArray(listOf(it.t, it.he, it.en, it.game)) }).toString()) }
    }

    @Synchronized
    fun history(): List<Moment> = moments.toList()

    private const val KEEP_MS = 3 * 24 * 3600_000L
    private const val MAX = 400
}
