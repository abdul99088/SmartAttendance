
package com.example.smartattendance.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.Closeable

class FaceImageProcessor(context: Context) : Closeable {

    private val detector: FaceDetector

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .enableTracking()
            .build()

        detector = FaceDetection.getClient(options)
    }

    fun detectAndCrop(
        bitmap: Bitmap,
        onResult: (Result<Bitmap>) -> Unit
    ) {
        if (bitmap.isRecycled) {
            onResult(Result.failure(IllegalArgumentException("Image is unavailable")))
            return
        }

        val image = InputImage.fromBitmap(bitmap, 0)

        detector.process(image)
            .addOnSuccessListener { faces ->

                try {
                    if (faces.isEmpty()) {
                        throw IllegalArgumentException("No face detected. Select another image.")
                    }

                    if (faces.size != 1) {
                        throw IllegalArgumentException(
                            "Please select an image containing exactly one face."
                        )
                    }

                    val bounds = Rect(faces[0].boundingBox)

                    // Add a little margin around the detected face.
                    val horizontalMargin = (bounds.width() * 0.15f).toInt()
                    val verticalMargin = (bounds.height() * 0.15f).toInt()

                    bounds.inset(-horizontalMargin, -verticalMargin)

                    // Keep the crop inside the original image.
                    bounds.left = bounds.left.coerceAtLeast(0)
                    bounds.top = bounds.top.coerceAtLeast(0)
                    bounds.right = bounds.right.coerceAtMost(bitmap.width)
                    bounds.bottom = bounds.bottom.coerceAtMost(bitmap.height)

                    if (bounds.width() < 40 || bounds.height() < 40) {
                        throw IllegalArgumentException("Detected face is too small.")
                    }

                    val faceBitmap = Bitmap.createBitmap(
                        bitmap,
                        bounds.left,
                        bounds.top,
                        bounds.width(),
                        bounds.height()
                    )

                    onResult(Result.success(faceBitmap))

                } catch (e: Exception) {
                    onResult(Result.failure(e))
                }
            }
            .addOnFailureListener { exception ->
                onResult(Result.failure(exception))
            }
    }

    override fun close() {
        detector.close()
    }
}
