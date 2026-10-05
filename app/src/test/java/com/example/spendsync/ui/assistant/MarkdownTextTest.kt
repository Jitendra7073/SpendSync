package com.example.spendsync.ui.assistant

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {
    @Test
    fun boldAndItalicBecomeStylesAndNoStarsRemain() {
        val a = parseInline("You spent **₹4,500** on *food* this month")
        assertEquals("You spent ₹4,500 on food this month", a.text)
        val bold = a.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(1, bold.size)
        assertEquals("₹4,500", a.text.substring(bold[0].start, bold[0].end))
    }

    @Test
    fun anUnfinishedMarkerWhileTypingShowsNoStrayStars() {
        val a = parseInline("Your biggest is **Rent")
        assertEquals("Your biggest is Rent", a.text)
        assertFalse(a.text.contains("*"))
    }

    @Test
    fun codeLinksAndSpacedStarsAreHandled() {
        assertEquals("use code now", parseInline("use `code` now").text)
        assertEquals("open Planify", parseInline("open [Planify](https://x.y)").text)
        assertEquals("5 * 3 = 15", parseInline("5 * 3 = 15").text) // a lone spaced star is just a star
        assertEquals("snake_case_name", parseInline("snake_case_name").text)
    }

    @Test
    fun listsHeadingsQuotesAndParagraphsAreSeparatedIntoBlocks() {
        val md = """
            ## Summary
            Here is where it went:

            - **Food**: ₹4,500
            - Rent: ₹12,000
              - includes maintenance
            1. First
            2) Second

            > careful
            ---
            Done.
        """.trimIndent()
        val b = parseMarkdown(md)
        assertEquals(MdBlock.Heading(2, "Summary"), b[0])
        assertEquals(MdBlock.Paragraph("Here is where it went:"), b[1])
        assertEquals(MdBlock.Bullet(0, "**Food**: ₹4,500"), b[2])
        assertEquals(MdBlock.Bullet(0, "Rent: ₹12,000"), b[3])
        assertEquals(MdBlock.Bullet(1, "includes maintenance"), b[4])
        assertEquals(MdBlock.Numbered("1", 0, "First"), b[5])
        assertEquals(MdBlock.Numbered("2", 0, "Second"), b[6])
        assertEquals(MdBlock.Quote("careful"), b[7])
        assertEquals(MdBlock.Rule, b[8])
        assertEquals(MdBlock.Paragraph("Done."), b[9])
    }

    @Test
    fun codeFencesAndTablesAndPlainTextSurvive() {
        val b = parseMarkdown("```\nline1\nline2\n```\n| a | b |\n|---|---|\n| 1 | 2 |\nplain")
        assertEquals(MdBlock.Code("line1\nline2"), b[0])
        assertEquals(MdBlock.Paragraph("a  ·  b"), b[1])
        assertEquals(MdBlock.Paragraph("1  ·  2"), b[2])
        assertEquals(MdBlock.Paragraph("plain"), b[3])
        assertTrue(parseMarkdown("").isEmpty())
        assertEquals(listOf<MdBlock>(MdBlock.Paragraph("a\nb")), parseMarkdown("a\nb"))
    }
}
