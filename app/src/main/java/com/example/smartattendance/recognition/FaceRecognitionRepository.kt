
package com.example.smartattendance.recognition

import android.content.Context
import android.graphics.Bitmap
import com.example.smartattendance.data.database.AttendanceDatabase
import com.example.smartattendance.data.entity.Attendance
import java.io.Closeable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class RecognitionStatus {
    ATTENDANCE_MARKED,
    ALREADY_MARKED,
    UNKNOWN_FACE,
    NO_ENROLLED_FACES
}

data class RecognitionResult(
    val status: RecognitionStatus,
    val studentName: String? = null,
    val rollNumber: String? = null,
    val similarity: Float? = null,
    val message: String
)

class FaceRecognitionRepository(
    context: Context,
    private val database: AttendanceDatabase
) : Closeable {

    companion object {
        // Initial testing value only. Calibrate with genuine test photos.
        const val DEFAULT_THRESHOLD = 0.75f
    }

    private val engine =
        FaceRecognitionEngine(context.applicationContext)

    suspend fun recognizeAndMarkAttendance(
        bitmap: Bitmap,
        threshold: Float = DEFAULT_THRESHOLD
    ): RecognitionResult {

        require(!bitmap.isRecycled) {
            "Selected image is unavailable."
        }

        require(threshold in -1f..1f) {
            "Invalid similarity threshold."
        }

        // Detect face and generate embedding.
        val inputEmbedding =
            suspendCancellableCoroutine<FloatArray> { continuation ->

                engine.processImage(bitmap) { result ->
                    result.fold(
                        onSuccess = { embedding ->
                            if (continuation.isActive) {
                                continuation.resume(embedding)
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

        return withContext(Dispatchers.IO) {

            val samples = database.faceSampleDao().getAllSamples()

            if (samples.isEmpty()) {
                return@withContext RecognitionResult(
                    status = RecognitionStatus.NO_ENROLLED_FACES,
                    message = "No face samples have been enrolled."
                )
            }

            val candidates = mutableListOf<FaceCandidate>()
            val studentCache = mutableMapOf<Long, com.example.smartattendance.data.entity.Student?>()

            // Decrypt every usable enrolled sample.
            for (sample in samples) {

                try {
                    val student = if (studentCache.containsKey(sample.studentId)) {
                        studentCache[sample.studentId]
                    } else {
                        database.studentDao()
                            .getStudent(sample.studentId)
                            .also {
                                studentCache[sample.studentId] = it
                            }
                    }

                    if (student == null ||
                        !student.isActive ||
                        !student.consentGiven
                    ) {
                        continue
                    }

                    val decrypted = FaceEmbeddingCrypto.decrypt(sample.embedding)
                    val storedEmbedding = FaceEmbeddingCodec.decode(decrypted)

                    if (storedEmbedding.size != inputEmbedding.size) {
                        continue
                    }

                    candidates.add(
                        FaceCandidate(
                            studentId = sample.studentId,
                            embedding = storedEmbedding
                        )
                    )

                } catch (_: Exception) {
                    // Ignore an unreadable or corrupt sample.
                    // Other valid samples can still be compared.
                    continue
                }
            }

            if (candidates.isEmpty()) {
                return@withContext RecognitionResult(
                    status = RecognitionStatus.NO_ENROLLED_FACES,
                    message = "No usable enrolled face samples were found."
                )
            }

            val bestMatch = FaceMatcher.findBestMatch(
                input = inputEmbedding,
                candidates = candidates
            )

            if (bestMatch == null || bestMatch.similarity < threshold) {
                return@withContext RecognitionResult(
                    status = RecognitionStatus.UNKNOWN_FACE,
                    similarity = bestMatch?.similarity,
                    message = "Unknown face. No sufficiently close match was found."
                )
            }

            val student = database.studentDao()
                .getStudent(bestMatch.studentId)

            if (student == null ||
                !student.isActive ||
                !student.consentGiven
            ) {
                return@withContext RecognitionResult(
                    status = RecognitionStatus.UNKNOWN_FACE,
                    similarity = bestMatch.similarity,
                    message = "The matched student is inactive or has not consented."
                )
            }

            // Local device date, formatted consistently.
            val today = SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
            ).format(Date())

            // The database's unique studentId + date index prevents duplicates.
            val insertedId = database.attendanceDao().insert(
                Attendance(
                    studentId = student.id,
                    date = today,
                    status = "PRESENT",
                    markedBy = "FACE_RECOGNITION"
                )
            )

            if (insertedId == -1L) {
                RecognitionResult(
                    status = RecognitionStatus.ALREADY_MARKED,
                    studentName = student.studentName,
                    rollNumber = student.rollNumber,
                    similarity = bestMatch.similarity,
                    message = "${student.studentName} is already marked present today."
                )
            } else {
                RecognitionResult(
                    status = RecognitionStatus.ATTENDANCE_MARKED,
                    studentName = student.studentName,
                    rollNumber = student.rollNumber,
                    similarity = bestMatch.similarity,
                    message = "Attendance marked successfully for ${student.studentName}."
                )
            }
        }
    }

    override fun close() {
        engine.close()
    }
}
