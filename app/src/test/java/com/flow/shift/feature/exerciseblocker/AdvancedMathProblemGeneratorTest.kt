package com.flow.shift.feature.exerciseblocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedMathProblemGeneratorTest {

    @Test
    fun testAllTopicsAndDifficulties_noEncodingArtifacts() {
        for (topic in AdvancedMathTopic.values()) {
            for (difficulty in MathDifficulty.values()) {
                repeat(20) {
                    val problem = AdvancedMathProblemGenerator.generateProblem(
                        topics = setOf(topic),
                        difficulty = difficulty
                    )

                    assertTrue("Question should not be blank", problem.question.isNotBlank())
                    assertFalse("Question contains mojibake Â: ${problem.question}", problem.question.contains("Â"))
                    assertFalse("Question contains mojibake Ã: ${problem.question}", problem.question.contains("Ã"))
                    assertFalse("Question contains mojibake â: ${problem.question}", problem.question.contains("â"))

                    assertFalse("Hint contains mojibake Â: ${problem.hint}", problem.hint.contains("Â"))
                    assertFalse("Hint contains mojibake Ã: ${problem.hint}", problem.hint.contains("Ã"))
                    assertFalse("Hint contains mojibake â: ${problem.hint}", problem.hint.contains("â"))

                    assertNotNull("Answer should be an integer string: ${problem.answer}", problem.answer.toIntOrNull())
                }
            }
        }
    }

    @Test
    fun testRandomTopicSelection_selectsMultipleTopics() {
        val selectedTopics = mutableSetOf<AdvancedMathTopic>()
        val allTopics = AdvancedMathTopic.values().toSet()
        repeat(100) {
            val problem = AdvancedMathProblemGenerator.generateProblem(
                topics = allTopics,
                difficulty = MathDifficulty.MEDIUM
            )
            selectedTopics.add(problem.topic)
        }
        assertTrue(selectedTopics.size > 1)
    }

    @Test
    fun testResolveTopicsForChallenge_withRandomOrEmpty() {
        fun resolveTopics(topics: Set<String>): Set<AdvancedMathTopic> {
            return if (topics.contains("RANDOM") || topics.isEmpty()) {
                AdvancedMathTopic.values().toSet()
            } else {
                topics.mapNotNull {
                    try { AdvancedMathTopic.valueOf(it) } catch (e: Exception) { null }
                }.toSet().ifEmpty { AdvancedMathTopic.values().toSet() }
            }
        }

        assertEquals(AdvancedMathTopic.values().toSet(), resolveTopics(setOf("RANDOM")))
        assertEquals(AdvancedMathTopic.values().toSet(), resolveTopics(emptySet()))
        assertEquals(setOf(AdvancedMathTopic.GEOMETRY), resolveTopics(setOf("GEOMETRY")))
        assertEquals(
            setOf(AdvancedMathTopic.CALCULUS, AdvancedMathTopic.MATRIX),
            resolveTopics(setOf("CALCULUS", "MATRIX"))
        )
    }
}
