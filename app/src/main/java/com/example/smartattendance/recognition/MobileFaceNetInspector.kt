
package com.example.smartattendance.recognition

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object MobileFaceNetInspector {

    private const val TAG = "MobileFaceNetInspector"
    private const val MODEL_FILE = "mobile_facenet.tflite"

    fun inspect(context: Context) {
        var interpreter: Interpreter? = null

        try {
            val modelBytes = context.assets.open(MODEL_FILE).use {
                it.readBytes()
            }

            val modelBuffer = ByteBuffer
                .allocateDirect(modelBytes.size)
                .order(ByteOrder.nativeOrder())

            modelBuffer.put(modelBytes)
            modelBuffer.rewind()

            interpreter = Interpreter(modelBuffer)

            Log.i(TAG, "========== MODEL INSPECTION ==========")

            for (i in 0 until interpreter.inputTensorCount) {
                val tensor = interpreter.getInputTensor(i)
                Log.i(
                    TAG,
                    "INPUT $i | ${tensor.name()} | " +
                            "${tensor.shape().contentToString()} | ${tensor.dataType()}"
                )
            }

            for (i in 0 until interpreter.outputTensorCount) {
                val tensor = interpreter.getOutputTensor(i)
                Log.i(
                    TAG,
                    "OUTPUT $i | ${tensor.name()} | " +
                            "${tensor.shape().contentToString()} | ${tensor.dataType()}"
                )
            }

            // Controlled test: identical NCHW input in both branches.
            val testImage = Array(1) {
                Array(3) {
                    Array(112) {
                        FloatArray(112) { 0.5f }
                    }
                }
            }

            val output = Array(2) {
                FloatArray(128)
            }

            interpreter.runForMultipleInputsOutputs(
                arrayOf(testImage, testImage),
                mapOf(0 to output)
            )

            val row0 = output[0]
            val row1 = output[1]

            var dot = 0.0
            var norm0 = 0.0
            var norm1 = 0.0

            for (i in 0 until 128) {
                dot += row0[i] * row1[i]
                norm0 += row0[i] * row0[i]
                norm1 += row1[i] * row1[i]
            }

            val similarity = if (norm0 > 0 && norm1 > 0) {
                dot / (sqrt(norm0) * sqrt(norm1))
            } else {
                0.0
            }

            Log.i(TAG, "========== INFERENCE TEST ==========")
            Log.i(TAG, "Output row 0 first 5: ${row0.take(5)}")
            Log.i(TAG, "Output row 1 first 5: ${row1.take(5)}")
            Log.i(TAG, "Row 0 norm: ${sqrt(norm0)}")
            Log.i(TAG, "Row 1 norm: ${sqrt(norm1)}")
            Log.i(TAG, "Cosine similarity between rows: $similarity")
            Log.i(TAG, "========== TEST COMPLETE ==========")

        } catch (e: Exception) {
            Log.e(TAG, "MODEL INSPECTION FAILED", e)
        } finally {
            interpreter?.close()
        }
    }
}
