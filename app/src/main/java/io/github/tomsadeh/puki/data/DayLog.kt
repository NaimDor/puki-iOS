package io.github.tomsadeh.puki.data

import android.content.Context
import io.github.tomsadeh.puki.data.content.DayEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * What the child did today, for Puki's bedtime recap: each game records a thing as it happens (fed the cow,
 * counted seven flowers), and bedtime reads the day back. Kept in a small file so a day survives the app
 * closing; a new day starts it over. Only on this device, like everything Puki knows.
 */
object DayLog {
    private var file: File? = null
    private val events = mutableListOf<DayEvent>()
    private var day = ""

    fun init(context: Context) {
        file = File(context.filesDir, "day.json")
        runCatching {
            val o = JSONObject(file!!.readText())
            if (o.getString("day") == LocalDate.now().toString()) {
                day = o.getString("day")
                val a = o.getJSONArray("events")
                for (i in 0 until a.length()) a.getJSONArray(i).let { events += DayEvent(it.getString(0), it.getString(1)) }
            }
        }
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
        runCatching {
            file?.writeText(JSONObject().put("day", day)
                .put("events", JSONArray(events.map { JSONArray(listOf(it.he, it.en)) })).toString())
        }
    }

    /** Today's things, the latest last. */
    @Synchronized
    fun today(): List<DayEvent> = if (day == LocalDate.now().toString()) events.toList() else emptyList()

    private const val MAX = 12
}
