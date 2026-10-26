package org.zotero.android.screens.reader.translate

/**
 * Markwon's LaTeX plugin only understands `$$...$$` (inline) and `$$` lines (display), while LLMs
 * usually write `$...$`, `\(...\)` and `\[...\]`. Converts those into the syntax Markwon expects,
 * leaving code spans and code blocks untouched.
 */
object MarkdownMathNormalizer {

    private val codeRegex = Regex("```[\\s\\S]*?(```|$)|`[^`\\n]*`")
    private val displayBracketsRegex = Regex("\\\\\\[([\\s\\S]+?)\\\\]")
    private val inlineParenthesesRegex = Regex("\\\\\\(([\\s\\S]+?)\\\\\\)")
    // Whole line made of a single $$...$$ formula: rendered as a display formula.
    private val displayLineRegex = Regex("^[ \\t]*\\$\\$([^\\n]+?)\\$\\$[ \\t]*$", RegexOption.MULTILINE)
    // $...$ with no space right after the opening and before the closing dollar (so "$5 and $10" stays text).
    private val inlineDollarRegex = Regex("(?<![\\\\$])\\$(?![\\s$])([^$\\n]*?[^\\s$\\\\])\\$(?![$\\d])")

    fun normalize(markdown: String): String {
        val result = StringBuilder()
        var last = 0
        for (code in codeRegex.findAll(markdown)) {
            result.append(normalizeMath(markdown.substring(last, code.range.first)))
            result.append(code.value)
            last = code.range.last + 1
        }
        result.append(normalizeMath(markdown.substring(last)))
        return result.toString()
    }

    private fun normalizeMath(text: String): String {
        var out = displayBracketsRegex.replace(text) { display(it.groupValues[1]) }
        out = displayLineRegex.replace(out) { display(it.groupValues[1]) }
        out = inlineParenthesesRegex.replace(out) { inline(it.groupValues[1]) }
        out = inlineDollarRegex.replace(out) { inline(it.groupValues[1]) }
        return out
    }

    private fun display(latex: String): String {
        return "\n\n$$\n${latex.trim()}\n$$\n\n"
    }

    private fun inline(latex: String): String {
        return "$$" + latex.trim().replace('\n', ' ') + "$$"
    }
}
