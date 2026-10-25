package io.github.jonathanxoxox.tvkanaele

import android.content.Context
import android.content.SharedPreferences
import io.github.jonathanxoxox.tvkanaele.core.Interests
import io.github.jonathanxoxox.tvkanaele.core.Text
import io.github.jonathanxoxox.tvkanaele.core.XtreamAccount

/** Alle Einstellungen der App in einer SharedPreferences-Datei. */
class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    enum class Source { M3U, XTREAM }

    /** Was beim Klick auf eine Kachel passiert. */
    enum class Player {
        /** Stream-Link an TiviMate, falls TiviMate ihn annimmt, sonst eingebauter Player. */
        TIVIMATE_LINK,
        /** Nur TiviMate öffnen (springt auf den zuletzt gesehenen Sender). */
        TIVIMATE_APP,
        BUILTIN,
        /** Beliebiger anderer Player (VLC, Just Player, MX …) über die Systemauswahl. */
        EXTERNAL,
    }

    var source: Source
        get() = enumOr(prefs.getString("source", null), Source.M3U)
        set(v) = put("source", v.name)

    var m3uUrl by string("m3u_url")
    var xtreamServer by string("xtream_server")
    var xtreamUser by string("xtream_user")
    var xtreamPassword by string("xtream_password")
    /** Zusätzliche oder abweichende EPG-Adressen, durch Komma getrennt. Leer = aus Playlist bzw. Xtream. */
    var epgUrl by string("epg_url")
    var userAgent by string("user_agent")

    var teams by string("teams", "Real Madrid, FC Barcelona")
    var leagues by string("leagues", "LaLiga, Champions League, Bundesliga, DFB-Pokal, Copa del Rey")
    var keywords by string("keywords", "Clásico")
    var excludes by string("excludes", "Highlights, Zusammenfassung, Wiederholung, Wdh, Replay, Re-Live, Relive, Best of, Doku")
    var preferredChannels by string("preferred_channels", "")
    var includeAllFootball by bool("all_football", true)
    var lookaheadHours: Int
        get() = prefs.getInt("lookahead_hours", 3)
        set(v) = prefs.edit().putInt("lookahead_hours", v.coerceIn(1, 24)).apply()

    var player: Player
        get() = enumOr(prefs.getString("player", null), Player.TIVIMATE_LINK)
        set(v) = put("player", v.name)

    var drawTiles by bool("draw_tiles", true)
    var watchNextEnabled by bool("watch_next", true)

    /** Favoriten in der gewählten Reihenfolge (Senderschlüssel). */
    var favorites: List<String>
        get() = prefs.getString("favorites", "").orEmpty().split('\n').filter { it.isNotEmpty() }
        set(v) = put("favorites", v.joinToString("\n"))

    var lastFullSync: Long
        get() = prefs.getLong("last_full_sync", 0L)
        set(v) = prefs.edit().putLong("last_full_sync", v).apply()

    /** Interessen oder Favoriten haben sich geändert: gespeicherten EPG neu filtern. */
    var needsReparse by bool("needs_reparse", false)
    var lastSyncMessage by nullableString("last_sync")

    val isConfigured: Boolean
        get() = when (source) {
            Source.M3U -> m3uUrl.isNotBlank()
            Source.XTREAM -> xtreamServer.isNotBlank() && xtreamUser.isNotBlank()
        }

    fun xtreamAccount() = XtreamAccount(xtreamServer, xtreamUser, xtreamPassword)

    fun interests() = Interests(
        teams = Text.splitList(teams),
        leagues = Text.splitList(leagues),
        keywords = Text.splitList(keywords),
        excludes = Text.splitList(excludes),
        preferredChannels = Text.splitList(preferredChannels),
        includeAllFootball = includeAllFootball,
    )

    private fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private fun string(key: String, default: String = "") = object : kotlin.properties.ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = prefs.getString(key, default) ?: default
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: String) = put(key, value.trim())
    }

    private fun nullableString(key: String) = object : kotlin.properties.ReadWriteProperty<Any?, String?> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = prefs.getString(key, null)
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: String?) =
            prefs.edit().putString(key, value).apply()
    }

    private fun bool(key: String, default: Boolean) = object : kotlin.properties.ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = prefs.getBoolean(key, default)
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Boolean) =
            prefs.edit().putBoolean(key, value).apply()
    }

    companion object {
        const val TIVIMATE_PACKAGE = "ar.tvplayer.tv"
    }
}
