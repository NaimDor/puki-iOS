package io.github.tomsadeh.puki.data

import android.content.Context
import io.github.tomsadeh.puki.data.content.Moment
import java.io.File

/**
 * This session's game moments, so Puki can reach back to one ("like the cow before!", ReachBack). Only in
 * memory, never saved: closing the app forgets them, so nothing carries from one session to the next. No names
 * and nothing she said: just what happened in a game, as the game told Puki.
 */
object MomentMemory {
    private val moments = mutableListOf<Moment>()

    fun init(context: Context) {
        File(context.filesDir, "moments.json").delete()  // (an earlier build kept them between sessions)
    }

    @Synchronized
    fun record(he: String, en: String, game: String) {
        moments += Moment(System.currentTimeMillis(), he.take(200), en.take(200), game)
        while (moments.size > MAX) moments.removeAt(0)
    }

    @Synchronized
    fun history(): List<Moment> = moments.toList()

    private const val MAX = 200
}
