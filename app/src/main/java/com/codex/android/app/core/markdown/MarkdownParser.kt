package com.codex.android.app.core.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

class MarkdownParser {
    private val parser = Parser.builder().build()
    private val urlRegex = Regex("""https?://[^\s)>\]]+""")
    private val pathRegex = Regex("""(?:(?:\.\.?)?/|~|/)[A-Za-z0-9._/\-]+""")

    fun parse(markdown: String): List<MarkdownBlock> {
        if (markdown.isBlank()) return emptyList()
        val document = parser.parse(markdown)
        val result = mutableListOf<MarkdownBlock>()
        var child = document.firstChild
        while (child != null) {
            appendBlock(result, child)
            child = child.next
        }
        return result
    }

    private fun appendBlock(target: MutableList<MarkdownBlock>, node: Node) {
        when (node) {
            is Heading -> target += MarkdownBlock.TextBlock(
                kind = when (node.level) {
                    1 -> TextKind.HEADING_1
                    2 -> TextKind.HEADING_2
                    else -> TextKind.HEADING_3
                },
                text = renderInline(node),
            )

            is Paragraph -> target += MarkdownBlock.TextBlock(
                kind = TextKind.PARAGRAPH,
                text = renderInline(node),
            )

            is BlockQuote -> target += MarkdownBlock.TextBlock(
                kind = TextKind.QUOTE,
                text = renderInline(node),
            )

            is BulletList -> {
                var item = node.firstChild
                while (item != null) {
                    if (item is ListItem) {
                        target += MarkdownBlock.TextBlock(
                            kind = TextKind.BULLET_ITEM,
                            text = buildAnnotatedString {
                                append("- ")
                                append(renderInline(item))
                            },
                        )
                    }
                    item = item.next
                }
            }

            is FencedCodeBlock -> target += MarkdownBlock.CodeBlock(
                info = node.info.takeIf { it.isNotBlank() },
                code = node.literal.trimEnd(),
            )

            is ThematicBreak -> target += MarkdownBlock.Divider

            else -> {
                var child = node.firstChild
                while (child != null) {
                    appendBlock(target, child)
                    child = child.next
                }
            }
        }
    }

    private fun renderInline(node: Node): AnnotatedString {
        return buildAnnotatedString {
            appendInline(this, node.firstChild)
        }
    }

    private fun appendInline(builder: AnnotatedString.Builder, node: Node?) {
        var current = node
        while (current != null) {
            when (current) {
                is Text -> appendTextWithLinks(builder, current.literal)
                is SoftLineBreak -> builder.append('\n')
                is Code -> {
                    builder.pushStyle(
                        SpanStyle(
                            background = Color(0x1AE0FBFC),
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    builder.append(current.literal)
                    builder.pop()
                }

                is Emphasis -> {
                    builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    appendInline(builder, current.firstChild)
                    builder.pop()
                }

                is StrongEmphasis -> {
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                    appendInline(builder, current.firstChild)
                    builder.pop()
                }

                is Link -> {
                    builder.pushStringAnnotation(
                        tag = annotationTagForDestination(current.destination),
                        annotation = current.destination,
                    )
                    builder.pushStyle(
                        SpanStyle(
                            color = Color(0xFF66C7F4),
                            textDecoration = TextDecoration.Underline,
                        ),
                    )
                    appendInline(builder, current.firstChild)
                    builder.pop()
                    builder.pop()
                }

                else -> appendInline(builder, current.firstChild)
            }
            current = current.next
        }
    }

    private fun appendTextWithLinks(builder: AnnotatedString.Builder, source: String) {
        val matches = buildList {
            urlRegex.findAll(source).forEach { add(LinkMatch(tag = "url", value = it.value, start = it.range.first, endExclusive = it.range.last + 1)) }
            pathRegex.findAll(source).forEach { add(LinkMatch(tag = "download", value = it.value, start = it.range.first, endExclusive = it.range.last + 1)) }
        }.sortedBy { it.start }

        var cursor = 0
        matches.forEach { match ->
            if (match.start < cursor) return@forEach
            if (match.start > cursor) {
                builder.append(source.substring(cursor, match.start))
            }
            builder.pushStringAnnotation(tag = match.tag, annotation = match.value)
            builder.pushStyle(
                SpanStyle(
                    color = Color(0xFF66C7F4),
                    textDecoration = TextDecoration.Underline,
                ),
            )
            builder.append(match.value)
            builder.pop()
            builder.pop()
            cursor = match.endExclusive
        }
        if (cursor < source.length) {
            builder.append(source.substring(cursor))
        }
    }

    private fun annotationTagForDestination(destination: String): String {
        return if (destination == "~" || pathRegex.matches(destination) || destination.startsWith("~/")) "download" else "url"
    }
}

private data class LinkMatch(
    val tag: String,
    val value: String,
    val start: Int,
    val endExclusive: Int,
)
