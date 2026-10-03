package com.example.spendsync.utils

object LocalizationUtils {
    /** Maps the stored date-format choice (Settings → Date format) to a java.time pattern. */
    fun getDateFormatPattern(formatPattern: String): String = when (formatPattern) {
        "MM / DD / YYYY" -> "MM/dd/yyyy"
        "YYYY - MM - DD" -> "yyyy-MM-dd"
        else             -> "dd/MM/yyyy" // DD / MM / YYYY
    }
}
