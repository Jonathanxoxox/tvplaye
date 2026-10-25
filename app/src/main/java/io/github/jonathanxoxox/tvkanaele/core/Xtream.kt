package io.github.jonathanxoxox.tvkanaele.core

import org.json.JSONArray
import java.net.URLEncoder

/** Zugang über die Xtream-Codes-API (Server, Benutzer, Passwort), wie ihn viele IPTV-Anbieter vergeben. */
class XtreamAccount(server: String, val username: String, val password: String) {
    val base: String = server.trim().trimEnd('/').let {
        if (it.startsWith("http://", true) || it.startsWith("https://", true)) it else "http://$it"
    }

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")
    private val auth get() = "username=${q(username)}&password=${q(password)}"

    fun liveStreamsUrl() = "$base/player_api.php?$auth&action=get_live_streams"
    fun liveCategoriesUrl() = "$base/player_api.php?$auth&action=get_live_categories"
    fun epgUrl() = "$base/xmltv.php?$auth"
    fun streamUrl(streamId: String, extension: String = "ts") = "$base/live/$username/$password/$streamId.$extension"
}

object XtreamParser {
    fun parse(account: XtreamAccount, streamsJson: String, categoriesJson: String?): List<IptvChannel> {
        val categories = mutableMapOf<String, String>()
        categoriesJson?.trim()?.takeIf { it.startsWith("[") }?.let { json ->
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                categories[c.optString("category_id")] = c.optString("category_name")
            }
        }

        val arr = JSONArray(streamsJson)
        val result = ArrayList<IptvChannel>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val id = s.opt("stream_id")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: continue
            val name = s.optString("name").trim()
            result += IptvChannel(
                key = "x:$id",
                name = name.ifEmpty { id },
                streamUrl = account.streamUrl(id),
                group = categories[s.optString("category_id")].orEmpty(),
                logo = s.optString("stream_icon").takeIf { it.startsWith("http") },
                tvgId = s.optString("epg_channel_id").takeIf { it.isNotBlank() && it != "null" },
                tvgName = name,
            )
        }
        return result
    }
}
