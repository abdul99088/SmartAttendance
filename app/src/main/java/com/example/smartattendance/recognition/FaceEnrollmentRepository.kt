
package com.example.smartattendance.recognition

import android.content.Context
import android.graphics.Bitmap
import com.example.smartattendance.data.database.AttendanceDatabase
import com.example.smartattendance.data.entity.FaceSample
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FaceEnrollmentRepository(
    context: Context,
    private val database: AttendanceDatabase
) : Closeable {

    private val recognitionEngine =
        FaceRecognitionEngine(context.applicationContext)

    /**
     * Detects a face, generates its embedding,
     * encrypts the embedding and saves it in Room.
     *
     * Returns the newly inserted FaceSample ID.
     */
    suspend fun enrollFace(
        studentId: Long,
        bitmap: Bitmap
    ): Long {

        require(studentId > 0) {
            "Invalid student ID."
        }

        require(!bitmap.isRecycled) {
            "Selected image is unavailable."
        }

        // Step 1: Detect the face and generate its embedding.
        val embedding = suspendCancellableCoroutine<FloatArray> { continuation ->

            recognitionEngine.processImage(bitmap) { result ->

                result.fold(
                    onSuccess = { value ->
                        if (continuation.isActive) {
                            continuation.resume(value)
                        }
                    },
                    onFailure = { error ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(error)
                        }
                    }
                )
            }
        }

        // Step 2: Encode and encrypt the embedding.
        // Step 3: Save encrypted bytes in Room.
        return withContext(Dispatchers.IO) {

            val encodedEmbedding = FaceEmbeddingCodec.encode(embedding)

            val encryptedEmbedding =
                FaceEmbeddingCrypto.encrypt(encodedEmbedding)

            val sample = FaceSample(
                studentId = studentId,
                embedding = encryptedEmbedding
            )

            database.faceSampleDao().insert(sample)
        }
    }

    override fun close() {
        recognitionEngine.close()
    }
}
