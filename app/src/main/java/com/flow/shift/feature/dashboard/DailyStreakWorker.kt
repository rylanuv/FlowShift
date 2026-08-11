package com.flow.shift.feature.dashboard

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flow.shift.core.database.UserGamificationDao
import com.flow.shift.core.database.UserGamificationEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar

@HiltWorker
class DailyStreakWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val gamificationDao: UserGamificationDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val userGamification = gamificationDao.getUserGamification().firstOrNull()
            
            if (userGamification != null) {
                val lastActive = Calendar.getInstance().apply {
                    timeInMillis = userGamification.lastActiveDateMillis
                }
                
                val yesterday = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                }

                val today = Calendar.getInstance()

                // Check if last active was yesterday (maintain streak) or today (already updated)
                val isSameDay = lastActive.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                lastActive.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                                
                val isYesterday = lastActive.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                                  lastActive.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)

                if (!isSameDay && !isYesterday) {
                    // Streak lost! Reset to 0
                    val updatedGamification = userGamification.copy(
                        currentStreakDays = 0
                    )
                    gamificationDao.insertOrUpdate(updatedGamification)
                }
            } else {
                // First time setup
                gamificationDao.insertOrUpdate(
                    UserGamificationEntity(
                        totalXp = 0,
                        currentLevel = 1,
                        currentStreakDays = 0,
                        lastActiveDateMillis = System.currentTimeMillis(),
                        scrollCredits = 0
                    )
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

