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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Represents a pet species loaded from pets.yml.
 * Pets give the PLAYER an attribute bonus that scales with pet level.
 */
public class PetType {

    public enum SpecialAbility { NONE, UNDERWATER_VISION, STORAGE }
    public enum AdultAbility { NONE, RIDING }

    public static class AttributeBonus {
        private final Attribute attribute;
        private final double perLevel;
        private final String display;

        public AttributeBonus(Attribute attribute, double perLevel, String display) {
            this.attribute = attribute;
            this.perLevel = perLevel;
            this.display = display;
        }

        public Attribute getAttribute() { return attribute; }
        public double getPerLevel() { return perLevel; }
        public String getDisplay() { return display; }
        public double getValueAtLevel(int level) { return level * perLevel; }
        public String getTierDisplay(int level) { return formatValue(level * perLevel); }
        public String getTierDisplay(int level, double multiplier) { return formatValue(level * perLevel * multiplier); }
        public String formatPerLevel() { return formatValue(perLevel); }
        public String formatPerLevel(double multiplier) { return formatValue(perLevel * multiplier); }

        public static String formatValue(double value) {
            if (Math.abs(value - Math.rint(value)) < DISPLAY_EPSILON) {
                return String.format(Locale.US, "%.0f", value);
            }
            if (Math.abs((value * 10.0) - Math.rint(value * 10.0)) < DISPLAY_EPSILON) {
                return String.format(Locale.US, "%.1f", value);
            }
            return String.format(Locale.US, "%.2f", value);
        }
    }

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
    private final AdultAbility adultAbility;
    private final int storageBase;
    private final double storagePerLevel;
    private final String storageGroup;
    private final Material storageGlass;

    private final List<AttributeBonus> attributeBonuses;
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
        AdultAbility grownAbility = AdultAbility.NONE;
        try {
            grownAbility = AdultAbility.valueOf(section.getString("adult_ability", "NONE").toUpperCase());
        } catch (IllegalArgumentException ignored) { }
        this.adultAbility = grownAbility;
        this.storageBase = section.getInt("storage_base", section.getInt("storage_size", 0));
        this.storagePerLevel = section.getDouble("storage_per_level", 0.0);
        this.storageGroup = section.getString("storage_group", id);
        Material glass = Material.GRAY_STAINED_GLASS_PANE;
        try {
            glass = Material.valueOf(section.getString("storage_glass", "GRAY_STAINED_GLASS_PANE").toUpperCase());
        } catch (IllegalArgumentException ignored) { }
        this.storageGlass = glass;

        List<AttributeBonus> parsedAttributes = new ArrayList<>();

        ConfigurationSection singleAttr = section.getConfigurationSection("player_attribute");
        if (singleAttr != null) {
            addAttributeBonus(parsedAttributes, id, singleAttr.getString("type"), singleAttr.getDouble("value_per_level", 0.0), singleAttr.getString("display", "Unknown"), "player_attribute");
        }

        List<?> attrList = section.getList("player_attributes");
        if (attrList != null) {
            for (Object obj : attrList) {
                if (obj instanceof Map<?, ?> map) {
                    addAttributeBonus(parsedAttributes, id, map, "player_attributes");
                }
            }
        } else {
            // Also accept a named map. This makes hand-authored YAML less fragile while
            // preserving the documented list format.
            ConfigurationSection attrMap = section.getConfigurationSection("player_attributes");
            if (attrMap != null) {
                for (String key : attrMap.getKeys(false)) {
                    ConfigurationSection entry = attrMap.getConfigurationSection(key);
                    if (entry != null) {
                        addAttributeBonus(parsedAttributes, id,
                                entry.getString("type"),
                                entry.getDouble("value_per_level", 0.0),
                                entry.getString("display", key),
                                "player_attributes." + key);
                    }
                }
            }
        }
        this.attributeBonuses = Collections.unmodifiableList(parsedAttributes);

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

    private void addAttributeBonus(List<AttributeBonus> out, String petId, String typeName, double perLevel, String display, String source) {
        String configuredType = typeName == null ? "" : typeName.trim();
        if (configuredType.isBlank()) {
            return;
        }

        Attribute parsed = parseAttribute(configuredType);
        if (parsed != null) {
            out.add(new AttributeBonus(parsed, perLevel, display == null ? "Unknown" : display));
            return;
        }

        Bukkit.getLogger().warning("[BabyPets] Invalid " + source + ".type '" + configuredType + "' for pet '" + petId + "'.");
    }

    private void addAttributeBonus(List<AttributeBonus> out, String petId, Map<?, ?> map, String source) {
        Object typeObj = map.get("type");
        String typeName = typeObj == null ? "" : String.valueOf(typeObj).trim();
        double perLevel = 0.0;
        Object perLevelObj = map.get("value_per_level");
        if (perLevelObj instanceof Number number) {
            perLevel = number.doubleValue();
        } else if (perLevelObj != null) {
            try {
                perLevel = Double.parseDouble(String.valueOf(perLevelObj));
            } catch (NumberFormatException ignored) { }
        }
        Object displayObj = map.get("display");
        String display = displayObj == null ? "Unknown" : String.valueOf(displayObj).trim();
        addAttributeBonus(out, petId, typeName, perLevel, display, source);
    }

    private Attribute parseAttribute(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        for (String candidate : attributeKeyCandidates(name)) {
            NamespacedKey key = candidate.contains(":")
                    ? NamespacedKey.fromString(candidate)
                    : NamespacedKey.minecraft(candidate);
            if (key == null) {
                continue;
            }

            Attribute attribute = Registry.ATTRIBUTE.get(key);
            if (attribute != null) {
                return attribute;
            }
        }
        return null;
    }

    static List<String> attributeKeyCandidates(String name) {
        if (name == null || name.isBlank()) {
            return Collections.emptyList();
        }

        String normalized = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(normalized);

        String keyOnly = normalized.startsWith("minecraft:")
                ? normalized.substring("minecraft:".length())
                : normalized;
        candidates.add(keyOnly);

        String modern = keyOnly.replace('.', '_');
        for (String legacyPrefix : List.of("generic_", "player_", "horse_", "zombie_")) {
            if (modern.startsWith(legacyPrefix)) {
                modern = modern.substring(legacyPrefix.length());
                break;
            }
        }
        candidates.add(modern);
        // Before 1.21.2, registry keys included the attribute's entity category.
        for (String prefix : List.of("generic.", "player.", "horse.", "zombie.")) {
            candidates.add(prefix + modern);
        }
        return List.copyOf(candidates);
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
    public AdultAbility getAdultAbility() { return adultAbility; }
    public boolean hasAdultAbility() { return adultAbility != AdultAbility.NONE; }
    public int getStorageSize() { return storageBase; }
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

    public List<AttributeBonus> getAttributeBonuses() { return attributeBonuses; }
    public boolean hasPlayerAttribute() { return !attributeBonuses.isEmpty(); }
    public Attribute getPlayerAttribute() { return attributeBonuses.isEmpty() ? null : attributeBonuses.get(0).getAttribute(); }
    public String getAttributeDisplay() { return attributeBonuses.isEmpty() ? "" : attributeBonuses.get(0).getDisplay(); }
    public String getLocalizedAttributeDisplay(LanguageManager lm) {
        return attributeBonuses.isEmpty() ? "" : lm.getString("pet." + id + ".attribute", attributeBonuses.get(0).getDisplay());
    }
    public List<PotionBonus> getPotionBonuses() { return potionBonuses; }
    public boolean hasPotionBonuses() { return !potionBonuses.isEmpty(); }

    /**
     * How many storage slots are active at the given level.
     * Scales linearly: ceil(maxSlots * level / maxLevel), minimum 1.
     */
    public int computeActiveStorageSlots(int level, int maxLevel) {
        if (storageBase <= 0) return 0;
        if (storagePerLevel == 0.0) return storageBase;
        return Math.max(1, storageBase + (int) Math.floor(storagePerLevel * level));
    }

    public int computeMaxStorageSlots(int maxLevel) {
        return computeActiveStorageSlots(maxLevel, maxLevel);
    }

    /** Total attribute bonus at this level for the first configured attribute. */
    public double getAttributeAtLevel(int level) {
        return attributeBonuses.isEmpty() ? 0.0 : attributeBonuses.get(0).getValueAtLevel(level);
    }

    /**
     * Returns the formatted bonus at this level, e.g. "2.5" or "-0.5".
     * Negative values (e.g. FALL_DAMAGE_MULTIPLIER) are already signed.
     */
    public String formatAttributeBonus(int level) {
        return formatAttributeValue(getAttributeAtLevel(level));
    }

    public String formatAttributePerLevel() {
        return attributeBonuses.isEmpty() ? formatAttributeValue(0.0) : formatAttributeValue(attributeBonuses.get(0).getPerLevel());
    }

    /** True if this attribute's value_per_level is negative (e.g. fall damage reduction). */
    public boolean isNegativeAttribute() {
        return !attributeBonuses.isEmpty() && attributeBonuses.get(0).getPerLevel() < 0;
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
