package com.codex.android.app.core.util

import com.codex.android.app.core.model.ConversationBinding

object AgentsFileManager {
    private const val StartMarker = "<!-- CODEX_ANDROID_MANAGED_START -->"
    private const val EndMarker = "<!-- CODEX_ANDROID_MANAGED_END -->"

    fun merge(existing: String?, binding: ConversationBinding): String {
        val managedBlock = buildManagedBlock(binding)
        val body = existing.orEmpty()
        return if (body.contains(StartMarker) && body.contains(EndMarker)) {
            body.replace(Regex("$StartMarker[\\s\\S]*?$EndMarker"), managedBlock)
        } else if (body.isBlank()) {
            managedBlock
        } else {
            managedBlock + "\n\n" + body.trimStart()
        }
    }

    fun buildManagedBlock(binding: ConversationBinding): String {
        val pinnedRepoLine = binding.pinnedRepoUrl?.let { "- Pinned GitHub repository: $it" }
        val pinnedPathLine = binding.pinnedRepoRemotePath?.let { "- Preferred working tree on server: $it" }
        val lines = buildList {
            add(StartMarker)
            add("# Android Codex Client Instructions")
            add("")
            add("- You are being used from a native Android client.")
            add("- The execution environment is a remote Linux server reached over SSH.")
            add("- Do not attempt to build Android artifacts on this Linux server.")
            add("- If a build or release is needed, use GitHub Actions and mention that requirement explicitly.")
            add("- If the remote Codex session needs OpenAI or ChatGPT authentication, tell the user to complete sign-in from the Android app settings instead of asking for raw tokens.")
            add("- Prefer Markdown output with fenced code blocks, inline code and clear file paths.")
            add("- If you reference a downloadable file that already exists on this server, use a Markdown link or a plain path so the Android client can fetch it over SFTP.")
            pinnedRepoLine?.let(::add)
            pinnedPathLine?.let(::add)
            add("- Keep responses compatible with a mobile chat UI.")
            add(EndMarker)
        }
        return lines.joinToString(separator = "\n")
    }
}
