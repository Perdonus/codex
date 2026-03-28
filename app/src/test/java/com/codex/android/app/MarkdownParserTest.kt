package com.codex.android.app

import com.codex.android.app.core.markdown.MarkdownBlock
import com.codex.android.app.core.markdown.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    private val parser = MarkdownParser()

    @Test
    fun markdownFileLinksAreMarkedForDownload() {
        val blocks = parser.parse("[artifact](/srv/codex/app-debug.apk)")
        val paragraph = blocks.single() as MarkdownBlock.TextBlock

        val annotations = paragraph.text.getStringAnnotations(start = 0, end = paragraph.text.length)

        assertEquals(1, annotations.size)
        assertEquals("download", annotations.single().tag)
        assertEquals("/srv/codex/app-debug.apk", annotations.single().item)
    }

    @Test
    fun plainHttpsLinksStayExternal() {
        val blocks = parser.parse("Build logs: https://github.com/Perdonus/codex/actions/runs/123")
        val paragraph = blocks.single() as MarkdownBlock.TextBlock

        val annotations = paragraph.text.getStringAnnotations(start = 0, end = paragraph.text.length)

        assertTrue(annotations.any { it.tag == "url" && it.item == "https://github.com/Perdonus/codex/actions/runs/123" })
    }
}
