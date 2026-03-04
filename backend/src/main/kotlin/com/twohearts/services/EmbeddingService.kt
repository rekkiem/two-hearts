package com.twohearts.services

import kotlin.math.sqrt

/**
 * Lightweight deterministic text embedding service.
 * Produces 128-dimensional float vectors from text using:
 * - Character trigrams (morphological similarity)
 * - Word unigrams + bigrams (semantic content)
 * - Positive PMI-inspired co-occurrence weighting
 *
 * Good enough for MVP matching. Replace with sentence-transformers in v2.
 */
class EmbeddingService {

    companion object {
        const val DIMS = 128
    }

    fun embed(text: String): FloatArray {
        val result = FloatArray(DIMS)
        if (text.isBlank()) return result

        val normalized = text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) return result

        val words = normalized.split(" ").filter { it.length >= 2 }

        // Unigrams (weight = 1.0)
        words.forEach { word ->
            val idx = (murmur64(word) and 0x7FFFFFFFFFFFFFFFL) % DIMS
            result[idx.toInt()] += 1.0f
        }

        // Bigrams (weight = 0.8)
        words.zipWithNext { a, b -> "${a}_$b" }.forEach { bigram ->
            val idx = (murmur64(bigram) and 0x7FFFFFFFFFFFFFFFL) % DIMS
            result[idx.toInt()] += 0.8f
        }

        // Character trigrams (weight = 0.4) — captures morphological similarity
        words.forEach { word ->
            if (word.length >= 3) {
                for (i in 0..word.length - 3) {
                    val trigram = word.substring(i, i + 3)
                    val idx = (murmur64(trigram) and 0x7FFFFFFFFFFFFFFFL) % DIMS
                    result[idx.toInt()] += 0.4f
                }
            }
        }

        // Skip-bigrams (w[i], w[i+2]) weight = 0.5
        if (words.size > 2) {
            for (i in 0..words.size - 3) {
                val skip = "${words[i]}_${words[i + 2]}"
                val idx = (murmur64(skip) and 0x7FFFFFFFFFFFFFFFL) % DIMS
                result[idx.toInt()] += 0.5f
            }
        }

        return l2Normalize(result)
    }

    /**
     * Combine multiple texts (e.g., bio + occupation + intent) into one embedding.
     * Each field is weighted differently.
     */
    fun embedProfile(bio: String?, occupation: String?, recentIntentAnswer: String?): FloatArray {
        val result = FloatArray(DIMS)

        bio?.let {
            val v = embed(it)
            for (i in result.indices) result[i] += v[i] * 0.4f
        }
        occupation?.let {
            val v = embed(it)
            for (i in result.indices) result[i] += v[i] * 0.2f
        }
        recentIntentAnswer?.let {
            val v = embed(it)
            for (i in result.indices) result[i] += v[i] * 0.4f
        }

        return l2Normalize(result)
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size)
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom == 0f) 0f else dot / denom
    }

    fun floatArrayToVector(arr: FloatArray): String = "[${arr.joinToString(",")}]"

    private fun l2Normalize(v: FloatArray): FloatArray {
        val norm = sqrt(v.sumOf { it.toDouble() * it }).toFloat()
        if (norm == 0f) return v
        return FloatArray(v.size) { v[it] / norm }
    }

    // FIX ERROR 3: constantes hex que desbordan Long → valores signed-Long equivalentes
    // 0xDEADBEEFCAFEBABE, 0xFF51AFD7ED558CCD, 0xC4CEB9FE1A85EC53 > Long.MAX_VALUE
    private fun murmur64(key: String): Long {
        var h = -2401053089206453570L  // 0xDEADBEEFCAFEBABE como Long signed
        key.forEach { c ->
            h = h xor c.code.toLong()
            h *= 7046029254386353131L  // 0x61C8864680B583EB (positivo, no desborda)
            h = h xor (h ushr 33)
        }
        h *= -49064778989728563L       // 0xFF51AFD7ED558CCD como Long signed
        h = h xor (h ushr 33)
        h *= -4265267296055464877L     // 0xC4CEB9FE1A85EC53 como Long signed
        h = h xor (h ushr 33)
        return h
    }
}
