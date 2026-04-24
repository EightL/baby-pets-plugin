package com.petsplugin.listener;

import com.petsplugin.PetsPlugin;
import com.petsplugin.gui.PetStorageGUI;
import com.petsplugin.model.PetFollowMode;
import com.petsplugin.model.PetInstance;
import com.petsplugin.model.PetType;
import io.papermc.paper.event.player.PlayerNameEntityEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
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
        plugin.getLanguageManager().withPlayer(event.getPlayer(), () -> handlePetName(event));
    }

    private void handlePetName(PlayerNameEntityEvent event) {
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
        if (plugin.getSettingsManager().isPetSoundsEnabled(player.getUniqueId())) {
            PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
            if (type != null) {
                plugin.getPetManager().playPetAmbientSound(event.getEntity(), type);
            }
        }
    }

    @EventHandler
    public void onPetInteractAt(PlayerInteractAtEntityEvent event) {
        plugin.getLanguageManager().withPlayer(event.getPlayer(), () -> handlePetInteract(event, event.getClickedPosition().getY()));
    }

    @EventHandler
    public void onPetInteract(PlayerInteractEntityEvent event) {
        plugin.getLanguageManager().withPlayer(event.getPlayer(), () -> handlePetInteract(event, null));
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

        if (player.isSneaking()) {
            toggleFollowMode(player);
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

        // Storage pet: empty hand (not sneaking) opens the storage GUI
        if (emptyHand && !player.isSneaking()
                && type != null && type.getSpecialAbility() == PetType.SpecialAbility.STORAGE) {
            openPetStorage(player, pet, type);
            return;
        }

        if (headPat || emptyHand) {
            handlePetting(player, pet);
        }
    }

    private void toggleFollowMode(Player player) {
        PetFollowMode current = plugin.getSettingsManager().getFollowMode(player.getUniqueId());
        PetFollowMode next = current == PetFollowMode.STAY ? PetFollowMode.FOLLOW : PetFollowMode.STAY;
        plugin.getPetManager().setFollowMode(player, next);
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
        long feedCd = plugin.getFeedCooldownMillis();
        Long lastFeed = feedCooldowns.get(player.getUniqueId());
        if (lastFeed != null && now - lastFeed < feedCd) {
            long remaining = (feedCd - (now - lastFeed)) / 1000;
            plugin.getPetManager().sendPetNotification(player,
                    "messages.feed_cooldown",
                    "&cYou can feed again in %seconds%s.",
                    Map.of("%seconds%", String.valueOf(remaining + 1)));
            return;
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
        feedCooldowns.put(player.getUniqueId(), now);
        plugin.getPetManager().feedPet(player, pet);
    }

    private void handlePetting(Player player, PetInstance pet) {
        long now = System.currentTimeMillis();
        long petCd = plugin.getPetCooldownMillis();
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
        if (plugin.getSettingsManager().isPetSoundsEnabled(player.getUniqueId())) {
            PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
            if (type != null) {
                plugin.getPetManager().playPetAmbientSound(entity, type);
            }
        }
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

    private void openPetStorage(Player player, PetInstance pet, PetType type) {
        Map<Integer, org.bukkit.inventory.ItemStack> contents =
                plugin.getDatabaseManager().loadPlayerStorage(player.getUniqueId(), type.getStorageGroup());
        new PetStorageGUI(plugin, player, type, pet.getLevel(), contents).open(player);
    }

    private boolean shouldIgnoreDuplicate(Player player, Entity entity) {
        String key = player.getUniqueId() + ":" + entity.getUniqueId();
        long now = System.currentTimeMillis();

        if (recentInteractions.size() > 4096) {
            long cutoff = now - 10000L;
            recentInteractions.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }

        Long lastAt = recentInteractions.get(key);
        recentInteractions.put(key, now);
        return lastAt != null && now - lastAt < 75L;
    }
}
