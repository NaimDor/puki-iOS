package io.github.tomsadeh.puki.data

import android.content.Context
import io.github.tomsadeh.puki.data.content.DayEvent
import java.io.File

/**
 * What the child did this session, for Puki's bedtime recap: each game records a thing as it happens (fed the
 * cow, counted seven flowers), and bedtime reads it back. Only in memory, never saved: closing the app forgets
 * it, as the site promises ("Puki's brain remembers only its last few lines while you play").
 */
object DayLog {
    private val events = mutableListOf<DayEvent>()

    fun init(context: Context) {
        File(context.filesDir, "day.json").delete()  // (an earlier build kept the day in a file)
    }

    /** She did [event] just now (the same thing twice counts once; the latest dozen are kept). */
    @Synchronized
    fun record(event: DayEvent?) {
        if (event == null) return
        events.remove(event)
        events += event
        while (events.size > MAX) events.removeAt(0)
    }

    /** This session's things, the latest last. */
    @Synchronized
    fun today(): List<DayEvent> = events.toList()

    private const val MAX = 12
}
