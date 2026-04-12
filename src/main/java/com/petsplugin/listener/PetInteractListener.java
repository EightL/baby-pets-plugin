package com.petsplugin.listener;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetInstance;
import com.petsplugin.model.PetType;
import io.papermc.paper.event.player.PlayerNameEntityEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles naming, feeding, and petting pet entities.
 * Also prevents pet damage.
 */
public class PetInteractListener implements Listener {

    private final PetsPlugin plugin;
    private final Map<UUID, Long> feedCooldowns = new HashMap<>();
    private final Map<UUID, Long> petCooldowns = new HashMap<>();
    private final Map<String, Long> recentInteractions = new HashMap<>();

    public PetInteractListener(PetsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPetName(PlayerNameEntityEvent event) {
        if (!plugin.getPetManager().isPetEntity(event.getEntity())) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        UUID ownerUuid = plugin.getPetManager().getPetOwner(event.getEntity());
        if (ownerUuid == null || !ownerUuid.equals(player.getUniqueId())) {
            return;
        }

        PetInstance pet = plugin.getPetManager().getActivePet(player.getUniqueId());
        if (pet == null) return;

        String nickname = PlainTextComponentSerializer.plainText().serialize(event.getName()).trim();
        if (nickname.isEmpty()) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.NAME_TAG) return;

        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }

        plugin.getPetManager().renamePet(player, pet, nickname);
        player.playSound(event.getEntity().getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
    }

    @EventHandler
    public void onPetInteractAt(PlayerInteractAtEntityEvent event) {
        handlePetInteract(event, event.getClickedPosition().getY());
    }

    @EventHandler
    public void onPetInteract(PlayerInteractEntityEvent event) {
        handlePetInteract(event, null);
    }

    private void handlePetInteract(PlayerInteractEntityEvent event, Double clickedY) {
        Entity entity = event.getRightClicked();
        if (!plugin.getPetManager().isPetEntity(entity)) return;

        Player player = event.getPlayer();
        UUID ownerUuid = plugin.getPetManager().getPetOwner(entity);
        if (ownerUuid == null || !ownerUuid.equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (shouldIgnoreDuplicate(player, entity)) return;

        PetInstance pet = plugin.getPetManager().getActivePet(player.getUniqueId());
        if (pet == null || pet.getEntityUuid() == null || !pet.getEntityUuid().equals(entity.getUniqueId())) {
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.NAME_TAG) {
            handleNameTagRename(player, entity, pet, hand);
            return;
        }

        boolean headPat = isHeadPat(entity, clickedY);
        boolean emptyHand = hand.getType().isAir();
        PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
        boolean validFood = isValidFood(hand, type);

        if (validFood && !headPat) {
            handleFeeding(player, entity, pet, hand);
            return;
        }

        if (player.isSneaking() || headPat || emptyHand) {
            handlePetting(player, pet);
        }
    }

    @EventHandler
    public void onPetDamage(EntityDamageByEntityEvent event) {
        if (plugin.getPetManager().isPetEntity(event.getDamager())) {
            event.setCancelled(true);
            return;
        }
        if (plugin.getPetManager().isPetEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPetTarget(EntityTargetEvent event) {
        if (!plugin.getPetManager().isPetEntity(event.getEntity())) return;
        event.setCancelled(true);
    }

    private void handleFeeding(Player player, Entity entity, PetInstance pet, ItemStack hand) {
        long now = System.currentTimeMillis();
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

        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
        feedCooldowns.put(player.getUniqueId(), now);
        plugin.getPetManager().feedPet(player, pet);
        player.playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0f, 1.2f);
    }

    private void handlePetting(Player player, PetInstance pet) {
        long now = System.currentTimeMillis();
        long petCd = plugin.getConfig().getInt("status.pet_cooldown_seconds", 5) * 1000L;
        Long lastPet = petCooldowns.get(player.getUniqueId());
        if (lastPet != null && now - lastPet < petCd) {
            return;
        }

        petCooldowns.put(player.getUniqueId(), now);
        plugin.getPetManager().petThePet(player, pet);
    }

    private void handleNameTagRename(Player player, Entity entity, PetInstance pet, ItemStack hand) {
        String nickname = readNameTagNickname(hand);
        if (nickname == null) {
            return;
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }

        plugin.getPetManager().renamePet(player, pet, nickname);
        player.playSound(entity.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
    }

    private String readNameTagNickname(ItemStack hand) {
        if (hand == null || hand.getType() != Material.NAME_TAG || !hand.hasItemMeta()) {
            return null;
        }

        if (hand.getItemMeta().hasDisplayName()) {
            String nickname = PlainTextComponentSerializer.plainText()
                    .serialize(hand.getItemMeta().displayName())
                    .trim();
            if (!nickname.isEmpty()) {
                return nickname;
            }
        }

        if (hand.getItemMeta().hasItemName()) {
            String nickname = PlainTextComponentSerializer.plainText()
                    .serialize(hand.getItemMeta().itemName())
                    .trim();
            if (!nickname.isEmpty()) {
                return nickname;
            }
        }

        return null;
    }

    private boolean isValidFood(ItemStack item, PetType type) {
        if (item == null || item.getType().isAir() || type == null) return false;
        return plugin.getPetManager().canPetEat(type, item.getType());
    }

    private boolean isHeadPat(Entity entity, Double clickedY) {
        if (clickedY == null) return false;
        return clickedY >= entity.getHeight() * 0.6;
    }

    private boolean shouldIgnoreDuplicate(Player player, Entity entity) {
        String key = player.getUniqueId() + ":" + entity.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastAt = recentInteractions.get(key);
        recentInteractions.put(key, now);
        return lastAt != null && now - lastAt < 75L;
    }
}
