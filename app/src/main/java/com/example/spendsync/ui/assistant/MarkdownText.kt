package com.example.spendsync.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.ui.components.Text

/** The block-level pieces of a model reply. Kept separate from drawing so they can be unit-tested. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val indent: Int, val text: String) : MdBlock
    data class Numbered(val number: String, val indent: Int, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data object Rule : MdBlock
}

private val headingRe = Regex("""^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$""")
private val bulletRe = Regex("""^(\s*)[-*+•]\s+(.*)$""")
private val numberedRe = Regex("""^(\s*)(\d{1,3})[.)]\s+(.*)$""")
private val ruleRe = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
private val tableSepRe = Regex("""^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$""")

/**
 * Reads the Markdown models actually write: headings, bullet and numbered lists (nested by indent), quotes, code
 * fences, rules, tables (shown as plain rows) and paragraphs. It accepts half-finished text, so it is safe to call
 * on a reply that is still being typed out.
 */
fun parseMarkdown(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val para = StringBuilder()
    fun flush() { if (para.isNotEmpty()) { out += MdBlock.Paragraph(para.toString().trimEnd()); para.clear() } }

    var inCode = false
    val code = StringBuilder()
    for (raw in src.replace("\r\n", "\n").split('\n')) {
        val line = raw.trimEnd()
        if (line.trimStart().startsWith("```")) {
            if (inCode) { out += MdBlock.Code(code.toString().trimEnd('\n')); code.clear(); inCode = false }
            else { flush(); inCode = true }
            continue
        }
        if (inCode) { code.append(raw).append('\n'); continue }
        if (line.isBlank()) { flush(); continue }
        if (tableSepRe.matches(line) && line.contains('-') && line.contains('|')) continue // the |---|---| row of a table

        val h = headingRe.matchEntire(line)
        if (h != null) { flush(); out += MdBlock.Heading(h.groupValues[1].length, h.groupValues[2]); continue }
        if (ruleRe.matches(line)) { flush(); out += MdBlock.Rule; continue }
        val bu = bulletRe.matchEntire(line)
        if (bu != null) { flush(); out += MdBlock.Bullet(bu.groupValues[1].length / 2, bu.groupValues[2]); continue }
        val nu = numberedRe.matchEntire(line)
        if (nu != null) { flush(); out += MdBlock.Numbered(nu.groupValues[2], nu.groupValues[1].length / 2, nu.groupValues[3]); continue }
        if (line.trimStart().startsWith(">")) { flush(); out += MdBlock.Quote(line.trimStart().removePrefix(">").trim()); continue }
        if (line.trimStart().startsWith("|")) { flush(); out += MdBlock.Paragraph(line.trim().trim('|').split('|').joinToString("  ·  ") { it.trim() }); continue }
        if (para.isNotEmpty()) para.append('\n')
        para.append(line.trim())
    }
    if (inCode && code.isNotEmpty()) out += MdBlock.Code(code.toString().trimEnd('\n'))
    flush()
    return out
}

/**
 * Bold (**x**), italic (*x*), inline code (`x`), links ([x](url), shown as the label) and ~~strike~~. An unfinished
 * marker while the reply is still being typed styles the rest of the text instead of showing stray stars.
 */
fun parseInline(text: String, linkColor: Color = Color.Unspecified, codeBackground: Color = Color.Unspecified): AnnotatedString = buildAnnotatedString {
    var bold = false
    var italic = false
    var strike = false
    val buf = StringBuilder()
    fun style() = SpanStyle(
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = if (strike) TextDecoration.LineThrough else null,
    )
    fun flush() { if (buf.isNotEmpty()) { withStyle(style()) { append(buf.toString()) }; buf.clear() } }

    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '\\' && i + 1 < text.length && text[i + 1] in "*_`~[]()#>-" -> { buf.append(text[i + 1]); i += 2 }
            text.startsWith("**", i) || (text.startsWith("__", i) && (i == 0 || !text[i - 1].isLetterOrDigit())) -> { flush(); bold = !bold; i += 2 }
            text.startsWith("~~", i) -> { flush(); strike = !strike; i += 2 }
            c == '*' && (italic || (i + 1 < text.length && !text[i + 1].isWhitespace())) -> { flush(); italic = !italic; i += 1 }
            c == '`' -> {
                val end = text.indexOf('`', i + 1)
                flush()
                val body = if (end < 0) text.substring(i + 1) else text.substring(i + 1, end)
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, fontWeight = if (bold) FontWeight.Bold else null)) { append(body) }
                i = if (end < 0) text.length else end + 1
            }
            c == '[' -> {
                val close = text.indexOf("](", i + 1)
                val paren = if (close > 0) text.indexOf(')', close + 2) else -1
                if (close > 0 && paren > 0) {
                    flush()
                    withStyle(style().merge(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))) { append(text.substring(i + 1, close)) }
                    i = paren + 1
                } else { buf.append(c); i += 1 }
            }
            else -> { buf.append(c); i += 1 }
        }
    }
    flush()
}

/** Draws a model reply with real formatting instead of raw `**` and `-` characters. */
@Composable
fun MarkdownText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 22.sp,
) {
    val scheme = MaterialTheme.colorScheme
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { b ->
            when (b) {
                is MdBlock.Heading -> Text(
                    parseInline(b.text, scheme.primary, scheme.surfaceVariant),
                    color = color, fontWeight = FontWeight.Bold,
                    fontSize = when (b.level) { 1 -> fontSize * 1.2f; 2 -> fontSize * 1.1f; else -> fontSize },
                    lineHeight = lineHeight, modifier = Modifier.padding(top = 4.dp),
                )
                is MdBlock.Bullet -> Row(Modifier.padding(start = (b.indent * 14).dp)) {
                    Text(if (b.indent > 0) "◦" else "•", color = scheme.primary, fontSize = fontSize, lineHeight = lineHeight, modifier = Modifier.width(18.dp))
                    Text(parseInline(b.text, scheme.primary, scheme.surfaceVariant), color = color, fontSize = fontSize, lineHeight = lineHeight, modifier = Modifier.weight(1f))
                }
                is MdBlock.Numbered -> Row(Modifier.padding(start = (b.indent * 14).dp)) {
                    Text("${b.number}.", color = scheme.primary, fontWeight = FontWeight.SemiBold, fontSize = fontSize, lineHeight = lineHeight, modifier = Modifier.width(26.dp))
                    Text(parseInline(b.text, scheme.primary, scheme.surfaceVariant), color = color, fontSize = fontSize, lineHeight = lineHeight, modifier = Modifier.weight(1f))
                }
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(scheme.primary.copy(alpha = 0.5f)))
                    Text(parseInline(b.text, scheme.primary, scheme.surfaceVariant), color = scheme.onSurfaceVariant, fontSize = fontSize, lineHeight = lineHeight, modifier = Modifier.padding(start = 10.dp))
                }
                is MdBlock.Code -> Text(
                    b.text, color = color, fontFamily = FontFamily.Monospace, fontSize = fontSize * 0.9f,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(scheme.surfaceVariant).padding(10.dp),
                )
                MdBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
                is MdBlock.Paragraph -> Text(parseInline(b.text, scheme.primary, scheme.surfaceVariant), color = color, fontSize = fontSize, lineHeight = lineHeight)
            }
        }
    }
}

/** The reply as clean plain text for sharing: lists keep their bullets and numbers, no Markdown marks remain. */
fun markdownToPlain(src: String): String {
    fun inline(t: String) = parseInline(t).text
    fun isList(b: MdBlock) = b is MdBlock.Bullet || b is MdBlock.Numbered
    val blocks = parseMarkdown(src)
    val sb = StringBuilder()
    blocks.forEachIndexed { i, b ->
        if (i > 0) sb.append(if (isList(b) && isList(blocks[i - 1])) "\n" else "\n\n")
        sb.append(
            when (b) {
                is MdBlock.Heading -> if (b.level == 1) inline(b.text).uppercase() else inline(b.text)
                is MdBlock.Bullet -> "  ".repeat(b.indent) + (if (b.indent > 0) "◦ " else "• ") + inline(b.text)
                is MdBlock.Numbered -> "  ".repeat(b.indent) + b.number + ". " + inline(b.text)
                is MdBlock.Quote -> "> " + inline(b.text)
                is MdBlock.Code -> b.text
                is MdBlock.Paragraph -> inline(b.text)
                MdBlock.Rule -> "—"
            },
        )
    }
    return sb.toString()
}
