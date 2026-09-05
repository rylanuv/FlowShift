package com.flow.shift.feature.exerciseblocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedMathVectorProblemTest {

    @Test
    fun testGenerateVectorEasy_allValid() {
        repeat(100) {
            val problem = AdvancedMathProblemGenerator.generateProblem(
                topics = setOf(AdvancedMathTopic.VECTOR),
                difficulty = MathDifficulty.EASY
            )
            assertEquals(AdvancedMathTopic.VECTOR, problem.topic)
            assertTrue(problem.question.isNotBlank())
            assertTrue(problem.hint.isNotBlank())
            val intAnswer = problem.answer.toIntOrNull()
            assertNotNull("Answer must be an integer, got: ${problem.answer}", intAnswer)
        }
    }

    @Test
    fun testGenerateVectorMedium_allValid() {
        repeat(100) {
            val problem = AdvancedMathProblemGenerator.generateProblem(
                topics = setOf(AdvancedMathTopic.VECTOR),
                difficulty = MathDifficulty.MEDIUM
            )
            assertEquals(AdvancedMathTopic.VECTOR, problem.topic)
            assertTrue(problem.question.isNotBlank())
            assertTrue(problem.hint.isNotBlank())
            val intAnswer = problem.answer.toIntOrNull()
            assertNotNull("Answer must be an integer, got: ${problem.answer}", intAnswer)
        }
    }

    @Test
    fun testGenerateVectorHard_allValid() {
        repeat(100) {
            val problem = AdvancedMathProblemGenerator.generateProblem(
                topics = setOf(AdvancedMathTopic.VECTOR),
                difficulty = MathDifficulty.HARD
            )
            assertEquals(AdvancedMathTopic.VECTOR, problem.topic)
            assertTrue(problem.question.isNotBlank())
            assertTrue(problem.hint.isNotBlank())
            val intAnswer = problem.answer.toIntOrNull()
            assertNotNull("Answer must be an integer, got: ${problem.answer}", intAnswer)
        }
    }

    @Test
    fun testGenerateVectorExtreme_allValid() {
        repeat(100) {
            val problem = AdvancedMathProblemGenerator.generateProblem(
                topics = setOf(AdvancedMathTopic.VECTOR),
                difficulty = MathDifficulty.EXTREME
            )
            assertEquals(AdvancedMathTopic.VECTOR, problem.topic)
            assertTrue(problem.question.isNotBlank())
            assertTrue(problem.hint.isNotBlank())
            val intAnswer = problem.answer.toIntOrNull()
            assertNotNull("Answer must be an integer, got: ${problem.answer}", intAnswer)
        }
    }
}
