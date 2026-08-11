package com.flow.shift.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        BlockedAppEntity::class,
        WorkoutSessionEntity::class,
        UserGamificationEntity::class,
        InterventionEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class FlowShiftDatabase : RoomDatabase() {
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun userGamificationDao(): UserGamificationDao
    abstract fun interventionDao(): InterventionDao
}

