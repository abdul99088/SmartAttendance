
package com.example.smartattendance.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.smartattendance.data.entity.Student
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(student: Student): Long

    @Update
    suspend fun update(student: Student)

    @Delete
    suspend fun delete(student: Student)

    @Query("SELECT * FROM students ORDER BY id DESC")
    fun getAllStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE id = :studentId LIMIT 1")
    suspend fun getStudent(studentId: Long): Student?

    @Query("""
        SELECT * FROM students
        WHERE TRIM(rollNumber) COLLATE NOCASE =
              TRIM(:roll) COLLATE NOCASE
        LIMIT 1
    """)
    suspend fun findByRollNumber(roll: String): Student?

    @Query("SELECT COUNT(*) FROM students")
    fun getStudentCount(): Flow<Int>
}
