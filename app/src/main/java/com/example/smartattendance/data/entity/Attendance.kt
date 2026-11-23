package com.example.smartattendance.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance",
    indices = [Index(value = ["studentId", "date"], unique = true)]
)
data class Attendance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val date: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PRESENT",
    val markedBy: String = "FACE_RECOGNITION"
)
