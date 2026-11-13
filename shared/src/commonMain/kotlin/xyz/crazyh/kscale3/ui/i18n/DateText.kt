package xyz.crazyh.kscale3.ui.i18n

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * KMP 没有 `java.time.format`，日期格式按生效语言手工拼装。
 * 时间统一取设备当前时区。
 */

private fun weekdayShort(day: DayOfWeek, language: AppLanguage): String =
    if (language == AppLanguage.Chinese) {
        when (day) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            DayOfWeek.SUNDAY -> "周日"
        }
    } else {
        when (day) {
            DayOfWeek.MONDAY -> "Mon"
            DayOfWeek.TUESDAY -> "Tue"
            DayOfWeek.WEDNESDAY -> "Wed"
            DayOfWeek.THURSDAY -> "Thu"
            DayOfWeek.FRIDAY -> "Fri"
            DayOfWeek.SATURDAY -> "Sat"
            DayOfWeek.SUNDAY -> "Sun"
        }
    }

private fun monthShort(month: Int, language: AppLanguage): String =
    if (language == AppLanguage.Chinese) "${month}月"
    else listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )[(month - 1).coerceIn(0, 11)]

private fun p2(v: Int): String = v.toString().padStart(2, '0')

/** 2026年10月27日 周二 / Oct 27, 2026 · Tue */
fun formatDateLong(instant: Instant, language: AppLanguage): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val wd = weekdayShort(dt.dayOfWeek, language)
    return if (language == AppLanguage.Chinese) {
        "${dt.year}年${dt.monthNumber}月${dt.dayOfMonth}日 $wd"
    } else {
        "${monthShort(dt.monthNumber, language)} ${dt.dayOfMonth}, ${dt.year} · $wd"
    }
}

/** 10月27日 周二 / Oct 27 · Tue */
fun formatDateShort(instant: Instant, language: AppLanguage): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val wd = weekdayShort(dt.dayOfWeek, language)
    return if (language == AppLanguage.Chinese) {
        "${dt.monthNumber}月${dt.dayOfMonth}日 $wd"
    } else {
        "${monthShort(dt.monthNumber, language)} ${dt.dayOfMonth} · $wd"
    }
}

/** 10月27日 / Oct 27 */
fun formatMonthDay(instant: Instant, language: AppLanguage): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return if (language == AppLanguage.Chinese) {
        "${dt.monthNumber}月${dt.dayOfMonth}日"
    } else {
        "${monthShort(dt.monthNumber, language)} ${dt.dayOfMonth}"
    }
}

/** 08:30 */
fun formatTime(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${p2(dt.hour)}:${p2(dt.minute)}"
}

/**
 * 设备时间戳（BIA 帧内 `Y-m-d-h-m-s`，见 Measurement.deviceTs）→「10月27日 08:30」。
 * 解析失败时原样返回，避免为了展示时间格式而丢信息。
 */
fun formatDeviceTs(deviceTs: String, language: AppLanguage): String {
    val parts = deviceTs.split('-', ' ', ':', 'T').filter { it.isNotEmpty() }
    if (parts.size < 5) return deviceTs
    parts[0].toIntOrNull() ?: return deviceTs
    val month = parts[1].toIntOrNull() ?: return deviceTs
    val day = parts[2].toIntOrNull() ?: return deviceTs
    val hour = parts[3].toIntOrNull() ?: return deviceTs
    val minute = parts[4].toIntOrNull() ?: return deviceTs
    if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59) return deviceTs
    val md = if (language == AppLanguage.Chinese) "${month}月${day}日"
    else "${monthShort(month, language)} $day"
    return "$md ${p2(hour)}:${p2(minute)}"
}
