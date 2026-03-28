package com.codex.android.app.core.markdown

import androidx.compose.ui.text.AnnotatedString

sealed interface MarkdownBlock {
    data class TextBlock(
        val kind: TextKind,
        val text: AnnotatedString,
    ) : MarkdownBlock

    data class CodeBlock(
        val info: String?,
        val code: String,
    ) : MarkdownBlock

    data object Divider : MarkdownBlock
}

enum class TextKind {
    HEADING_1,
    HEADING_2,
    HEADING_3,
    PARAGRAPH,
    QUOTE,
    BULLET_ITEM,
}

