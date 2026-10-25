package io.github.jonathanxoxox.tvkanaele.core

/** Ein Sender aus der Playlist. [key] bleibt über Aktualisierungen hinweg gleich (Favoriten hängen daran). */
data class IptvChannel(
    val key: String,
    val name: String,
    val streamUrl: String,
    val group: String = "",
    val logo: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
)

/** Eine Sendung aus dem EPG. [epgChannel] ist die Kanal-ID aus der XMLTV-Datei. */
data class Programme(
    val epgChannel: String,
    val start: Long,
    val end: Long,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val categories: List<String> = emptyList(),
    val icon: String? = null,
) {
    fun isRunning(now: Long) = start <= now && now < end
}

data class Playlist(val channels: List<IptvChannel>, val epgUrls: List<String> = emptyList())
