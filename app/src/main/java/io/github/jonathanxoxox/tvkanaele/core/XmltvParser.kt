package io.github.jonathanxoxox.tvkanaele.core

import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Liest einen XMLTV-EPG als Datenstrom, ohne die ganze Datei in den Speicher zu laden.
 * Nur Sendungen im Zeitfenster [from, to), für die [keep] true liefert, landen im Ergebnis.
 * Der XmlPullParser wird hereingereicht (Android: Xml.newPullParser(), Tests: KXmlParser).
 */
class XmltvParser(private val parser: XmlPullParser) {

    class Result(
        /** XMLTV-Kanal-ID → Anzeigenamen */
        val channelNames: Map<String, List<String>>,
        val programmes: List<Programme>,
    )

    private val names = mutableMapOf<String, MutableList<String>>()

    /** Die bisher gelesenen Kanäle; in XMLTV stehen sie vor den Sendungen. */
    fun namesSoFar(): Map<String, List<String>> = names

    fun parse(input: InputStream, from: Long, to: Long, keep: (Programme) -> Boolean): Result {
        try {
            parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true)
        } catch (_: Exception) {
        }
        parser.setInput(input, null)
        names.clear()

        val programmes = mutableListOf<Programme>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> readChannel()?.let { (id, list) -> names.getOrPut(id) { mutableListOf() } += list }
                    "programme" -> readProgramme(from, to)?.let { if (keep(it)) programmes += it }
                }
            }
            event = parser.next()
        }
        return Result(names.toMap(), programmes)
    }

    private fun readChannel(): Pair<String, List<String>>? {
        val id = parser.getAttributeValue(null, "id")
        val names = mutableListOf<String>()
        val depth = parser.depth
        while (true) {
            val e = parser.next()
            if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && parser.depth == depth)) break
            if (e == XmlPullParser.START_TAG && parser.name == "display-name") names += readText()
        }
        return id?.takeIf { it.isNotBlank() }?.let { it to names.filter { n -> n.isNotBlank() } }
    }

    private fun readProgramme(from: Long, to: Long): Programme? {
        val channel = parser.getAttributeValue(null, "channel")
        val start = parseTime(parser.getAttributeValue(null, "start"))
        val stop = parseTime(parser.getAttributeValue(null, "stop"))
        val inWindow = channel != null && start != null && stop != null && stop > from && start < to
        if (!inWindow) {
            skip()
            return null
        }

        var title: String? = null
        var subtitle: String? = null
        var desc: String? = null
        var icon: String? = null
        val categories = mutableListOf<String>()
        val depth = parser.depth
        while (true) {
            val e = parser.next()
            if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && parser.depth == depth)) break
            if (e != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "title" -> readText().let { if (title == null && it.isNotBlank()) title = it }
                "sub-title" -> readText().let { if (subtitle == null && it.isNotBlank()) subtitle = it }
                "desc" -> readText().let { if (desc == null && it.isNotBlank()) desc = it.take(MAX_DESC) }
                "category" -> readText().takeIf { it.isNotBlank() }?.let { categories += it }
                "icon" -> if (icon == null) icon = parser.getAttributeValue(null, "src")
            }
        }
        return Programme(channel!!, start!!, stop!!, title ?: return null, subtitle, desc, categories, icon)
    }

    private fun readText(): String {
        val sb = StringBuilder()
        val depth = parser.depth
        while (true) {
            val e = parser.next()
            if (e == XmlPullParser.TEXT || e == XmlPullParser.CDSECT || e == XmlPullParser.ENTITY_REF) sb.append(parser.text ?: "")
            if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && parser.depth == depth)) break
        }
        return sb.toString().trim()
    }

    private fun skip() {
        val depth = parser.depth
        if (parser.isEmptyElementTag) return
        while (true) {
            val e = parser.next()
            if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && parser.depth == depth)) return
        }
    }

    companion object {
        private const val MAX_DESC = 400
        private val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

        /** XMLTV-Zeit wie "20261025210000 +0100"; ohne Zeitzone gilt UTC. */
        fun parseTime(s: String?): Long? {
            if (s.isNullOrBlank()) return null
            val trimmed = s.trim()
            val digits = trimmed.takeWhile { it.isDigit() }
            if (digits.length < 12) return null
            return try {
                val local = LocalDateTime.parse(digits.take(14).padEnd(14, '0'), FORMAT)
                val zone = trimmed.drop(digits.length).trim()
                val offset = if (zone.matches(Regex("[+-]\\d{4}"))) {
                    ZoneOffset.of("${zone.take(3)}:${zone.substring(3)}")
                } else {
                    ZoneOffset.UTC
                }
                local.toInstant(offset).toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }
}
