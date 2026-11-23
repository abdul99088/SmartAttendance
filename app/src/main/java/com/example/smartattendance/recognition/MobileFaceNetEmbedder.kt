
package com.example.smartattendance.recognition

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class MobileFaceNetEmbedder(context: Context) : Closeable {

    companion object {
        private const val MODEL_FILE = "mobile_facenet.tflite"
        private const val IMAGE_SIZE = 112
        private const val EMBEDDING_SIZE = 128
    }

    private val interpreter: Interpreter

    init {
        val modelBytes = context.applicationContext.assets
            .open(MODEL_FILE)
            .use { it.readBytes() }

        val modelBuffer = ByteBuffer
            .allocateDirect(modelBytes.size)
            .order(ByteOrder.nativeOrder())

        modelBuffer.put(modelBytes)
        modelBuffer.rewind()

        val options = Interpreter.Options().apply {
            setNumThreads(2)
        }

        interpreter = Interpreter(modelBuffer, options)

        require(interpreter.inputTensorCount == 2) {
            "Expected 2 model inputs"
        }

        require(
            interpreter.getInputTensor(0).shape()
                .contentEquals(intArrayOf(1, 3, IMAGE_SIZE, IMAGE_SIZE))
        ) {
            "Unexpected first input shape"
        }

        require(
            interpreter.getInputTensor(1).shape()
                .contentEquals(intArrayOf(1, 3, IMAGE_SIZE, IMAGE_SIZE))
        ) {
            "Unexpected second input shape"
        }
    }

    @Synchronized
    fun embed(faceBitmap: Bitmap): FloatArray {

        require(!faceBitmap.isRecycled) {
            "Face bitmap has been recycled"
        }

        val resized = Bitmap.createScaledBitmap(
            faceBitmap,
            IMAGE_SIZE,
            IMAGE_SIZE,
            true
        )

        try {
            val input = Array(1) {
                Array(3) {
                    Array(IMAGE_SIZE) {
                        FloatArray(IMAGE_SIZE)
                    }
                }
            }

            val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)

            resized.getPixels(
                pixels,
                0,
                IMAGE_SIZE,
                0,
                0,
                IMAGE_SIZE,
                IMAGE_SIZE
            )

            for (y in 0 until IMAGE_SIZE) {
                for (x in 0 until IMAGE_SIZE) {

                    val pixel = pixels[y * IMAGE_SIZE + x]

                    val red = (pixel shr 16) and 0xFF
                    val green = (pixel shr 8) and 0xFF
                    val blue = pixel and 0xFF

                    input[0][0][y][x] = red / 255f
                    input[0][1][y][x] = green / 255f
                    input[0][2][y][x] = blue / 255f
                }
            }

            val output = Array(2) {
                FloatArray(EMBEDDING_SIZE)
            }

            interpreter.runForMultipleInputsOutputs(
                arrayOf(input, input),
                mapOf(0 to output)
            )

            val embedding = output[0]

            var squaredNorm = 0.0

            for (value in embedding) {
                require(value.isFinite()) {
                    "Model produced a non-finite embedding"
                }

                squaredNorm += value.toDouble() * value.toDouble()
            }

            val norm = sqrt(squaredNorm)

            require(norm > 0.000001) {
                "Model produced an empty embedding"
            }

            return FloatArray(EMBEDDING_SIZE) { index ->
                (embedding[index] / norm).toFloat()
            }

        } finally {
            if (resized !== faceBitmap) {
                resized.recycle()
            }
        }
    }

    override fun close() {
        interpreter.close()
    }
}
