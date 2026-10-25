package io.github.jonathanxoxox.tvkanaele.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Speichert Sender und gefilterte EPG-Daten als JSON im App-Verzeichnis, damit der Launcher-Abgleich
 * alle 15 Minuten ohne neuen Download auskommt.
 */
class GuideStore(private val dir: File) {
    private val channelsFile get() = File(dir, "channels.json")
    private val guideFile get() = File(dir, "guide.json")

    fun saveChannels(channels: List<IptvChannel>) = write(channelsFile, channelsToJson(channels).toString())

    fun loadChannels(): List<IptvChannel> =
        channelsFile.takeIf { it.exists() }?.let { channelsFromJson(JSONArray(it.readText())) }.orEmpty()

    fun saveGuide(epgMap: Map<String, String>, programmes: List<Programme>) {
        val json = JSONObject()
            .put("map", JSONObject(epgMap as Map<*, *>))
            .put("programmes", programmesToJson(programmes))
        write(guideFile, json.toString())
    }

    fun load(): Guide {
        val channels = loadChannels()
        if (!guideFile.exists()) return Guide(channels, emptyMap(), emptyList())
        val json = JSONObject(guideFile.readText())
        val mapJson = json.optJSONObject("map") ?: JSONObject()
        val map = mapJson.keys().asSequence().associateWith { mapJson.getString(it) }
        return Guide(channels, map, programmesFromJson(json.optJSONArray("programmes") ?: JSONArray()))
    }

    private fun write(file: File, text: String) {
        dir.mkdirs()
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        fun channelsToJson(channels: List<IptvChannel>) = JSONArray().apply {
            channels.forEach { c ->
                put(JSONObject().apply {
                    put("k", c.key); put("n", c.name); put("u", c.streamUrl); put("g", c.group)
                    c.logo?.let { put("l", it) }
                    c.tvgId?.let { put("i", it) }
                    c.tvgName?.let { put("tn", it) }
                    c.userAgent?.let { put("ua", it) }
                    c.referrer?.let { put("r", it) }
                })
            }
        }

        fun channelsFromJson(arr: JSONArray): List<IptvChannel> = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            IptvChannel(
                key = o.getString("k"), name = o.getString("n"), streamUrl = o.getString("u"), group = o.optString("g"),
                logo = o.optStringOrNull("l"), tvgId = o.optStringOrNull("i"), tvgName = o.optStringOrNull("tn"),
                userAgent = o.optStringOrNull("ua"), referrer = o.optStringOrNull("r"),
            )
        }

        fun programmesToJson(programmes: List<Programme>) = JSONArray().apply {
            programmes.forEach { p ->
                put(JSONObject().apply {
                    put("c", p.epgChannel); put("s", p.start); put("e", p.end); put("t", p.title)
                    p.subtitle?.let { put("st", it) }
                    p.description?.let { put("d", it) }
                    if (p.categories.isNotEmpty()) put("cat", JSONArray(p.categories))
                    p.icon?.let { put("ic", it) }
                })
            }
        }

        fun programmesFromJson(arr: JSONArray): List<Programme> = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val cats = o.optJSONArray("cat")
            Programme(
                epgChannel = o.getString("c"), start = o.getLong("s"), end = o.getLong("e"), title = o.getString("t"),
                subtitle = o.optStringOrNull("st"), description = o.optStringOrNull("d"),
                categories = cats?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
                icon = o.optStringOrNull("ic"),
            )
        }

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (has(key) && !isNull(key)) getString(key) else null
    }
}
