package io.github.jonathanxoxox.tvkanaele.core

import java.text.Normalizer

/** Textvergleich ohne Groß-/Kleinschreibung, Akzente und Satzzeichen ("Barça" == "barca"). */
object Text {
    private val MARKS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    fun fold(s: String): String {
        val lower = s.lowercase()
            .replace("ß", "ss")
            .replace("ø", "o")
            .replace("æ", "ae")
            .replace("ł", "l")
        val noAccents = MARKS.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
        return NON_ALNUM.replace(noAccents, " ").trim()
    }

    /** true, wenn [phrase] (bereits gefaltet) als ganze Wörter in [folded] vorkommt. */
    fun containsPhrase(folded: String, phrase: String): Boolean =
        phrase.isNotEmpty() && " $folded ".contains(" $phrase ")

    /** Liste aus einem Eingabefeld: getrennt durch Komma, Semikolon oder Zeilenumbruch. */
    fun splitList(s: String?): List<String> =
        s.orEmpty().split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    private val COUNTRY_PREFIX = Regex("^\\s*[\\[(|]?\\s*[A-Za-z]{2,3}\\s*[\\])|:]\\s*[|:-]?\\s*")
    private val QUALITY = setOf(
        "hd", "fhd", "uhd", "sd", "4k", "8k", "hevc", "h265", "h264", "1080p", "720p", "50fps", "60fps",
        "raw", "backup", "plus", "hq", "lq", "vip",
    )

    /** Sendername ohne Länderpräfix und Qualitätsangaben, für den Abgleich mit EPG-Namen. */
    fun channelBaseName(name: String): String {
        val withoutPrefix = COUNTRY_PREFIX.replace(name, "").ifBlank { name }
        return fold(withoutPrefix).split(' ').filter { it !in QUALITY }.joinToString(" ")
    }
}
