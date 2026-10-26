package tv.own.owntv.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import tv.own.owntv.core.epg.EpgMatcher

/** The football group finds every variant of a channel by its family name (LauncherRows). */
class ChannelFamilyTest {

    private fun family(name: String) = EpgMatcher.normalizeForEpg(name)

    @Test
    fun variantsShareAFamily() {
        val base = family("Sky Sport Bundesliga 1")
        listOf(
            "DE: Sky Sport Bundesliga 1 HD",
            "DE| Sky Sport Bundesliga 1 FHD",
            "Sky Sport Bundesliga 1 UHD",
            "Sky Sport Bundesliga 1 (Backup)",
            "AT: Sky Sport Bundesliga 1 ᴴᴰ",
            "SKY SPORT BUNDESLIGA 1 1080p",
        ).forEach { assertEquals(it, base, family(it)) }
    }

    @Test
    fun otherChannelsStayApart() {
        assertNotEquals(family("Sky Sport Bundesliga 1 HD"), family("Sky Sport Bundesliga 2 HD"))
        assertNotEquals(family("Sky Sport Bundesliga 1"), family("Sky Sport Bundesliga"))
        assertNotEquals(family("DAZN 1 HD"), family("DAZN 2 HD"))
    }
}
