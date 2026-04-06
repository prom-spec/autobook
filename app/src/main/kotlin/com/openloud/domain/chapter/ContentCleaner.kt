package com.openloud.domain.chapter

class ContentCleaner {

    fun cleanText(text: String): String {
        var cleaned = text

        // Strip HTML tags (MOBI parser returns raw HTML)
        cleaned = stripHtml(cleaned)

        // Remove page numbers (standalone numbers on their own line)
        cleaned = removePageNumbers(cleaned)

        // Fix hyphenation (word- \n word -> word-word or just word)
        cleaned = fixHyphenation(cleaned)

        // Remove repeated headers/footers
        cleaned = removeRepeatedText(cleaned)

        // Normalize whitespace
        cleaned = normalizeWhitespace(cleaned)

        return cleaned
    }

    private fun stripHtml(text: String): String {
        var cleaned = text

        // Convert block-level tags to newlines for paragraph breaks
        cleaned = cleaned.replace(Regex("<\\s*(br|p|div|h[1-6]|li|tr|blockquote)[^>]*/?>", RegexOption.IGNORE_CASE), "\n")
        // Remove closing block tags
        cleaned = cleaned.replace(Regex("</\\s*(p|div|h[1-6]|li|tr|blockquote|ul|ol|table)\\s*>", RegexOption.IGNORE_CASE), "\n")

        // Decode common HTML entities
        cleaned = cleaned
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace(Regex("&#(\\d+);")) { match ->
                val code = match.groupValues[1].toIntOrNull()
                if (code != null && code in 32..126) code.toChar().toString() else ""
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                val code = match.groupValues[1].toIntOrNull(16)
                if (code != null && code in 32..126) code.toChar().toString() else ""
            }

        // Strip all remaining HTML tags
        cleaned = cleaned.replace(Regex("<[^>]*>"), "")

        return cleaned
    }

    private fun removePageNumbers(text: String): String {
        // Remove standalone numbers (likely page numbers)
        // Match: newline, optional whitespace, 1-4 digits, optional whitespace, newline
        return text.replace(Regex("(?m)^\\s*\\d{1,4}\\s*$"), "")
    }

    private fun fixHyphenation(text: String): String {
        // Fix words broken across lines: "recon-\nstruct" -> "reconstruct"
        // But keep intentional hyphens: "state-of-the-art" stays as is
        return text.replace(Regex("(\\w+)-\\s*\n\\s*(\\w+)")) { match ->
            val word1 = match.groupValues[1]
            val word2 = match.groupValues[2]
            // If second word starts with lowercase, it's likely a broken word
            if (word2[0].isLowerCase()) {
                "$word1$word2"
            } else {
                // Keep the hyphen if second word is capitalized
                "$word1-$word2"
            }
        }
    }

    private fun removeRepeatedText(text: String): String {
        // This is a simple heuristic: if a line appears many times, it's likely a header/footer
        val lines = text.lines()
        val lineFrequency = lines.groupingBy { it.trim() }
            .eachCount()
            .filter { it.value > 3 && it.key.length > 5 } // Repeated more than 3 times

        var cleaned = text
        for ((repeatedLine, _) in lineFrequency) {
            if (repeatedLine.isNotBlank()) {
                // Remove the repeated line but keep one instance if it's meaningful
                cleaned = cleaned.replace(Regex("(?m)^\\s*${Regex.escape(repeatedLine)}\\s*$"), "")
            }
        }

        return cleaned
    }

    private fun normalizeWhitespace(text: String): String {
        var normalized = text

        // Replace multiple spaces with single space
        normalized = normalized.replace(Regex(" {2,}"), " ")

        // Replace multiple newlines with max 2 newlines (paragraph break)
        normalized = normalized.replace(Regex("\n{3,}"), "\n\n")

        // Remove trailing/leading whitespace from each line
        normalized = normalized.lines()
            .joinToString("\n") { it.trim() }

        return normalized.trim()
    }

    /**
     * Marker inserted between paragraphs so the TTS engine can add a pause.
     */
    companion object {
        const val PARAGRAPH_BREAK = "\u0000PARA\u0000"
    }

    fun splitIntoSentences(text: String): List<String> {
        val chunks = mutableListOf<String>()

        // First: aggressively merge PDF line-wrapping artifacts.
        // PDF extractors insert \n at every visual line break (~60-80 chars).
        // Real paragraph breaks are \n\n (blank line) or \n followed by indent/caps after period.
        val normalized = text
            // Preserve real paragraph breaks (blank lines)
            .replace(Regex("\n\\s*\n"), "\u0000PARA\u0000")
            // Merge remaining single newlines into spaces (PDF line wraps)
            .replace(Regex("\n"), " ")
            // Restore paragraph breaks
            .replace("\u0000PARA\u0000", "\n\n")
            // Collapse multiple spaces
            .replace(Regex(" {2,}"), " ")

        val paragraphs = normalized.split(Regex("\n\n+")).map { it.trim() }.filter { it.isNotEmpty() }

        for ((i, paragraph) in paragraphs.withIndex()) {
            // Split into sentences at .!? followed by space+capital or quote
            val sentenceParts = paragraph.split(Regex("(?<=[.!?])\\s+(?=[A-Z\"])"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            // Batch 2-4 sentences into chunks for smoother TTS flow.
            // Each chunk should be 100-600 chars — natural reading span.
            val buffer = StringBuilder()
            for (sentence in sentenceParts) {
                if (buffer.length + sentence.length > 500 && buffer.isNotEmpty()) {
                    chunks.add(buffer.toString().trim())
                    buffer.clear()
                }
                if (buffer.isNotEmpty()) buffer.append(" ")
                buffer.append(sentence)
            }
            if (buffer.isNotEmpty()) chunks.add(buffer.toString().trim())

            // Add paragraph break marker (except after last paragraph)
            if (i < paragraphs.size - 1) {
                chunks.add(PARAGRAPH_BREAK)
            }
        }

        return chunks
    }
}
