package com.flow.shift.feature.modes

import com.flow.shift.R

/**
 * Represents the three blocking modes available in FlowShift.
 * Each mode has a [level] that determines upgrade/downgrade direction.
 * Higher level = stricter mode.
 */
enum class BlockingMode(val level: Int, val displayName: String, val backgroundImageRes: Int) {
    EASY(1, "Easy", R.drawable.main_screen),
    STRICT(2, "Discipline", R.drawable.strict_home),
    HARDCORE(3, "Hardcore", R.drawable.hardcore_home);

    fun isUpgradeFrom(other: BlockingMode) = this.level > other.level
    fun isDowngradeFrom(other: BlockingMode) = this.level < other.level

    companion object {
        fun fromString(value: String): BlockingMode {
            return entries.find { it.name == value } ?: EASY
        }
    }
}

