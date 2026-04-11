package dev.ori.core.common.extension

fun String.isValidHost(): Boolean {
    if (isBlank()) return false
    val ipPattern = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
    val hostnamePattern =
        Regex("""^[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?)*$""")
    return ipPattern.matches(this) || hostnamePattern.matches(this)
}

fun String.isValidPort(): Boolean {
    val port = toIntOrNull() ?: return false
    return port in 1..65535
}

fun String.truncateMiddle(maxLength: Int): String {
    if (length <= maxLength) return this
    val keep = (maxLength - 3) / 2
    return "${take(keep)}...${takeLast(keep)}"
}
