package com.example.spendsync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the "whole app switches language" promise: every language file must define exactly the
 * strings the English file defines, with the same %1$s-style placeholders, so no screen can fall
 * back to English (or crash on a format mismatch) after a language switch.
 */
class I18nResourcesTest {
    private val resDir = listOf(File("src/main/res"), File("app/src/main/res")).first { it.exists() }
    private val entry = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val placeholder = Regex("""%\d\$[sd]""")

    private fun load(folder: String): Map<String, String> =
        File(resDir, folder).listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }!!
            .flatMap { f -> entry.findAll(f.readText()).map { it.groupValues[1] to it.groupValues[2] }.toList() }
            .toMap()

    // The brand name is intentionally not translated.
    private val english = load("values") - "app_name"
    private val languages = listOf("values-hi", "values-es", "values-fr", "values-de")

    @Test
    fun everyLanguageHasEveryString() {
        languages.forEach { lang ->
            val other = load(lang)
            val missing = english.keys - other.keys
            assertTrue("$lang is missing: ${missing.take(10)}", missing.isEmpty())
            val extra = other.keys - english.keys
            assertTrue("$lang has strings English doesn't: ${extra.take(10)}", extra.isEmpty())
        }
    }

    @Test
    fun placeholdersMatchEnglish() {
        languages.forEach { lang ->
            val other = load(lang)
            english.forEach { (key, text) ->
                assertEquals(
                    "$lang/$key placeholders",
                    placeholder.findAll(text).map { it.value }.sorted().toList(),
                    placeholder.findAll(other.getValue(key)).map { it.value }.sorted().toList(),
                )
            }
        }
    }

    @Test
    fun translatedStringsDifferFromEnglishWhereTheyShould() {
        // Spot check: a handful of user-visible labels must really be translated.
        val other = load("values-hi")
        listOf("settings", "cancel", "delete", "language").forEach { key ->
            assertTrue("$key not translated to Hindi", other.getValue(key) != english.getValue(key))
        }
    }
}
