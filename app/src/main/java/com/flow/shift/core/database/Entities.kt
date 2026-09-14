package com.flow.shift.core.database

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isEnabled: Boolean,
    val customDifficulty: Int? = null
)

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val timestamp: Long,
    val targetAppPackage: String,
    val repsCompleted: Int,
    val timeUnlockedMillis: Long,
    @ColumnInfo(defaultValue = "0")
    val durationUnlockedMillis: Long = 0L
)

@Entity(tableName = "interventions")
data class InterventionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val targetAppPackage: String,
    val targetAppName: String,
    val requiredReps: Int
)

@Entity(tableName = "user_gamification")
data class UserGamificationEntity(
    @PrimaryKey val id: Int = 1,
    val totalXp: Int,
    val currentLevel: Int,
    val currentStreakDays: Int,
    val lastActiveDateMillis: Long,
    val scrollCredits: Int
)

@Entity(tableName = "reel_events")
data class ReelEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val packageName: String
)


