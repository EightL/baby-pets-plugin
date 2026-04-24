package com.petsplugin.listener;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Handles player join/quit — loading data, respawning pets.
 */
public class PlayerListener implements Listener {

    private final PetsPlugin plugin;

    public PlayerListener(PetsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Load pet data into cache
        plugin.getPetManager().loadPlayerPets(player.getUniqueId());
        plugin.getSettingsManager().loadPlayerSettings(player.getUniqueId());
        plugin.getAdvancementManager().syncPlayerAdvancements(player);
        plugin.getIncubatorManager().discoverIncubatorRecipe(player);
        plugin.getPetManager().refreshPlayerCustomItems(player);
        plugin.getPetManager().refreshPetVisibility(player);

        // Respawn active pet if configured
        if (plugin.getConfig().getBoolean("pets.respawn_on_join", true)) {
            PetInstance selected = plugin.getPetManager().getSelectedPet(player.getUniqueId());
            if (selected != null) {
                // Delay spawn to let player fully load.
                schedulePetRespawn(player, selected, 40L);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Despawn pet entity
        plugin.getPetManager().despawnPet(player.getUniqueId(), false);
        plugin.getPetManager().clearViewerHoverTarget(player.getUniqueId());

        // Save data (pets are saved on each update, but clear cache)
        plugin.getPetManager().clearCache(player.getUniqueId());
        plugin.getPetManager().clearPlayerSessionState(player.getUniqueId());
        plugin.getSettingsManager().unloadPlayerSettings(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            plugin.getPetManager().refreshPlayerCustomItems(player);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getPetManager().refreshPlayerCustomItems(player));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Respawn pet after death
        PetInstance selected = plugin.getPetManager().getActivePet(player.getUniqueId());
        if (selected == null) {
            selected = plugin.getPetManager().getSelectedPet(player.getUniqueId());
        }
        if (selected != null) {
            schedulePetRespawn(player, selected, 20L);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!plugin.getConfig().getBoolean("pets.cross_dimension_teleport", true)) return;

        Player player = event.getPlayer();
        PetInstance pet = plugin.getPetManager().getActivePet(player.getUniqueId());
        if (pet != null) {
            // Despawn in old world, respawn in new
            plugin.getPetManager().despawnPet(player.getUniqueId(), false);
            schedulePetRespawn(player, pet, 10L);
        }
    }

    private void schedulePetRespawn(Player player, PetInstance pet, long delayTicks) {
        if (pet == null) {
            return;
        }
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getPetManager().spawnPet(player, pet);
            }
        }, delayTicks);
    }
}
