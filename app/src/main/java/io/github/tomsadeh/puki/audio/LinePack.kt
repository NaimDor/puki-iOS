package io.github.tomsadeh.puki.audio

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Puki's approved lines for the game's moments (the PC's line pack, see brain/packs.py): written
 * at night on the PC, approved by a parent on its dashboard, and kept here with their audio, so
 * Puki can react to a moment instantly, without waking the PC's brain, and even when it's off.
 *
 * [fetch] does a GET on the PC (the companion's paired connection) and returns the body, or null.
 */
class LinePack(filesDir: File, private val fetch: (String) -> ByteArray?) {
    data class Line(val id: String, val situation: String, val lang: String, val gender: String, val text: String)

    private val dir = File(filesDir, "pack")
    private val indexFile = File(dir, "index.json")
    @Volatile private var version = ""
    @Volatile private var lines: List<Line> = emptyList()
    private var lastPlayed = ""

    /** Reads the lines already on the tablet (call off the main thread). */
    @Synchronized
    fun load() {
        runCatching { read(JSONObject(indexFile.readText())) }
    }

    private fun read(json: JSONObject) {
        val arr = json.optJSONArray("lines") ?: JSONArray()
        lines = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Line(o.getString("id"), o.getString("situation"), o.getString("lang"), o.getString("gender"), o.getString("text"))
        }.filter { ID.matches(it.id) }
        version = json.optString("version")
    }

    /** Brings the approved lines up to date with the PC (call off the main thread); false if the PC didn't answer. */
    @Synchronized
    fun sync(): Boolean {
        val manifest = fetch("/pack")?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() } ?: return false
        if (manifest.optString("version") == version && version.isNotEmpty()) return true
        val fresh = LinePack(dir.parentFile ?: dir, fetch).also { it.read(manifest) }.lines
        dir.mkdirs()
        for (line in fresh) {
            val f = File(dir, "${line.id}.ogg")
            if (!f.exists()) fetch("/pack/audio?id=${line.id}")?.takeIf { it.size > 4 }?.let { f.writeBytes(it) }
        }
        // Lines the parent took back, or ones whose audio didn't come: gone
        val keep = fresh.filter { File(dir, "${it.id}.ogg").exists() }
        val keepIds = keep.map { "${it.id}.ogg" }.toSet()
        dir.listFiles { f -> f.name.endsWith(".ogg") && f.name !in keepIds }?.forEach { it.delete() }
        val out = JSONObject().put("version", manifest.optString("version")).put("lines", JSONArray().apply {
            keep.forEach { put(JSONObject().put("id", it.id).put("situation", it.situation).put("lang", it.lang).put("gender", it.gender).put("text", it.text)) }
        })
        indexFile.writeText(out.toString())
        read(out)
        if (keep.size != fresh.size) version = ""  // some audio is missing: try again next time
        return true
    }

    /** An approved line for this moment (not the one played just before), with its audio file, or null. */
    fun pick(situation: String, lang: String, girl: Boolean): Pair<Line, File>? {
        val gender = if (girl) "girl" else "boy"
        val matches = lines.filter { it.situation == situation.trim() && it.lang == lang && it.gender == gender }
        val choice = matches.filter { it.id != lastPlayed }.randomOrNull() ?: matches.randomOrNull() ?: return null
        val file = File(dir, "${choice.id}.ogg").takeIf { it.exists() } ?: return null
        lastPlayed = choice.id
        return choice to file
    }

    val size get() = lines.size

    private companion object {
        val ID = Regex("^[0-9a-f]{12}$")
    }
}
