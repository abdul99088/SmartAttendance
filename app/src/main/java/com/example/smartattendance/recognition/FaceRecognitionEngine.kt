
package com.example.smartattendance.recognition

import android.content.Context
import android.graphics.Bitmap
import java.io.Closeable
import java.util.concurrent.Executors

class FaceRecognitionEngine(context: Context) : Closeable {

    private val imageProcessor = FaceImageProcessor(context)
    private val embedder = MobileFaceNetEmbedder(context)

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Detects exactly one face, crops it, and generates
     * a normalized 128-dimensional face embedding.
     *
     * The callback runs on the background executor.
     */
    fun processImage(
        bitmap: Bitmap,
        onResult: (Result<FloatArray>) -> Unit
    ) {
        if (bitmap.isRecycled) {
            onResult(
                Result.failure(
                    IllegalArgumentException("Selected image is unavailable.")
                )
            )
            return
        }

        imageProcessor.detectAndCrop(bitmap) { cropResult ->

            if (cropResult.isFailure) {
                onResult(
                    Result.failure(
                        cropResult.exceptionOrNull()
                            ?: Exception("Face detection failed.")
                    )
                )
                return@detectAndCrop
            }

            val faceBitmap = cropResult.getOrThrow()

            try {
                executor.execute {
                    try {
                        val embedding = embedder.embed(faceBitmap)
                        onResult(Result.success(embedding))
                    } catch (e: Exception) {
                        onResult(Result.failure(e))
                    } finally {
                        faceBitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                faceBitmap.recycle()
                onResult(Result.failure(e))
            }
        }
    }

    override fun close() {
        executor.shutdown()
        imageProcessor.close()
        embedder.close()
    }
}
