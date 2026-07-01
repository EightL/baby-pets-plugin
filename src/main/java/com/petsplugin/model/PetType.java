package com.petsplugin.model;

import com.petsplugin.manager.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Represents a pet species loaded from pets.yml.
 * Pets give the PLAYER an attribute bonus that scales with pet level.
 */
public class PetType {

    public enum SpecialAbility { NONE, UNDERWATER_VISION, STORAGE }

    public static class PotionBonus {
        private final PotionEffectType effectType;
        private final int amplifier;

        public PotionBonus(PotionEffectType effectType, int amplifier) {
            this.effectType = effectType;
            this.amplifier = amplifier;
        }

        public PotionEffectType getEffectType() { return effectType; }
        public int getAmplifier() { return amplifier; }
        public String getTierDisplay() { return amplifier >= 1 ? "II" : "I"; }
    }

    private static final double DISPLAY_EPSILON = 1.0E-9;

    private final String id;
    private final String displayName;
    private final EntityType entityType;
    private final Rarity rarity;
    private final String description;
    private final Material icon;
    private final boolean baby;
    private final boolean flying;
    private final boolean aquatic;

    // Special ability
    private final SpecialAbility specialAbility;
    private final int storageSize;
    private final String storageGroup;
    private final Material storageGlass;

    // Player attribute this pet provides
    private final Attribute playerAttribute;
    private final boolean hasPlayerAttribute;
    private final double attributePerLevel;
    private final String attributeDisplay;   // Human-readable label
    private final List<PotionBonus> potionBonuses;

    public PetType(String id, ConfigurationSection section) {
        this.id = id;
        this.displayName = section.getString("display_name", id);
        this.entityType = EntityType.valueOf(section.getString("entity_type", "PIG"));
        this.rarity = Rarity.valueOf(section.getString("rarity", "COMMON"));
        this.description = section.getString("description", "");
        this.icon = Material.valueOf(section.getString("icon", "PIG_SPAWN_EGG"));
        this.baby = section.getBoolean("baby", true);
        this.flying = section.getBoolean("flying", false);
        this.aquatic = section.getBoolean("aquatic", false);

        // Special ability
        SpecialAbility ability = SpecialAbility.NONE;
        try {
            ability = SpecialAbility.valueOf(section.getString("special_ability", "NONE").toUpperCase());
        } catch (IllegalArgumentException ignored) { }
        this.specialAbility = ability;
        this.storageSize = section.getInt("storage_size", 0);
        this.storageGroup = section.getString("storage_group", id);
        Material glass = Material.GRAY_STAINED_GLASS_PANE;
        try {
            glass = Material.valueOf(section.getString("storage_glass", "GRAY_STAINED_GLASS_PANE").toUpperCase());
        } catch (IllegalArgumentException ignored) { }
        this.storageGlass = glass;

        // Player attribute
        ConfigurationSection attr = section.getConfigurationSection("player_attribute");
        if (attr != null) {
            String configuredType = attr.getString("type");
            Attribute parsed = parseAttribute(configuredType);
            this.hasPlayerAttribute = (parsed != null);
            this.playerAttribute = parsed;
            this.attributePerLevel = attr.getDouble("value_per_level", 0.0);
            this.attributeDisplay = attr.getString("display", "Unknown");

            if (configuredType != null && !configuredType.isBlank() && parsed == null) {
                Bukkit.getLogger().warning("[BabyPets] Invalid player_attribute.type '" + configuredType
                        + "' for pet '" + id + "'. This bonus will be disabled. Potion effects like EFFECT.MINECRAFT.* are not supported here.");
            }
        } else {
            this.hasPlayerAttribute = false;
            this.playerAttribute = null;
            this.attributePerLevel = 0.0;
            this.attributeDisplay = "";
        }

        List<PotionBonus> parsedPotions = new ArrayList<>();
        List<?> effectsList = section.getList("effects");
        if (effectsList != null) {
            for (Object obj : effectsList) {
                if (obj instanceof Map<?, ?> map) {
                    addPotionBonus(parsedPotions, id, map);
                }
            }
        }
        this.potionBonuses = Collections.unmodifiableList(parsedPotions);
    }

    private Attribute parseAttribute(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        String normalized = name.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = normalized.contains(":")
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized);
        if (key == null) {
            return null;
        }

        return Registry.ATTRIBUTE.get(key);
    }

    private void addPotionBonus(List<PotionBonus> out, String petId, Map<?, ?> effectMap) {
        Object typeValue = effectMap.get("type");
        String typeName = typeValue == null ? "" : String.valueOf(typeValue).trim().toLowerCase(Locale.ROOT);
        if (typeName.isBlank()) {
            return;
        }

        int amplifier = 0;
        Object tierValue = effectMap.get("tier");
        if (tierValue != null && String.valueOf(tierValue).trim().equalsIgnoreCase("II")) {
            amplifier = 1;
        }

        NamespacedKey key = typeName.contains(":")
                ? NamespacedKey.fromString(typeName)
                : NamespacedKey.minecraft(typeName);
        if (key == null) {
            Bukkit.getLogger().warning("[BabyPets] Unknown potion effect '" + typeName + "' for pet '" + petId + "'. This entry will be ignored.");
            return;
        }

        PotionEffectType effectType = Registry.EFFECT.get(key);
        if (effectType == null) {
            Bukkit.getLogger().warning("[BabyPets] Unknown potion effect '" + typeName + "' for pet '" + petId + "'. This entry will be ignored.");
            return;
        }

        out.add(new PotionBonus(effectType, amplifier));
    }

    // Getters
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getLocalizedDisplayName(LanguageManager lm) {
        return lm.getString("pet." + id + ".name", displayName);
    }
    public EntityType getEntityType() { return entityType; }
    public Rarity getRarity() { return rarity; }
    public String getDescription() { return description; }
    public String getLocalizedDescription(LanguageManager lm) {
        return lm.getString("pet." + id + ".desc", description);
    }
    public Material getIcon() { return icon; }
    public boolean isBaby() { return baby; }
    public SpecialAbility getSpecialAbility() { return specialAbility; }
    public int getStorageSize() { return storageSize; }
    public String getStorageGroup() { return storageGroup; }
    public Material getStorageGlass() { return storageGlass; }
    public PetMovementType getMovementType() {
        if (aquatic) {
            return PetMovementType.WATER;
        }
        if (flying) {
            return PetMovementType.FLYING;
        }
        return PetMovementType.GROUND;
    }

    public boolean hasPlayerAttribute() { return hasPlayerAttribute; }
    public Attribute getPlayerAttribute() { return playerAttribute; }
    public String getAttributeDisplay() { return attributeDisplay; }
    public String getLocalizedAttributeDisplay(LanguageManager lm) {
        return lm.getString("pet." + id + ".attribute", attributeDisplay);
    }
    public List<PotionBonus> getPotionBonuses() { return potionBonuses; }
    public boolean hasPotionBonuses() { return !potionBonuses.isEmpty(); }

    /**
     * How many storage slots are active at the given level.
     * Scales linearly: ceil(maxSlots * level / maxLevel), minimum 1.
     */
    public int computeActiveStorageSlots(int level, int maxLevel) {
        if (storageSize <= 0) return 0;
        return Math.max(1, (int) Math.ceil((double) storageSize * level / maxLevel));
    }

    /** Total attribute bonus at this level. */
    public double getAttributeAtLevel(int level) {
        return level * attributePerLevel;
    }

    /**
     * Returns the formatted bonus at this level, e.g. "2.5" or "-0.5".
     * Negative values (e.g. FALL_DAMAGE_MULTIPLIER) are already signed.
     */
    public String formatAttributeBonus(int level) {
        return formatAttributeValue(getAttributeAtLevel(level));
    }

    public String formatAttributePerLevel() {
        return formatAttributeValue(attributePerLevel);
    }

    /** True if this attribute's value_per_level is negative (e.g. fall damage reduction). */
    public boolean isNegativeAttribute() {
        return attributePerLevel < 0;
    }

    private String formatAttributeValue(double value) {
        if (Math.abs(value - Math.rint(value)) < DISPLAY_EPSILON) {
            return String.format(Locale.US, "%.0f", value);
        }
        if (Math.abs((value * 10.0) - Math.rint(value * 10.0)) < DISPLAY_EPSILON) {
            return String.format(Locale.US, "%.1f", value);
        }
        return String.format(Locale.US, "%.2f", value);
    }
}
