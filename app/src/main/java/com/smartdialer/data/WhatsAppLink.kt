package com.smartdialer.data

/** Builds `wa.me` chat links. Nothing is ever read from WhatsApp; the link only opens a chat. */
object WhatsAppLink {
    /** Package names to try, in order of preference: WhatsApp, then WhatsApp Business. */
    val PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    /**
     * Returns the number as international digits only (no `+`, spaces or dashes), or null if it cannot be
     * made international.
     *
     * - `+972 50-111-1111` and `00972501111111` are international already.
     * - Anything else is a local number; [localToE164] (the platform's formatter for the device's country)
     *   converts it. It returns null when it cannot.
     */
    fun internationalDigits(number: String, localToE164: (String) -> String?): String? {
        val trimmed = number.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val result = when {
            trimmed.startsWith("+") -> digits
            digits.startsWith("00") -> digits.drop(2)
            else -> localToE164(trimmed)?.filter { it.isDigit() }
        }
        return result?.takeIf { it.length in 7..15 && !it.startsWith("0") }
    }

    fun url(digits: String): String = "https://wa.me/$digits"

    /** Picks the first preferred package that is installed, or null when WhatsApp is not installed. */
    fun choosePackage(installed: Set<String>): String? = PACKAGES.firstOrNull { it in installed }
}
