package com.flow.shift.feature.modes

import com.flow.shift.feature.settings.ChallengeState
import org.junit.Assert.assertEquals
import org.junit.Test

class AdvancedMathTopicSelectionTest {

    // Helper implementing the exact toggle logic from DisciplineModeSettingsViewModel
    private fun toggleTopic(current: Set<String>, topic: String): Set<String> {
        if (topic == "RANDOM") {
            return setOf("RANDOM")
        }

        val newTopics = current.toMutableSet()
        newTopics.remove("RANDOM")

        if (newTopics.contains(topic)) {
            if (newTopics.size > 1) {
                newTopics.remove(topic)
            } else {
                return setOf("RANDOM")
            }
        } else {
            newTopics.add(topic)
        }
        return newTopics
    }

    @Test
    fun testInitialDefaultIsRandom() {
        val state = ChallengeState()
        assertEquals(setOf("RANDOM"), state.advancedMathTopics)
    }

    @Test
    fun testSelectingSpecificTopicWhenRandomIsActive_replacesRandom() {
        val current = setOf("RANDOM")
        val updated = toggleTopic(current, "POLYNOMIAL")
        assertEquals(setOf("POLYNOMIAL"), updated)
    }

    @Test
    fun testAddingMultipleSpecificTopics() {
        var current = setOf("RANDOM")
        current = toggleTopic(current, "POLYNOMIAL")
        current = toggleTopic(current, "GEOMETRY")
        assertEquals(setOf("POLYNOMIAL", "GEOMETRY"), current)
    }

    @Test
    fun testRemovingOneOfMultipleSpecificTopics() {
        var current = setOf("POLYNOMIAL", "GEOMETRY")
        current = toggleTopic(current, "GEOMETRY")
        assertEquals(setOf("POLYNOMIAL"), current)
    }

    @Test
    fun testRemovingLastSpecificTopic_revertsToRandom() {
        val current = setOf("POLYNOMIAL")
        val updated = toggleTopic(current, "POLYNOMIAL")
        assertEquals(setOf("RANDOM"), updated)
    }

    @Test
    fun testClickingRandomWithSpecificTopicsSelected_clearsSpecificTopicsAndSetsRandom() {
        val current = setOf("POLYNOMIAL", "CALCULUS", "MATRIX")
        val updated = toggleTopic(current, "RANDOM")
        assertEquals(setOf("RANDOM"), updated)
    }

    @Test
    fun testClickingRandomWhenAlreadyRandom_staysRandom() {
        val current = setOf("RANDOM")
        val updated = toggleTopic(current, "RANDOM")
        assertEquals(setOf("RANDOM"), updated)
    }
}
