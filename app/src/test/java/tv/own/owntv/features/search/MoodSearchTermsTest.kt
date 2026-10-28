package tv.own.owntv.features.search

import org.junit.Assert.assertEquals
import org.junit.Test

class MoodSearchTermsTest {

    @Test
    fun fillerEndingIsCut() = assertEquals(listOf("stoner", "stonermovie"), MoodSearch.keywordTerms("Stonermovie"))

    @Test
    fun germanSeriesEnding() = assertEquals(listOf("zombie", "zombieserie"), MoodSearch.keywordTerms("Zombieserie"))

    @Test
    fun fillerWordAloneIsDropped() = assertEquals(listOf("heist"), MoodSearch.keywordTerms("heist movie"))

    @Test
    fun shortWordsAreIgnored() = assertEquals(listOf("lustige", "kiffer", "kifferfilme", "lustige kifferfilme"), MoodSearch.keywordTerms("lustige Kifferfilme am"))

    @Test
    fun spokenSentenceKeepsTheWish() = assertEquals(
        listOf("gruseligen", "hütte", "wald"),
        MoodSearch.keywordTerms("Ich möchte einen gruseligen Film mit Hütte im Wald schauen"),
    )
}
