package io.github.jonathanxoxox.tvkanaele.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.File
import java.nio.file.Files

class CoreTest {
    private val m3u = """
        #EXTM3U url-tvg="http://epg.example/guide.xml.gz"
        #EXTINF:-1 tvg-id="thezone.es" tvg-name="ES: The Zone HD" tvg-logo="http://logo/zone.png" group-title="Spanien | Sport",ES: The Zone HD
        http://iptv.example/live/u/p/1.ts
        #EXTINF:-1 tvg-id="thezone.es" tvg-logo="http://logo/zone.png" group-title="Spanien | Sport",ES: The Zone FHD
        http://iptv.example/live/u/p/2.ts
        #EXTINF:-1 group-title="Deutschland",DE: Sky Sport Bundesliga 1
        #EXTVLCOPT:http-user-agent=TestAgent
        http://iptv.example/live/u/p/3.ts
        #EXTINF:-1 tvg-id="kika.de" group-title="Deutschland",DE: KiKA
        http://iptv.example/live/u/p/4.ts
    """.trimIndent()

    private val xmltv = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE tv SYSTEM "xmltv.dtd">
        <tv generator-info-name="test">
          <channel id="thezone.es"><display-name>The Zone</display-name></channel>
          <channel id="sky.bl1"><display-name lang="de">Sky Sport Bundesliga 1 HD</display-name></channel>
          <channel id="kika.de"><display-name>KiKA</display-name></channel>
          <programme start="20261025200000 +0100" stop="20261025230000 +0100" channel="thezone.es">
            <title lang="es">LaLiga: Real Madrid - FC Barcelona (Directo)</title>
            <desc>El Clásico en el Bernabéu &amp; más.</desc>
            <category>Fútbol</category>
          </programme>
          <programme start="20261025150000 +0100" stop="20261025170000 +0100" channel="thezone.es">
            <title>Highlights: Real Madrid - Getafe</title>
          </programme>
          <programme start="20261025203000 +0100" stop="20261025223000 +0100" channel="sky.bl1">
            <title>Live: Mainz 05 - VfL Bochum</title>
            <category>Sport</category><category>Fußball</category>
          </programme>
          <programme start="20261025200000 +0100" stop="20261025210000 +0100" channel="kika.de">
            <title>Die Sendung mit der Maus</title>
          </programme>
          <programme start="20261101200000 +0100" stop="20261101210000 +0100" channel="kika.de">
            <title>Außerhalb des Zeitfensters</title>
          </programme>
        </tv>
    """.trimIndent()

    // 25.10.2026, 20:15 Uhr MEZ (Sommerzeit endet am 25.10., danach +01:00)
    private val now = XmltvParser.parseTime("20261025201500 +0100")!!

    private val interests = Interests(
        teams = listOf("Real Madrid", "Barça"),
        leagues = listOf("Bundesliga"),
        keywords = listOf("Clasico"),
        excludes = listOf("Highlights", "Wdh"),
        preferredChannels = listOf("FHD"),
    )

    @Test
    fun parsesM3u() {
        val playlist = M3uParser.parse(m3u.lineSequence())
        assertEquals(listOf("http://epg.example/guide.xml.gz"), playlist.epgUrls)
        assertEquals(4, playlist.channels.size)
        val zone = playlist.channels[0]
        assertEquals("ES: The Zone HD", zone.name)
        assertEquals("thezone.es", zone.tvgId)
        assertEquals("Spanien | Sport", zone.group)
        assertEquals("http://logo/zone.png", zone.logo)
        assertEquals("TestAgent", playlist.channels[2].userAgent)
        assertEquals(playlist.channels.size, playlist.channels.map { it.key }.toSet().size)
    }

    @Test
    fun parsesXtream() {
        val account = XtreamAccount("iptv.example:8080/", "user", "p@ss")
        val streams = """[{"num":1,"name":"ES: The Zone","stream_id":17,"stream_icon":"http://logo/z.png","epg_channel_id":"thezone.es","category_id":"5"},
            {"num":2,"name":"Ohne EPG","stream_id":"18","stream_icon":"","epg_channel_id":null,"category_id":"6"}]"""
        val cats = """[{"category_id":"5","category_name":"Spanien Sport"}]"""
        val channels = XtreamParser.parse(account, streams, cats)
        assertEquals(2, channels.size)
        assertEquals("x:17", channels[0].key)
        assertEquals("http://iptv.example:8080/live/user/p@ss/17.ts", channels[0].streamUrl)
        assertEquals("Spanien Sport", channels[0].group)
        assertNull(channels[1].tvgId)
        assertNull(channels[1].logo)
        assertTrue(account.epgUrl().endsWith("xmltv.php?username=user&password=p%40ss"))
    }

    @Test
    fun parsesXmltvWithinWindow() {
        val result = XmltvParser(KXmlParser()).parse(
            xmltv.byteInputStream(), now - 6 * HOUR, now + 24 * HOUR,
        ) { true }
        assertEquals(listOf("Sky Sport Bundesliga 1 HD"), result.channelNames["sky.bl1"])
        assertEquals(4, result.programmes.size)
        val clasico = result.programmes.first()
        assertEquals("El Clásico en el Bernabéu & más.", clasico.description)
        assertEquals(listOf("Fútbol"), clasico.categories)
        assertTrue(clasico.isRunning(now))
    }

    @Test
    fun parsesXmltvTimes() {
        assertEquals(0L, XmltvParser.parseTime("19700101000000 +0000"))
        assertEquals(0L, XmltvParser.parseTime("19700101010000 +0100"))
        assertEquals(0L, XmltvParser.parseTime("197001010000"))
        assertNull(XmltvParser.parseTime("kaputt"))
    }

    @Test
    fun matcherFindsTeamsAliasesAndExcludes() {
        val matcher = Matcher(interests)
        val clasico = Programme("x", 0, 1, "LaLiga: Real Madrid - FC Barcelona (Directo)", description = "El Clásico")
        val hit = matcher.match(clasico)!!
        assertTrue(hit.strong)
        assertTrue(hit.reasons.containsAll(listOf("Real Madrid", "Barça", "Clasico")))
        assertTrue(hit.score >= Matcher.TEAM * 2 + Matcher.DERBY)

        assertNull(matcher.match(Programme("x", 0, 1, "Highlights: Real Madrid - Getafe")))
        assertNull(matcher.match(Programme("x", 0, 1, "Die Sendung mit der Maus")))
        // "Atlético Madrid" darf nicht als Real Madrid zählen
        assertNull(Matcher(Interests(teams = listOf("Real Madrid"), includeAllFootball = false))
            .match(Programme("x", 0, 1, "Atlético Madrid - Sevilla")))

        val other = matcher.match(Programme("x", 0, 1, "Live: Mainz 05 - VfL Bochum", categories = listOf("Fußball")))!!
        assertFalse(other.strong)
        assertEquals(listOf("Fußball"), other.reasons)
    }

    @Test
    fun suggestionsPickBestChannelAndRank() {
        val playlist = M3uParser.parse(m3u.lineSequence())
        val parsed = XmltvParser(KXmlParser()).parse(xmltv.byteInputStream(), now - 6 * HOUR, now + 24 * HOUR) { true }
        val map = ChannelMapper.map(playlist.channels, parsed.channelNames)
        assertEquals("sky.bl1", map[playlist.channels[2].key]) // Zuordnung über den Namen
        assertEquals("thezone.es", map[playlist.channels[1].key])

        val guide = Guide(playlist.channels, map, parsed.programmes)
        val list = guide.suggestions(Matcher(interests), emptySet(), interests.preferredChannels, now, 3 * HOUR, 10)
        assertEquals(2, list.size)
        val top = list[0]
        assertEquals("ES: The Zone FHD", top.channel.name) // bevorzugt "FHD"
        assertEquals(1, top.alternatives)
        assertTrue(top.programme.title.contains("Real Madrid"))
        assertEquals("DE: Sky Sport Bundesliga 1", list[1].channel.name)

        val favs = guide.favorites(listOf(playlist.channels[3].key, "gibt-es-nicht"), now)
        assertEquals(1, favs.size)
        assertEquals("Die Sendung mit der Maus", favs[0].now?.title)
    }

    @Test
    fun storeRoundTrip() {
        val dir: File = Files.createTempDirectory("guide").toFile()
        val playlist = M3uParser.parse(m3u.lineSequence())
        val p = Programme("thezone.es", 1, 2, "Titel", "Sub", null, listOf("Fußball"), "http://i")
        val store = GuideStore(dir)
        store.saveChannels(playlist.channels)
        store.saveGuide(mapOf(playlist.channels[0].key to "thezone.es"), listOf(p))
        val guide = store.load()
        assertEquals(playlist.channels, guide.channels)
        assertEquals(listOf(p), guide.programmes)
        assertEquals("thezone.es", guide.epgMap[playlist.channels[0].key])
        assertNotNull(guide.channelsByKey[playlist.channels[0].key])
        dir.deleteRecursively()
    }

    @Test
    fun foldsText() {
        assertEquals("barca", Text.fold("Barça"))
        assertEquals("1 fc koln", Text.fold("1. FC Köln"))
        assertEquals("the zone", Text.channelBaseName("ES: The Zone FHD"))
        assertEquals("sky sport bundesliga 1", Text.channelBaseName("|DE| Sky Sport Bundesliga 1 HD"))
    }

    companion object {
        private const val HOUR = 3_600_000L
    }
}
