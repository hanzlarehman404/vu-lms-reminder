package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

enum class ActivityType(
    val displayName: String,
    val emoji: String,
    val color: Color
) {
    ASSIGNMENT("Assignment", "📝", CategoryAssignmentColor),
    QUIZ("Quiz", "⚡", CategoryQuizColor),
    GDB("GDB", "💬", CategoryGdbColor),
    PRACTICAL("Practical", "🧪", CategoryPracticalColor),
    CHALLAN("Challan", "💳", CategoryChallanColor),
    OTHER("Activity", "📌", CategoryOtherColor);

    val fullLabel: String
        get() = "$emoji $displayName"

    companion object {
        fun fromString(typeStr: String?, title: String? = "", url: String? = ""): ActivityType {
            val text = "${typeStr.orEmpty()} ${title.orEmpty()} ${url.orEmpty()}".lowercase()
            return when {
                text.contains("assignment") -> ASSIGNMENT
                text.contains("quiz") -> QUIZ
                text.contains("gdb") || text.contains("discussion") || text.contains("activitytype=gdb") -> GDB
                text.contains("practical") || text.contains("lab") || text.contains("301p") -> PRACTICAL
                text.contains("challan") || text.contains("fee") -> CHALLAN
                else -> OTHER
            }
        }
    }
}
