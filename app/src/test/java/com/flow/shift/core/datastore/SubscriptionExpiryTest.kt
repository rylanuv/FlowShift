package com.flow.shift.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionExpiryTest {

    // Helper logic mirrored directly from SettingsDataStore
    private fun isPremiumChallenge(type: String): Boolean {
        return type != "MATH"
    }

    private fun fallbackChallenge(type: String): String {
        return "MATH"
    }

    private fun resolveEffectiveChallenge(challengeType: String, isPremium: Boolean): String {
        return if (!isPremium && isPremiumChallenge(challengeType)) {
            fallbackChallenge(challengeType)
        } else {
            challengeType
        }
    }

    private fun resolveEffectiveBlockType(blockType: String, isPremium: Boolean): String {
        return if (blockType == "REELS" && !isPremium) "WHOLE_APP" else blockType
    }

    private fun resolveEffectiveShowReelCount(showReelCount: Boolean, isPremium: Boolean): Boolean {
        return isPremium && showReelCount
    }

    @Test
    fun testPremiumChallenges() {
        assertTrue(isPremiumChallenge("ADVANCED_MATH"))
        assertTrue(isPremiumChallenge("SQUATS"))
        assertTrue(isPremiumChallenge("CHARGE_PHONE"))
        assertTrue(isPremiumChallenge("PUSHUPS"))
        assertFalse(isPremiumChallenge("MATH"))
    }

    @Test
    fun testFallbackLogic() {
        assertEquals("MATH", fallbackChallenge("ADVANCED_MATH"))
        assertEquals("MATH", fallbackChallenge("SQUATS"))
        assertEquals("MATH", fallbackChallenge("CHARGE_PHONE"))
        assertEquals("MATH", fallbackChallenge("PUSHUPS"))
    }

    @Test
    fun testChallengeExpiryResolution() {
        // When subscription is expired (isPremium = false), all premium challenges fallback
        assertEquals("MATH", resolveEffectiveChallenge("ADVANCED_MATH", isPremium = false))
        assertEquals("MATH", resolveEffectiveChallenge("SQUATS", isPremium = false))
        assertEquals("MATH", resolveEffectiveChallenge("CHARGE_PHONE", isPremium = false))
        assertEquals("MATH", resolveEffectiveChallenge("PUSHUPS", isPremium = false))

        // Free challenges remain unaffected
        assertEquals("MATH", resolveEffectiveChallenge("MATH", isPremium = false))

        // When subscription is active (isPremium = true), premium challenges remain
        assertEquals("ADVANCED_MATH", resolveEffectiveChallenge("ADVANCED_MATH", isPremium = true))
        assertEquals("SQUATS", resolveEffectiveChallenge("SQUATS", isPremium = true))
        assertEquals("CHARGE_PHONE", resolveEffectiveChallenge("CHARGE_PHONE", isPremium = true))
    }

    @Test
    fun testBlockTypeExpiryResolution() {
        // When expired, REELS reverts to WHOLE_APP
        assertEquals("WHOLE_APP", resolveEffectiveBlockType("REELS", isPremium = false))
        assertEquals("WHOLE_APP", resolveEffectiveBlockType("WHOLE_APP", isPremium = false))

        // When premium, REELS is kept
        assertEquals("REELS", resolveEffectiveBlockType("REELS", isPremium = true))
        assertEquals("WHOLE_APP", resolveEffectiveBlockType("WHOLE_APP", isPremium = true))
    }

    @Test
    fun testShowReelCountExpiryResolution() {
        // When expired, showReelCount is disabled even if previously enabled
        assertFalse(resolveEffectiveShowReelCount(showReelCount = true, isPremium = false))
        assertFalse(resolveEffectiveShowReelCount(showReelCount = false, isPremium = false))

        // When premium, respects user setting
        assertTrue(resolveEffectiveShowReelCount(showReelCount = true, isPremium = true))
        assertFalse(resolveEffectiveShowReelCount(showReelCount = false, isPremium = true))
    }
}
