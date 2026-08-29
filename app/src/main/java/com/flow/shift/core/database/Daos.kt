package com.flow.shift.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps")
    fun getAllBlockedApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps WHERE isEnabled = 1")
    fun getEnabledBlockedApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps WHERE packageName = :packageName")
    fun getBlockedApp(packageName: String): BlockedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBlockedApp(app: BlockedAppEntity)

    @Update
    fun updateBlockedApp(app: BlockedAppEntity)
}

@Dao
interface WorkoutSessionDao {
    @Query("SELECT * FROM workout_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE targetAppPackage = :targetPackage ORDER BY timeUnlockedMillis DESC LIMIT 1")
    fun getLatestSessionForPackage(targetPackage: String): WorkoutSessionEntity?

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE (targetAppPackage = :packageName OR targetAppPackage = 'ALL_APPS') AND timeUnlockedMillis > :nowMillis")
    fun getActiveUnlockCount(packageName: String, nowMillis: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSession(session: WorkoutSessionEntity)
}

@Dao
interface InterventionDao {
    @Query("SELECT * FROM interventions ORDER BY timestamp DESC")
    fun getAllInterventions(): Flow<List<InterventionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertIntervention(intervention: InterventionEntity)
}

@Dao
interface UserGamificationDao {
    @Query("SELECT * FROM user_gamification WHERE id = 1")
    fun getUserGamification(): Flow<UserGamificationEntity?>

    @Query("SELECT * FROM user_gamification WHERE id = 1")
    fun getUserGamificationNow(): UserGamificationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(gamification: UserGamificationEntity)
}

