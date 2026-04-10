package com.petsplugin.manager;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetType;
import com.petsplugin.model.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import java.util.*;

/**
 * Manages pet egg items — creation, rarity rolling, and identification.
 */
public class EggManager {

    private final PetsPlugin plugin;
    public final NamespacedKey EGG_RARITY_KEY;
    public final NamespacedKey EGG_KEY;

    public EggManager(PetsPlugin plugin) {
        this.plugin = plugin;
        this.EGG_RARITY_KEY = new NamespacedKey(plugin, "egg_rarity");
        this.EGG_KEY = new NamespacedKey(plugin, "pet_egg");
    }

    /**
     * Create an egg item of the given rarity.
     */
    public ItemStack createEgg(Rarity rarity) {
        String base64 = plugin.getConfig().getString("eggs.texture", "");
        ItemStack egg = getCustomSkull(base64);

        SkullMeta meta = (SkullMeta) egg.getItemMeta();
        meta.displayName(Component.text("Pet Egg")
                .color(rarity.getColor())
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("A mysterious egg...").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.empty());
        lore.add(Component.text("Place in a ").color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("Pet Incubator").color(NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, true))
                .append(Component.text(" to hatch!").color(NamedTextColor.YELLOW)));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(EGG_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(EGG_RARITY_KEY, PersistentDataType.STRING, rarity.name());
        egg.setItemMeta(meta);
        return egg;
    }

    /**
     * Check if an item is a pet egg.
     */
    public boolean isEgg(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(EGG_KEY, PersistentDataType.BYTE);
    }

    /**
     * Get the rarity of a pet egg item.
     */
    public Rarity getEggRarity(ItemStack item) {
        if (!isEgg(item)) return null;
        String rarity = item.getItemMeta().getPersistentDataContainer()
                .get(EGG_RARITY_KEY, PersistentDataType.STRING);
        if (rarity == null) return Rarity.COMMON;
        try {
            return Rarity.valueOf(rarity);
        } catch (IllegalArgumentException e) {
            return Rarity.COMMON;
        }
    }

    /**
     * Roll a random rarity for an egg based on configured weights.
     */
    public Rarity rollRarity() {
        double roll = new Random().nextDouble() * 100.0;
        double commonThreshold = plugin.getConfig().getDouble("eggs.loot_injection.common_threshold", 50.0);
        double uncommonThreshold = plugin.getConfig().getDouble("eggs.loot_injection.uncommon_threshold", 75.0);
        double rareThreshold = plugin.getConfig().getDouble("eggs.loot_injection.rare_threshold", 90.0);
        double epicThreshold = plugin.getConfig().getDouble("eggs.loot_injection.epic_threshold", 98.0);

        if (roll < commonThreshold) return Rarity.COMMON;
        if (roll < uncommonThreshold) return Rarity.UNCOMMON;
        if (roll < rareThreshold) return Rarity.RARE;
        if (roll < epicThreshold) return Rarity.EPIC;
        return Rarity.LEGENDARY;
    }

    /**
     * Roll a pet type from the given egg rarity.
     * Each rarity maps to specific pet types.
     */
    public PetType rollPetType(Rarity rarity) {
        List<PetType> candidates = new ArrayList<>();
        for (PetType type : plugin.getPetTypes().values()) {
            if (type.getRarity() == rarity) {
                candidates.add(type);
            }
        }

        if (candidates.isEmpty()) {
            // Fallback: pick any pet
            candidates.addAll(plugin.getPetTypes().values());
        }

        return candidates.get(new Random().nextInt(candidates.size()));
    }

    private ItemStack getCustomSkull(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (base64 == null || base64.isEmpty()) return head;

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        PlayerProfile profile = org.bukkit.Bukkit.createProfile(UUID.randomUUID(), null);
        profile.getProperties().add(new ProfileProperty("textures", base64));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }
}
