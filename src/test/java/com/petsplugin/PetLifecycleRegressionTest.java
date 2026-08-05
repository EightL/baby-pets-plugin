package com.petsplugin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PetLifecycleRegressionTest {

    @Test
    void deathScreenCannotCreatePersistentPetEntities() throws IOException {
        String playerListener = Files.readString(
                Path.of("src/main/java/com/petsplugin/listener/PlayerListener.java"));
        String petManager = Files.readString(
                Path.of("src/main/java/com/petsplugin/manager/PetManager.java"));

        assertTrue(playerListener.contains("onDeath(PlayerDeathEvent event)"));
        assertTrue(playerListener.contains("onEntitiesLoad(EntitiesLoadEvent event)"));
        assertTrue(playerListener.contains("cancelPendingPetRespawn(player.getUniqueId())"));
        assertTrue(playerListener.contains("despawnPet(player.getUniqueId(), false)"));
        assertTrue(petManager.contains("player.isDead()"));
        assertTrue(petManager.contains("mob.setPersistent(false)"));
        assertTrue(petManager.contains("cleanupStalePetArtifacts(Collection<? extends Entity> entities)"));
    }
}
