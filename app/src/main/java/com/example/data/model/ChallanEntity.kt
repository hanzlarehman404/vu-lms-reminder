package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ChallanEntity(
    val challanNo: String,
    val challanType: String,
    val payableAmount: String,
    val paidAmount: String,
    val balance: String,
    val dueDate: String,
    val paidDate: String,
    val isPaid: Boolean,
    val printUrl: String = "",
    val payCardUrl: String = "",
    val payControlName: String = "",
    val viewState: String = "",
    val viewStateGenerator: String = "",
    val previousPage: String = ""
) {
    fun dueDateMillis(): Long? = parseChallanDateMillis(dueDate)

    fun isDueToday(now: Long = System.currentTimeMillis()): Boolean {
        val due = dueDateMillis() ?: return false
        val calNow = Calendar.getInstance().apply { timeInMillis = now }
        val calDue = Calendar.getInstance().apply { timeInMillis = due }
        return calNow.get(Calendar.YEAR) == calDue.get(Calendar.YEAR) &&
            calNow.get(Calendar.DAY_OF_YEAR) == calDue.get(Calendar.DAY_OF_YEAR)
    }

    fun isDueTomorrow(now: Long = System.currentTimeMillis()): Boolean {
        val due = dueDateMillis() ?: return false
        val calTom = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val calDue = Calendar.getInstance().apply { timeInMillis = due }
        return calTom.get(Calendar.YEAR) == calDue.get(Calendar.YEAR) &&
            calTom.get(Calendar.DAY_OF_YEAR) == calDue.get(Calendar.DAY_OF_YEAR)
    }

    fun isOverdue(now: Long = System.currentTimeMillis()): Boolean {
        if (isPaid) return false
        val due = dueDateMillis() ?: return false
        return due < now
    }

    companion object {
        private val DATE_PATTERNS = listOf(
            "dd-MMM-yyyy",
            "dd MMM yyyy",
            "dd MMM, yyyy",
            "dd-MMM-yy",
            "dd/MM/yyyy",
            "dd-MM-yyyy",
            "yyyy-MM-dd",
            "MMM dd, yyyy",
            "MMMM dd, yyyy",
            "EEEE, MMMM dd, yyyy",
            "MM/dd/yyyy"
        )

        fun parseChallanDateMillis(raw: String): Long? {
            val cleaned = raw.trim()
                .replace(Regex("\\s+"), " ")
                .replace(Regex("(?i)^due[:\\s]*"), "")
                .trim()
            if (cleaned.isEmpty() || cleaned == "-") return null

            for (pattern in DATE_PATTERNS) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.ENGLISH).apply {
                        isLenient = false
                    }
                    val date = sdf.parse(cleaned) ?: continue
                    val cal = Calendar.getInstance().apply {
                        time = date
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                    return cal.timeInMillis
                } catch (_: Exception) {
                }
            }
            return null
        }

        fun sortChallansByUpcomingDeadline(
            challans: List<ChallanEntity>,
            now: Long = System.currentTimeMillis()
        ): List<ChallanEntity> {
            return challans.sortedWith(
                compareBy<ChallanEntity> { it.isPaid }
                    .thenBy { ch ->
                        val due = ch.dueDateMillis()
                        when {
                            ch.isPaid -> 2
                            due != null && due >= now -> 0 // Upcoming unpaid first!
                            due != null -> 1 // Overdue unpaid next
                            else -> 2
                        }
                    }
                    .thenBy { ch ->
                        val due = ch.dueDateMillis()
                        if (!ch.isPaid && due != null && due >= now) {
                            due // Nearest upcoming deadline first
                        } else if (!ch.isPaid && due != null) {
                            -due // Most recently overdue first among overdue
                        } else {
                            -(due ?: 0L) // Paid: most recent first
                        }
                    }
            )
        }
    }
}
