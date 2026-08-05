package com.petsplugin.listener;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles player join/quit — loading data, respawning pets.
 */
public class PlayerListener implements Listener {

    private final PetsPlugin plugin;
    private final Map<UUID, BukkitTask> pendingPetRespawns = new HashMap<>();

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
                schedulePetRespawn(player, 40L);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        cancelPendingPetRespawn(player.getUniqueId());

        // A dead Player remains online while the death screen is open. Removing the
        // runtime entity here prevents the follow task from projecting new pets at
        // the death location until PlayerRespawnEvent fires.
        plugin.getPetManager().despawnPet(player.getUniqueId(), false);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        // Older BabyPets versions saved runtime pet mobs into chunks. Remove any
        // untracked copies as soon as an affected chunk is loaded.
        plugin.getPetManager().cleanupStalePetArtifacts(event.getEntities());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cancelPendingPetRespawn(player.getUniqueId());

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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAbsorptionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getNewEffect() == null || event.getModifiedType() != PotionEffectType.ABSORPTION) return;

        // The effect event fires before vanilla has finished updating its attribute
        // modifier and absorption amount. Fill the cow's extra capacity next tick.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.getPetManager().fillActivePetAbsorptionBonus(player);
            }
        });
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
            schedulePetRespawn(player, 20L);
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
            schedulePetRespawn(player, 10L);
        }
    }

    private void schedulePetRespawn(Player player, long delayTicks) {
        UUID playerUuid = player.getUniqueId();
        cancelPendingPetRespawn(playerUuid);

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingPetRespawns.remove(playerUuid);
            if (player.isOnline() && player.isValid() && !player.isDead()) {
                PetInstance selected = plugin.getPetManager().getSelectedPet(playerUuid);
                if (selected != null) {
                    plugin.getPetManager().spawnPet(player, selected);
                }
            }
        }, delayTicks);
        pendingPetRespawns.put(playerUuid, task);
    }

    private void cancelPendingPetRespawn(UUID playerUuid) {
        BukkitTask pending = pendingPetRespawns.remove(playerUuid);
        if (pending != null) {
            pending.cancel();
        }
    }
}
