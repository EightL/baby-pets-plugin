package com.petsplugin.listener;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetInstance;
import com.petsplugin.model.PetType;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles feeding pets (right-click with food) and petting (shift+right-click empty hand).
 * Also prevents pet damage.
 */
public class PetInteractListener implements Listener {

    private final PetsPlugin plugin;
    private final Map<UUID, Long> feedCooldowns = new HashMap<>();
    private final Map<UUID, Long> petCooldowns = new HashMap<>();

    public PetInteractListener(PetsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPetInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!plugin.getPetManager().isPetEntity(entity)) return;

        Player player = event.getPlayer();
        UUID ownerUuid = plugin.getPetManager().getPetOwner(entity);
        if (ownerUuid == null || !ownerUuid.equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        PetInstance pet = plugin.getPetManager().getActivePet(player.getUniqueId());
        if (pet == null) return;

        long now = System.currentTimeMillis();

        // Shift + right-click with empty hand = pet the pet
        if (player.isSneaking()) {
            long petCd = plugin.getConfig().getInt("status.pet_cooldown_seconds", 5) * 1000L;
            Long lastPet = petCooldowns.get(player.getUniqueId());
            if (lastPet != null && now - lastPet < petCd) return;

            petCooldowns.put(player.getUniqueId(), now);
            plugin.getPetManager().petThePet(player, pet);
            return;
        }

        // Right-click with valid food = feed
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isValidFood(hand)) return;

        long feedCd = plugin.getConfig().getInt("status.feed_cooldown_seconds", 3) * 1000L;
        Long lastFeed = feedCooldowns.get(player.getUniqueId());
        if (lastFeed != null && now - lastFeed < feedCd) {
            long remaining = (feedCd - (now - lastFeed)) / 1000;
            String msg = plugin.getConfig().getString("messages.feed_cooldown",
                    "&cYou can feed again in %seconds%s.");
            msg = msg.replace("%seconds%", String.valueOf(remaining + 1));
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize(msg));
            return;
        }

        hand.setAmount(hand.getAmount() - 1);
        feedCooldowns.put(player.getUniqueId(), now);
        plugin.getPetManager().feedPet(player, pet);

        player.playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0f, 1.2f);
    }

    @EventHandler
    public void onPetDamage(EntityDamageByEntityEvent event) {
        if (plugin.getPetManager().isPetEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private boolean isValidFood(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        List<String> foodItems = plugin.getConfig().getStringList("status.food_items");
        String materialName = item.getType().name();
        for (String food : foodItems) {
            if (food.equalsIgnoreCase(materialName)) return true;
        }
        return false;
    }
}
