package com.openloud.domain.chapter

data class ChapterBreak(
    val startOffset: Int,
    val title: String,
    val score: Int
)

class ChapterDetector {

    private val patterns = listOf(
        // "Chapter 1", "CHAPTER ONE", "Chapter I"
        Regex("(?m)^\\s*(CHAPTER|Chapter)\\s+([\\dIVXLCDM]+)[.:]?\\s*(.*)$") to 10,
        // "Part 1", "PART ONE"
        Regex("(?m)^\\s*(PART|Part)\\s+([\\dIVXLCDM]+)[.:]?\\s*(.*)$") to 9,
        // Numbered sections: "1.", "1.1", "I."
        Regex("(?m)^\\s*([\\dIVX]+)\\.\\s+([A-Z][^\\n]{5,50})$") to 7,
        // ALL CAPS TITLE (likely chapter heading) - at least 10 chars
        Regex("(?m)^\\s*([A-Z][A-Z\\s]{9,50})\\s*$") to 6,
        // "* * *" or "---" section breaks
        Regex("(?m)^\\s*([*]{3,}|[-]{3,}|[_]{3,})\\s*$") to 5,
        // Prologue, Epilogue, Introduction
        Regex("(?m)^\\s*(PROLOGUE|Prologue|EPILOGUE|Epilogue|INTRODUCTION|Introduction)\\s*$") to 8
    )

    fun detectChapters(text: String, minChapterLength: Int = 500): List<ChapterBreak> {
        val candidates = mutableListOf<ChapterBreak>()

        // Find all pattern matches
        for ((pattern, score) in patterns) {
            val matches = pattern.findAll(text)
            for (match in matches) {
                val offset = match.range.first
                val title = extractTitle(match)
                candidates.add(ChapterBreak(offset, title, score))
            }
        }

        // Sort by offset
        val sorted = candidates.sortedBy { it.startOffset }

        // Filter out candidates that are too close together (likely false positives)
        val filtered = mutableListOf<ChapterBreak>()
        var lastOffset = -minChapterLength

        for (candidate in sorted) {
            if (candidate.startOffset - lastOffset >= minChapterLength) {
                filtered.add(candidate)
                lastOffset = candidate.startOffset
            }
        }

        // If no chapters found, create a single chapter
        if (filtered.isEmpty()) {
            filtered.add(ChapterBreak(0, "Chapter 1", 10))
        }

        return filtered
    }

    private fun extractTitle(match: MatchResult): String {
        // Try to extract meaningful title from match groups
        return when {
            match.groupValues.size > 3 && match.groupValues[3].isNotBlank() -> {
                "${match.groupValues[1]} ${match.groupValues[2]}: ${match.groupValues[3].trim()}"
            }
            match.groupValues.size > 2 && match.groupValues[2].isNotBlank() -> {
                "${match.groupValues[1]} ${match.groupValues[2]}"
            }
            else -> {
                match.value.trim().take(50)
            }
        }
    }

    fun createChapters(text: String, breaks: List<ChapterBreak>): List<Pair<String, String>> {
        val chapters = mutableListOf<Pair<String, String>>()

        for (i in breaks.indices) {
            val start = breaks[i].startOffset
            val end = if (i < breaks.size - 1) breaks[i + 1].startOffset else text.length
            val title = breaks[i].title
            val content = text.substring(start, end).trim()
            chapters.add(title to content)
        }

        return chapters
    }
}
