package com.flow.shift.core.progress

import com.flow.shift.core.database.UserGamificationDao
import com.flow.shift.core.database.UserGamificationEntity
import com.flow.shift.core.database.WorkoutSessionDao
import com.flow.shift.core.database.WorkoutSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressRepository @Inject constructor(
    private val workoutSessionDao: WorkoutSessionDao,
    private val userGamificationDao: UserGamificationDao
) {
    suspend fun recordUnlock(
        targetPackage: String,
        repsCompleted: Int,
        durationUnlockedMillis: Long
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        
        val recentSessions = workoutSessionDao.getAllSessions().first()
        val latestSession = recentSessions.find { it.targetAppPackage == targetPackage }
        val baseTime = if (latestSession != null && latestSession.timeUnlockedMillis > now) {
            latestSession.timeUnlockedMillis
        } else {
            now
        }

        workoutSessionDao.insertSession(
            WorkoutSessionEntity(
                timestamp = now,
                targetAppPackage = targetPackage,
                repsCompleted = repsCompleted,
                timeUnlockedMillis = baseTime + durationUnlockedMillis,
                durationUnlockedMillis = durationUnlockedMillis
            )
        )

        val current = userGamificationDao.getUserGamificationNow()
        val earnedMinutes = TimeUnit.MILLISECONDS.toMinutes(durationUnlockedMillis).toInt()
        val previousXp = current?.totalXp ?: 0
        val updatedXp = previousXp + (repsCompleted * 2) + earnedMinutes

        userGamificationDao.insertOrUpdate(
            UserGamificationEntity(
                id = 1,
                totalXp = updatedXp,
                currentLevel = (updatedXp / 100) + 1,
                currentStreakDays = nextStreak(current?.lastActiveDateMillis, now, current?.currentStreakDays ?: 0),
                lastActiveDateMillis = now,
                scrollCredits = (current?.scrollCredits ?: 0) + earnedMinutes
            )
        )
    }

    private fun nextStreak(lastActiveMillis: Long?, nowMillis: Long, currentStreak: Int): Int {
        if (lastActiveMillis == null || lastActiveMillis == 0L) return 1

        val lastActive = Calendar.getInstance().apply { timeInMillis = lastActiveMillis }
        val today = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }

        val sameDay = lastActive.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            lastActive.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        val previousDay = lastActive.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            lastActive.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)

        return when {
            sameDay -> currentStreak.coerceAtLeast(1)
            previousDay -> currentStreak + 1
            else -> 1
        }
    }
}

