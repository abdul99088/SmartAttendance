
package com.example.smartattendance.data.dao

import androidx.room.*
import com.example.smartattendance.data.entity.Student
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {

    @Insert
    suspend fun insert(student: Student): Long

    @Update
    suspend fun update(student: Student)

    @Delete
    suspend fun delete(student: Student)

    @Query("SELECT * FROM students ORDER BY studentName ASC")
    fun getAllStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudent(id: Long): Student?

    @Query("SELECT * FROM students WHERE rollNumber = :roll LIMIT 1")
    suspend fun findByRollNumber(roll: String): Student?

    @Query("SELECT COUNT(*) FROM students WHERE isActive = 1")
    fun getStudentCount(): Flow<Int>
}
