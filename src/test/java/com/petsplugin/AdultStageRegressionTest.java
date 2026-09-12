package com.petsplugin;

import com.petsplugin.model.PetInstance;
import com.petsplugin.model.PetType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdultStageRegressionTest {

    @Test
    void newAndLegacyConstructedPetsKeepTheirBabyAppearanceByDefault() {
        PetInstance newPet = PetInstance.createNew(UUID.randomUUID(), "horse");
        PetInstance legacyPet = new PetInstance(
                1, UUID.randomUUID(), "mule", null, 7, 0.0,
                false, 1L, null, null);

        assertTrue(newPet.isKeepBabyAppearance());
        assertTrue(legacyPet.isKeepBabyAppearance());
        assertFalse(newPet.hasReachedAdultStage(7));
        assertTrue(legacyPet.hasReachedAdultStage(7));
    }

    @Test
    void configuredAdultRidingIsLimitedToTheRequestedPets() throws IOException {
        String pets = Files.readString(Path.of("src/main/resources/pets.yml"));

        assertTrue(section(pets, "horse", "donkey").contains("adult_ability: RIDING"));
        assertFalse(section(pets, "donkey", "mule").contains("adult_ability: RIDING"));
        assertTrue(section(pets, "mule", "dolphin").contains("adult_ability: RIDING"));
        assertTrue(section(pets, "llama", "fox").contains("adult_ability: RIDING"));
        assertFalse(section(pets, "camel", "goat").contains("adult_ability: RIDING"));
        assertFalse(section(pets, "trader_llama", null).contains("adult_ability: RIDING"));
        assertTrue(PetType.AdultAbility.valueOf("RIDING") == PetType.AdultAbility.RIDING);
    }

    @Test
    void databaseMigrationPreservesExistingPetsAsBabies() throws IOException {
        String database = Files.readString(
                Path.of("src/main/java/com/petsplugin/storage/PetDatabaseManager.java"));

        assertTrue(database.contains("keep_baby_appearance BOOLEAN NOT NULL DEFAULT 1"));
        assertTrue(database.contains("ensureColumn(stmt, \"player_pets\", \"keep_baby_appearance\""));
        assertTrue(database.contains("pet.isKeepBabyAppearance()"));
    }

    private String section(String yaml, String id, String nextId) {
        int start = yaml.indexOf("  " + id + ":");
        int end = nextId == null ? yaml.length() : yaml.indexOf("  " + nextId + ":", start + 1);
        return yaml.substring(start, end);
    }
}
