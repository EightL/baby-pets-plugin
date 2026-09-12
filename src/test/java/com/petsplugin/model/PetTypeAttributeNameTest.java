package com.petsplugin.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PetTypeAttributeNameTest {

    @Test
    void acceptsModernAndLegacyAttributeNames() {
        assertTrue(PetType.attributeKeyCandidates("MAX_HEALTH").contains("max_health"));
        assertTrue(PetType.attributeKeyCandidates("GENERIC_MAX_HEALTH").contains("max_health"));
        assertTrue(PetType.attributeKeyCandidates("generic.attack_damage").contains("attack_damage"));
        assertTrue(PetType.attributeKeyCandidates("minecraft:generic.attack_speed").contains("attack_speed"));
        assertTrue(PetType.attributeKeyCandidates("PLAYER_BLOCK_BREAK_SPEED").contains("block_break_speed"));
        assertTrue(PetType.attributeKeyCandidates("MAX_HEALTH").contains("generic.max_health"));
        assertTrue(PetType.attributeKeyCandidates("BLOCK_BREAK_SPEED").contains("player.block_break_speed"));
        assertTrue(PetType.attributeKeyCandidates("GENERIC_MAX_HEALTH").contains("generic.max_health"));
    }
}
