package com.example.smartattendance.data.dao

import androidx.room.*
import com.example.smartattendance.data.entity.FaceSample

@Dao
interface FaceSampleDao {
    @Insert
    suspend fun insert(sample: FaceSample): Long

    @Query("SELECT * FROM face_samples WHERE studentId = :studentId")
    suspend fun getSamples(studentId: Long): List<FaceSample>

    @Query("SELECT * FROM face_samples")
    suspend fun getAllSamples(): List<FaceSample>

    @Query("DELETE FROM face_samples WHERE studentId = :studentId")
    suspend fun deleteStudentSamples(studentId: Long)
}
