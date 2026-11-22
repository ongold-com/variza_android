package com.variza.smsforwarder.util

/**
 * Decides whether an SMS sender matches one of the numbers the user entered.
 *
 * - Numbers: digits only are compared, using the last 10 digits, so
 *   "+98912...", "0098912..." and "0912..." all match each other.
 * - Short codes (fewer than 7 digits, e.g. "3000") must match exactly.
 * - Names (e.g. "BankMelli"): compared as text, ignoring case.
 */
object PhoneMatch {

    /** Keeps only digits, converting Persian/Arabic-Indic digits to ASCII. */
    fun digits(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            val d = Character.digit(c, 10)
            if (d >= 0) sb.append(('0' + d))
        }
        return sb.toString()
    }

    private fun hasLetters(s: String): Boolean = s.any { it.isLetter() }

    fun matches(sender: String, entry: String): Boolean {
        val s = sender.trim()
        val e = entry.trim()
        if (s.isEmpty() || e.isEmpty()) return false

        if (hasLetters(s) || hasLetters(e)) {
            return s.equals(e, ignoreCase = true)
        }

        val sd = digits(s)
        val ed = digits(e)
        if (sd.isEmpty() || ed.isEmpty()) return false
        if (sd.length < 7 || ed.length < 7) return sd == ed
        return sd.takeLast(10) == ed.takeLast(10)
    }

    fun matchesAny(sender: String, entries: List<String>): Boolean =
        entries.any { matches(sender, it) }
}
