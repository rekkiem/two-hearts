package com.twohearts

import com.twohearts.services.EmbeddingService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.math.abs

class EmbeddingServiceTest {

    private val svc = EmbeddingService()

    @Test
    fun `embed returns correct dimension`() {
        val v = svc.embed("I love hiking and cooking")
        assertEquals(EmbeddingService.DIMS, v.size)
    }

    @Test
    fun `embed returns L2-normalized vector`() {
        val v = svc.embed("test text for normalization")
        val norm = Math.sqrt(v.sumOf { it.toDouble() * it }).toFloat()
        assertEquals(1.0f, norm, 0.001f)
    }

    @Test
    fun `similar texts have higher cosine similarity than dissimilar`() {
        val a = svc.embed("I love hiking in the mountains and exploring nature")
        val b = svc.embed("Hiking and outdoor adventures in nature are my passion")
        val c = svc.embed("I enjoy cooking Italian food and visiting art museums")

        val simAB = svc.cosineSimilarity(a, b)
        val simAC = svc.cosineSimilarity(a, c)

        assertTrue(simAB > simAC, "Similar texts should have higher cosine sim (simAB=$simAB simAC=$simAC)")
    }

    @Test
    fun `identical text has similarity 1_0`() {
        val text = "looking for someone who appreciates deep conversations"
        val a = svc.embed(text)
        val b = svc.embed(text)
        assertEquals(1.0f, svc.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `blank text returns zero vector`() {
        val v = svc.embed("")
        assertTrue(v.all { it == 0f }, "Blank text should produce zero vector")
    }

    @Test
    fun `embedProfile combines bio and occupation`() {
        val v = svc.embedProfile("I love music and art", "Software engineer", null)
        assertEquals(EmbeddingService.DIMS, v.size)
        val norm = Math.sqrt(v.sumOf { it.toDouble() * it }).toFloat()
        assertTrue(norm > 0.99f && norm < 1.01f, "Should be normalized: norm=$norm")
    }

    @Test
    fun `floatArrayToVector formats correctly`() {
        val v = floatArrayOf(0.1f, -0.2f, 0.3f)
        val str = svc.floatArrayToVector(v)
        assertTrue(str.startsWith("["))
        assertTrue(str.endsWith("]"))
        assertTrue(str.contains("0.1"))
    }

    @Test
    fun `cosine similarity range is -1 to 1`() {
        repeat(10) {
            val a = FloatArray(128) { Math.random().toFloat() - 0.5f }
            val b = FloatArray(128) { Math.random().toFloat() - 0.5f }
            val sim = svc.cosineSimilarity(a, b)
            assertTrue(sim >= -1.01f && sim <= 1.01f, "sim=$sim out of range")
        }
    }
}

class ProfileServiceUtilsTest {

    @Test
    fun `calculateAge returns correct age`() {
        val today = java.time.LocalDate.now()
        val birthDate = today.minusYears(25).toString()
        val age = com.twohearts.services.ProfileService.calculateAge(birthDate)
        assertEquals(25, age)
    }

    @Test
    fun `calculateAge handles invalid date gracefully`() {
        val age = com.twohearts.services.ProfileService.calculateAge("not-a-date")
        assertEquals(0, age)
    }

    @Test
    fun `genders round-trip through DB format`() {
        val genders = listOf("woman", "non-binary")
        val dbStr = com.twohearts.services.ProfileService.gendersToDb(genders)
        val back = com.twohearts.services.ProfileService.dbToGenders(dbStr)
        assertEquals(genders, back)
    }

    @Test
    fun `empty genders round-trip`() {
        val dbStr = com.twohearts.services.ProfileService.gendersToDb(emptyList())
        val back = com.twohearts.services.ProfileService.dbToGenders(dbStr)
        assertTrue(back.isEmpty())
    }
}
