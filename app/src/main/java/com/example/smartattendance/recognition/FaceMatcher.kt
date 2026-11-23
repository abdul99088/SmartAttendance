package com.example.smartattendance.recognition

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class FaceCandidate(
    val studentId: Long,
    val embedding: FloatArray
)

data class FaceMatch(
    val studentId: Long,
    val similarity: Float
)

object FaceEmbeddingCodec {

    fun encode(embedding: FloatArray): ByteArray {
        require(embedding.isNotEmpty()) { "Embedding is empty" }
        require(embedding.all { it.isFinite() }) {
            "Embedding contains invalid values"
        }

        val buffer = ByteBuffer
            .allocate(embedding.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)

        embedding.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun decode(bytes: ByteArray): FloatArray {
        require(bytes.isNotEmpty() && bytes.size % 4 == 0) {
            "Invalid embedding data"
        }

        val buffer = ByteBuffer
            .wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)

        return FloatArray(bytes.size / 4) {
            buffer.float
        }.also {
            require(it.all { value -> value.isFinite() }) {
                "Embedding contains invalid values"
            }
        }
    }
}

object FaceMatcher {

    fun cosineSimilarity(
        first: FloatArray,
        second: FloatArray
    ): Float {
        require(first.isNotEmpty() && first.size == second.size) {
            "Embedding dimensions do not match"
        }

        var dot = 0.0
        var normFirst = 0.0
        var normSecond = 0.0

        for (i in first.indices) {
            dot += first[i].toDouble() * second[i].toDouble()
            normFirst += first[i].toDouble() * first[i].toDouble()
            normSecond += second[i].toDouble() * second[i].toDouble()
        }

        if (normFirst == 0.0 || normSecond == 0.0) return 0f

        return (dot / (kotlin.math.sqrt(normFirst) *
                kotlin.math.sqrt(normSecond))).toFloat()
    }

    fun findBestMatch(
        input: FloatArray,
        candidates: List<FaceCandidate>
    ): FaceMatch? {
        if (candidates.isEmpty()) return null

        return candidates
            .filter { it.embedding.size == input.size }
            .map {
                FaceMatch(
                    studentId = it.studentId,
                    similarity = cosineSimilarity(input, it.embedding)
                )
            }
            .maxByOrNull { it.similarity }
    }
}
