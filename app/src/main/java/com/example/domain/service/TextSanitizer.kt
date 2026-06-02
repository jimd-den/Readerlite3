package com.example.domain.service

interface TextSanitizer {
    /**
     * Sanitizes and normalizes raw text by decoding HTML/XML/numeric entities,
     * removing invisible/zero-width formatting characters, collapsing repeated whitespaces,
     * and preserving natural sentence spacing and apostrophes.
     */
    fun sanitize(text: String): String
}

class TextSanitizerImpl : TextSanitizer {
    
    // Core HTML named entities map
    private val namedEntities = mapOf(
        "&nbsp;" to " ",
        "&amp;" to "&",
        "&quot;" to "\"",
        "&apos;" to "'",
        "&lt;" to "<",
        "&gt;" to ">",
        "&copy;" to "©",
        "&reg;" to "®",
        "&trade;" to "™",
        "&shy;" to "" // soft hyphen removed
    )

    override fun sanitize(text: String): String {
        if (text.isEmpty()) return text

        var working = text

        // 1. First wash: replace known case-insensitive named HTML entities
        namedEntities.forEach { (entity, replacement) ->
            working = working.replace(entity, replacement, ignoreCase = true)
        }

        // 2. Decode general decimal numeric entities (e.g., &#39; and &#160;)
        val decRegex = Regex("&#(\\d+);")
        working = decRegex.replace(working) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null) {
                // Handle non-breaking space specially as normal space
                if (code == 160) {
                    " "
                } else {
                    code.toChar().toString()
                }
            } else {
                match.value
            }
        }

        // 3. Decode general hex numeric entities (e.g., &#x201C; and &#x201D;)
        val hexRegex = Regex("&#[xX]([0-9a-fA-F]+);")
        working = hexRegex.replace(working) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null) {
                if (code == 0xA0 || code == 0x2002 || code == 0x2003 || code == 0x2009) {
                    " " // spaces to space
                } else {
                    code.toChar().toString()
                }
            } else {
                match.value
            }
        }

        // 4. Remove soft hyphens, zero-width spaces, and other invisible joiners / non-joiners
        // \u00AD = soft hyphen
        // \u200B = zero-width space
        // \u200C = zero-width non-joiner
        // \u200D = zero-width joiner
        // \uFEFF = zero-width no-break space
        val invisibleChars = charArrayOf('\u00AD', '\u200B', '\u200C', '\u200D', '\uFEFF')
        working = working.filter { it !in invisibleChars }.toString()

        // Re-construct string from unfiltered builder if necessary, but Kotlin's filter returns the String or CharSequence
        val builder = java.lang.StringBuilder()
        for (i in working.indices) {
            val c = working[i]
            if (c != '\u00AD' && c != '\u200B' && c != '\u200C' && c != '\u200D' && c != '\uFEFF') {
                builder.append(c)
            }
        }
        working = builder.toString()

        // 5. Clean line separators, tab gaps, carriage returns, and non-breaking space unicode representations
        working = working.replace('\u00A0', ' ')
        working = working.replace('\u2007', ' ')
        working = working.replace('\u202F', ' ')

        // 6. Normalize all whitespace gaps into a single space, collapse consecutive spaces, trim edges
        working = working.replace(Regex("\\s+"), " ")

        return working.trim()
    }
}
