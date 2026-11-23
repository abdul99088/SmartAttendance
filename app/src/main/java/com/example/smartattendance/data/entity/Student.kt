package com.example.smartattendance.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentName: String,
    val rollNumber: String,
    val className: String,
    val registrationDate: Long = System.currentTimeMillis(),
    val consentGiven: Boolean = false,
    val isActive: Boolean = true
)
