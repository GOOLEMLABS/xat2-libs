package com.goolem.kmp.markdown

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Minimal Markdown renderer backed entirely by Compose [AnnotatedString].
 *
 * No external dependencies — pure Compose Multiplatform.
 *
 * Supported syntax:
 *   - `#` / `##` / `###` headers
 *   - `**bold**` and `__bold__`
 *   - `*italic*` and `_italic_`
 *   - `` `inline code` ``
 *   - `- ` / `* ` bullet lists
 *   - blank-line paragraph breaks
 *
 * @param content   Raw Markdown text.
 * @param modifier  Modifier applied to the [Text] composable.
 * @param color     Text color. Defaults to [LocalContentColor] (inherits from parent theme).
 * @param codeColor Inline-code text color. Defaults to [MaterialTheme.colorScheme.outline].
 */
/** Maximum input length to prevent excessive CPU usage from regex parsing. */
public var maxInputLength: Int = 50_000

@Composable
public fun SimpleMarkdown(
    content: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    codeColor: Color = MaterialTheme.colorScheme.outline,
) {
    val safe = if (content.length > maxInputLength) content.take(maxInputLength) else content
    val annotated = remember(safe, color, codeColor) {
        buildMarkdown(safe, color, codeColor)
    }
    Text(
        text     = annotated,
        style    = MaterialTheme.typography.bodyMedium,
        color    = color,
        modifier = modifier,
    )
}

private fun buildMarkdown(raw: String, textColor: Color, codeColor: Color): AnnotatedString =
    buildAnnotatedString {
        val lines = raw.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            if (line.isBlank()) {
                append("\n")
                i++
                continue
            }

            // Headers
            val headerMatch = Regex("^(#{1,3})\\s+(.*)").matchEntire(line)
            if (headerMatch != null) {
                val level = headerMatch.groupValues[1].length
                val text  = headerMatch.groupValues[2]
                val weight   = if (level == 1) FontWeight.ExtraBold else FontWeight.Bold
                val fontSize = when (level) { 1 -> 20.sp; 2 -> 17.sp; else -> 15.sp }
                withStyle(SpanStyle(fontWeight = weight, fontSize = fontSize, color = textColor)) {
                    appendInline(text, textColor, codeColor)
                }
                append("\n")
                i++
                continue
            }

            // Bullet list
            val bulletMatch = Regex("^[-*]\\s+(.*)").matchEntire(line)
            if (bulletMatch != null) {
                append("• ")
                appendInline(bulletMatch.groupValues[1], textColor, codeColor)
                append("\n")
                i++
                continue
            }

            // Regular paragraph line
            appendInline(line, textColor, codeColor)
            val nextBlank = i + 1 >= lines.size || lines[i + 1].isBlank()
            append(if (nextBlank) "\n" else " ")
            i++
        }
    }

private fun AnnotatedString.Builder.appendInline(text: String, textColor: Color, codeColor: Color) {
    val pattern = Regex("""\*\*([^*]+)\*\*|__([^_]+)__|`([^`]+)`|\*([^*]+)\*|_([^_]+)_""")
    var last = 0
    for (match in pattern.findAll(text)) {
        if (match.range.first > last) append(text.substring(last, match.range.first))
        when {
            match.groupValues[1].isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
            match.groupValues[2].isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[2]) }
            match.groupValues[3].isNotEmpty() ->
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor)) { append(match.groupValues[3]) }
            match.groupValues[4].isNotEmpty() ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[4]) }
            match.groupValues[5].isNotEmpty() ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[5]) }
        }
        last = match.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}
