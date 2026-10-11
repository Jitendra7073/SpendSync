package com.example.spendsync.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SpokenTextTest {
    private val hidden = "amount hidden"

    @Test
    fun markdownIsRemovedAndHiddenAmountsAreSaidAsAPhrase() {
        val text = "You spent **₹4,500** on *food* and ₹300 on coffee."
        assertEquals(
            "You spent amount hidden on food and ₹300 on coffee.",
            toSpeech(text, maskingEnabled = true, unlocked = false, hiddenPhrase = hidden),
        )
        assertEquals(
            "You spent ₹4,500 on food and ₹300 on coffee.",
            toSpeech(text, maskingEnabled = true, unlocked = true, hiddenPhrase = hidden),
        )
    }

    @Test
    fun bulletsTablePipesAndStarsAreNotReadOut() {
        val spoken = toSpeech("- Rent | ₹200\n- Food", maskingEnabled = false, unlocked = false, hiddenPhrase = hidden)
        assertFalse(spoken.contains("•"))
        assertFalse(spoken.contains("|"))
        assertFalse(spoken.contains("★"))
        assertTrue(spoken.contains("Rent"))
    }

    @Test
    fun onlyFinishedSentencesAreReleasedWhileStreaming() {
        val feed = SentenceFeed()
        assertEquals(emptyList<String>(), feed.update("You spent ₹1,250.5").sentences)
        assertEquals(listOf("You spent ₹1,250.50 today."), feed.update("You spent ₹1,250.50 today. Fo").sentences)
        assertEquals(emptyList<String>(), feed.update("You spent ₹1,250.50 today. Food was").sentences)
        assertEquals(listOf("Food was most."), feed.finish("You spent ₹1,250.50 today. Food was most.").sentences)
    }

    @Test
    fun hindiDandaQuestionMarksAndNewlinesEndSentences() {
        val out = SentenceFeed().finish("आज ₹500 खर्च हुए। क्या और देखना है? ठीक है\nबस").sentences
        assertEquals(listOf("आज ₹500 खर्च हुए।", "क्या और देखना है?", "ठीक है", "बस"), out)
    }

    @Test
    fun numberedListMarkersDoNotBecomeTheirOwnSentence() {
        val out = SentenceFeed().finish("1. Rent is the biggest. 2. Food is next.").sentences
        assertEquals(listOf("1. Rent is the biggest.", "2. Food is next."), out)
    }

    @Test
    fun aReplacedReplyIsReportedAsResetAndStartsOver() {
        val feed = SentenceFeed()
        feed.update("First try. Mor")
        val after = feed.update("Second answer. ")
        assertTrue(after.reset)
        assertEquals(listOf("Second answer."), after.sentences)
        assertFalse(feed.update("Second answer. More").reset)
    }

    @Test
    fun voiceLanguageFollowsTheScriptOfTheReply() {
        val hinglish = Locale.forLanguageTag("hi-IN")
        assertEquals("hi", speechLocaleFor("आज आपने ₹500 खर्च किए", Locale.forLanguageTag("en-IN")).language)
        assertEquals("ta", speechLocaleFor("இன்று ₹500 செலவு", hinglish).language)
        // Latin text keeps a Latin-script preference (Spanish stays Spanish)...
        assertEquals("es", speechLocaleFor("Gastaste ₹500 hoy", Locale.forLanguageTag("es-ES")).language)
        // ...but a Hindi listener getting an English reply hears an English voice.
        assertEquals("en", speechLocaleFor("You spent ₹500 today", hinglish).language)
        // Devanagari keeps a Devanagari preference (Marathi is not turned into Hindi).
        assertEquals("mr", speechLocaleFor("आज ५०० रुपये खर्च झाले", Locale.forLanguageTag("mr-IN")).language)
    }
}
