package com.petsplugin.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetStatusTest {

    @Test
    void careProgressionCapsAtEcstatic() {
        assertEquals(PetStatus.CONTENT, PetStatus.HUNGRY.better());
        assertEquals(PetStatus.ECSTATIC, PetStatus.HAPPY.better());
        assertEquals(PetStatus.ECSTATIC, PetStatus.ECSTATIC.better());
        assertTrue(PetStatus.HAPPY.canImprove());
        assertFalse(PetStatus.ECSTATIC.canImprove());
    }

    @Test
    void decayProgressionCapsAtSad() {
        assertEquals(PetStatus.HUNGRY, PetStatus.CONTENT.worse());
        assertEquals(PetStatus.SAD, PetStatus.HUNGRY.worse());
        assertEquals(PetStatus.SAD, PetStatus.SAD.worse());
    }

    @Test
    void defaultMoodMultipliersRewardCare() {
        assertTrue(PetStatus.ECSTATIC.getDefaultAbilityMultiplier()
                > PetStatus.CONTENT.getDefaultAbilityMultiplier());
        assertTrue(PetStatus.CONTENT.getDefaultAbilityMultiplier()
                > PetStatus.SAD.getDefaultAbilityMultiplier());
        assertTrue(PetStatus.ECSTATIC.getDefaultXpMultiplier()
                > PetStatus.CONTENT.getDefaultXpMultiplier());
        assertTrue(PetStatus.CONTENT.getDefaultXpMultiplier()
                > PetStatus.SAD.getDefaultXpMultiplier());
    }
}
