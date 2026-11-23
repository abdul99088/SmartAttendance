package com.example.smartattendance.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.smartattendance.data.dao.*
import com.example.smartattendance.data.entity.*

@Database(
    entities = [Student::class, FaceSample::class, Attendance::class],
    version = 1,
    exportSchema = false
)
abstract class AttendanceDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun faceSampleDao(): FaceSampleDao
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        @Volatile
        private var INSTANCE: AttendanceDatabase? = null

        fun getDatabase(context: Context): AttendanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AttendanceDatabase::class.java,
                    "smart_attendance_database"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }
}
