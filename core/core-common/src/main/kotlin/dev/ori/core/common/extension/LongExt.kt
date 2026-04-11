package dev.ori.core.common.extension

import java.text.DecimalFormat

fun Long.toHumanReadableSize(): String {
    if (this < 1024) return "$this B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    val formatter = DecimalFormat("#.##").apply {
        decimalFormatSymbols = decimalFormatSymbols.apply { decimalSeparator = '.' }
    }
    var size = this.toDouble()
    var unitIndex = -1
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }
    return "${formatter.format(size)} ${units[unitIndex]}"
}

fun Long.toRelativeTimeString(): String {
    val now = System.currentTimeMillis()
    val diff = now - this
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        else -> "${diff / 604_800_000}w ago"
    }
}
