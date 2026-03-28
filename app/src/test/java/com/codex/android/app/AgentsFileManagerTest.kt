package com.codex.android.app

import com.codex.android.app.core.model.ConversationBinding
import com.codex.android.app.core.util.AgentsFileManager
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentsFileManagerTest {
    @Test
    fun mergeAddsManagedBlockWhenFileIsEmpty() {
        val binding = ConversationBinding(
            threadId = "thread-1",
            accountId = "account-1",
            pinnedRepoUrl = "https://github.com/Perdonus/codex",
            pinnedRepoRemotePath = "/srv/codex",
        )

        val merged = AgentsFileManager.merge(existing = "", binding = binding)

        assertTrue(merged.contains("Android Codex Client Instructions"))
        assertTrue(merged.contains("Pinned GitHub repository: https://github.com/Perdonus/codex"))
        assertTrue(merged.contains("Preferred working tree on server: /srv/codex"))
        assertTrue(merged.contains("If the remote Codex session needs OpenAI or ChatGPT authentication"))
    }

    @Test
    fun mergePreservesUserContentBelowManagedBlock() {
        val binding = ConversationBinding(threadId = "thread-1", accountId = "account-1")
        val existing = """
            ## Existing instructions

            Keep answers compact.
        """.trimIndent()

        val merged = AgentsFileManager.merge(existing = existing, binding = binding)

        assertTrue(merged.contains("Android Codex Client Instructions"))
        assertTrue(merged.contains("## Existing instructions"))
    }
}
