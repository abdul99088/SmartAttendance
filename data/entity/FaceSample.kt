
package com.example.smartattendance.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "face_samples")
data class FaceSample(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val studentId: Long,
    val embedding: ByteArray,
    val createdAt: Long = System.currentTimeMillis()
)
