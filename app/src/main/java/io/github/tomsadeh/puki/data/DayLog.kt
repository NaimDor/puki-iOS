package io.github.tomsadeh.puki.data

import android.content.Context
import io.github.tomsadeh.puki.data.content.DayEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * What the child did today, for Puki's bedtime recap: each game records a thing as it happens (fed the cow,
 * counted seven flowers), and bedtime reads it back. In memory only, so closing the app forgets it, as the site
 * promises; unless the parent turned on "Puki remembers between sessions" ([keep]), and then today's things are
 * kept in a small file on the device (a new day starts it over; turning it off deletes it).
 */
object DayLog {
    private var file: File? = null
    private val events = mutableListOf<DayEvent>()
    private var day = LocalDate.now().toString()
    private var keep = false

    fun init(context: Context) {
        file = File(context.filesDir, "day.json")
    }

    /** The parent's setting: keep today's things in a file (and read back what's there), or not (and delete it). */
    @Synchronized
    fun keep(on: Boolean) {
        if (on == keep) return
        keep = on
        if (!on) { file?.delete(); return }
        runCatching {
            val o = JSONObject(file!!.readText())
            if (o.getString("day") == LocalDate.now().toString()) {
                val a = o.getJSONArray("events")
                for (i in 0 until a.length()) a.getJSONArray(i).let { DayEvent(it.getString(0), it.getString(1)) }.let { if (it !in events) events.add(0, it) }
            }
        }
        save()
    }

    /** She did [event] just now (the same thing twice counts once; the latest dozen are kept). */
    @Synchronized
    fun record(event: DayEvent?) {
        if (event == null) return
        val today = LocalDate.now().toString()
        if (today != day) { events.clear(); day = today }
        events.remove(event)
        events += event
        while (events.size > MAX) events.removeAt(0)
        save()
    }

    /** Today's things, the latest last. */
    @Synchronized
    fun today(): List<DayEvent> = if (day == LocalDate.now().toString()) events.toList() else emptyList()

    private fun save() {
        if (!keep) return
        runCatching {
            file?.writeText(JSONObject().put("day", day).put("events", JSONArray(events.map { JSONArray(listOf(it.he, it.en)) })).toString())
        }
    }

    private const val MAX = 12
}
