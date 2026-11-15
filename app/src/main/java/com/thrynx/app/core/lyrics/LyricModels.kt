package com.thrynx.app.core.lyrics

data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList()
) {
    val isBreak: Boolean get() = text.isBlank()
}
