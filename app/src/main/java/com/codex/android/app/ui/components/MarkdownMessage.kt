package com.codex.android.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.android.app.core.markdown.MarkdownBlock
import com.codex.android.app.core.markdown.MarkdownParser
import com.codex.android.app.core.markdown.TextKind

@Composable
fun MarkdownMessage(
    text: String,
    onDownloadLinkClick: (String) -> Unit,
    onExternalLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { MarkdownParser().parse(text) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.CodeBlock -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = RoundedCornerShape(18.dp),
                            )
                            .padding(14.dp),
                    ) {
                        block.info?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }

                MarkdownBlock.Divider -> HorizontalDivider()

                is MarkdownBlock.TextBlock -> {
                    val style = when (block.kind) {
                        TextKind.HEADING_1 -> MaterialTheme.typography.headlineSmall
                        TextKind.HEADING_2 -> MaterialTheme.typography.titleLarge
                        TextKind.HEADING_3 -> MaterialTheme.typography.titleMedium
                        TextKind.QUOTE -> MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextKind.BULLET_ITEM -> MaterialTheme.typography.bodyLarge
                        TextKind.PARAGRAPH -> MaterialTheme.typography.bodyLarge
                    }
                    ClickableText(
                        text = block.text,
                        style = style,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { offset ->
                            block.text.getStringAnnotations(start = offset, end = offset)
                                .firstOrNull()
                                ?.let { annotation ->
                                    when (annotation.tag) {
                                        "download" -> onDownloadLinkClick(annotation.item)
                                        "url" -> onExternalLinkClick(annotation.item)
                                    }
                                }
                        },
                    )
                }
            }
        }
        if (blocks.isEmpty()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                overflow = TextOverflow.Visible,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
