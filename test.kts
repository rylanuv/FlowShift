import java.util.regex.Regex

fun parseTargetScreenTimeMillis(targetStr: String): Long {
    if (targetStr.contains("-")) {
        val parts = targetStr.split("-")
        val hours = parts.lastOrNull()?.trim()?.toLongOrNull() ?: 2L
        return hours * 60L * 60L * 1000L
    }
    var totalMinutes = 0L
    val hoursMatch = Regex("(\\\\d+)\\\\s*h", RegexOption.IGNORE_CASE).find(targetStr)
    if (hoursMatch != null) {
        totalMinutes += (hoursMatch.groupValues[1].toLongOrNull() ?: 0L) * 60L
    }
    val minutesMatch = Regex("(\\\\d+)\\\\s*m", RegexOption.IGNORE_CASE).find(targetStr)
    if (minutesMatch != null) {
        totalMinutes += (minutesMatch.groupValues[1].toLongOrNull() ?: 0L) * 1L
    }
    if (hoursMatch == null && minutesMatch == null) {
        val raw = targetStr.trim().toLongOrNull()
        if (raw != null && raw > 0) {
            totalMinutes = if (raw <= 24) raw * 60L else raw
        } else {
            totalMinutes = 120L // default 2 hours
        }
    }
    return totalMinutes * 60L * 1000L
}

fun main() {
    println(parseTargetScreenTimeMillis("0m"))
    println(parseTargetScreenTimeMillis("0"))
    println(parseTargetScreenTimeMillis("0 min"))
}
