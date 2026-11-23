package com.example.smartattendance.data.dao

import androidx.room.*
import com.example.smartattendance.data.entity.Attendance
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(attendance: Attendance): Long

    @Update
    suspend fun update(attendance: Attendance)

    @Query("""
        SELECT * FROM attendance
        WHERE studentId = :studentId AND date = :date
        LIMIT 1
    """)
    suspend fun getAttendanceForStudentAndDate(
        studentId: Long,
        date: String
    ): Attendance?

    @Query("SELECT * FROM attendance WHERE date = :date")
    fun getAttendanceByDate(date: String): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId ORDER BY date DESC")
    fun getStudentAttendance(studentId: Long): Flow<List<Attendance>>

    @Query("SELECT COUNT(*) FROM attendance WHERE date = :date AND status = 'PRESENT'")
    fun getPresentCount(date: String): Flow<Int>

    @Query("SELECT * FROM attendance ORDER BY date DESC")
    fun getAllAttendance(): Flow<List<Attendance>>
}