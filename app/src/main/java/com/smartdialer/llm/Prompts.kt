package com.smartdialer.llm

/**
 * The prompt templates live in versioned resource files (`src/main/resources/prompts/`). Changing a
 * template means adding a new file, bumping the version here, and nothing else; the version is stored with
 * every ranking result, so a result made by an older prompt is refreshed.
 */
object Prompts {
    const val RANKING_VERSION = "ranking-v4"
    const val CLASSIFICATION_VERSION = "classification-v2"

    val ranking: String by lazy { load("prompts/ranking_v4.txt") }
    val classification: String by lazy { load("prompts/classification_v2.txt") }

    private fun load(path: String): String {
        val stream = Prompts::class.java.classLoader?.getResourceAsStream(path)
            ?: error("Missing prompt resource $path")
        return stream.bufferedReader().use { it.readText() }.trim()
    }
}
