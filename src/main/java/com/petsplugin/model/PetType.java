package com.petsplugin.model;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.Locale;

/**
 * Represents a pet species loaded from pets.yml.
 * Pets give the PLAYER an attribute bonus that scales with pet level.
 */
public class PetType {

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

    // Player attribute this pet provides
    private final Attribute playerAttribute;
    private final double attributePerLevel;
    private final String attributeDisplay;   // Human-readable label

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

        // Player attribute
        ConfigurationSection attr = section.getConfigurationSection("player_attribute");
        if (attr != null) {
            this.playerAttribute = parseAttribute(attr.getString("type", "MAX_HEALTH"));
            this.attributePerLevel = attr.getDouble("value_per_level", 0.5);
            this.attributeDisplay = attr.getString("display", "Unknown");
        } else {
            this.playerAttribute = Attribute.MAX_HEALTH;
            this.attributePerLevel = 0.5;
            this.attributeDisplay = "+HP";
        }
    }

    private Attribute parseAttribute(String name) {
        try {
            return Attribute.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Attribute.MAX_HEALTH;
        }
    }

    // Getters
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public EntityType getEntityType() { return entityType; }
    public Rarity getRarity() { return rarity; }
    public String getDescription() { return description; }
    public Material getIcon() { return icon; }
    public boolean isBaby() { return baby; }
    public boolean isFlying() { return flying; }
    public boolean isAquatic() { return aquatic; }
    public PetMovementType getMovementType() {
        if (aquatic) {
            return PetMovementType.WATER;
        }
        if (flying) {
            return PetMovementType.FLYING;
        }
        return PetMovementType.GROUND;
    }

    public Attribute getPlayerAttribute() { return playerAttribute; }
    public double getAttributePerLevel() { return attributePerLevel; }
    public String getAttributeDisplay() { return attributeDisplay; }

    /** Total attribute bonus at this level. */
    public double getAttributeAtLevel(int level) {
        return level * attributePerLevel;
    }

    public String formatAttributeBonus(int level) {
        return formatAttributeValue(getAttributeAtLevel(level));
    }

    public String formatAttributePerLevel() {
        return formatAttributeValue(attributePerLevel);
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
