package com.flow.shift.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flow.shift.core.database.BlockedAppDao
import com.flow.shift.core.database.FlowShiftDatabase
import com.flow.shift.core.database.InterventionDao
import com.flow.shift.core.database.UserGamificationDao
import com.flow.shift.core.database.WorkoutSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE workout_sessions ADD COLUMN durationUnlockedMillis INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS interventions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    targetAppPackage TEXT NOT NULL,
                    targetAppName TEXT NOT NULL,
                    requiredReps INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FlowShiftDatabase {
        return Room.databaseBuilder(
            context,
            FlowShiftDatabase::class.java,
            "flowshift_db"
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideBlockedAppDao(database: FlowShiftDatabase): BlockedAppDao {
        return database.blockedAppDao()
    }

    @Provides
    fun provideWorkoutSessionDao(database: FlowShiftDatabase): WorkoutSessionDao {
        return database.workoutSessionDao()
    }

    @Provides
    fun provideUserGamificationDao(database: FlowShiftDatabase): UserGamificationDao {
        return database.userGamificationDao()
    }

    @Provides
    fun provideInterventionDao(database: FlowShiftDatabase): InterventionDao {
        return database.interventionDao()
    }
}

