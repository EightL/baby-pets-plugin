package com.petsplugin.model;

/**
 * Pet mood/status affects gameplay slightly and is managed via feeding and petting.
 */
public enum PetStatus {
    ECSTATIC("✦", "Ecstatic", 1.20, 1.25),
    HAPPY("☺", "Happy", 1.10, 1.10),
    CONTENT("◉", "Content", 1.00, 1.00),
    HUNGRY("◎", "Hungry", 0.75, 0.75),
    SAD("☹", "Sad", 0.50, 0.50);

    private final String icon;
    private final String defaultName;
    private final double defaultAbilityMultiplier;
    private final double defaultXpMultiplier;

    PetStatus(String icon, String defaultName, double defaultAbilityMultiplier, double defaultXpMultiplier) {
        this.icon = icon;
        this.defaultName = defaultName;
        this.defaultAbilityMultiplier = defaultAbilityMultiplier;
        this.defaultXpMultiplier = defaultXpMultiplier;
    }

    public String getDisplay() {
        return icon + " " + defaultName;
    }

    public String getIcon() {
        return icon;
    }

    public String getDefaultName() {
        return defaultName;
    }

    public double getDefaultAbilityMultiplier() {
        return defaultAbilityMultiplier;
    }

    public double getDefaultXpMultiplier() {
        return defaultXpMultiplier;
    }

    public boolean canImprove() {
        return this != ECSTATIC;
    }

    /** Get the next better status (capped at ECSTATIC). */
    public PetStatus better() {
        int idx = ordinal() - 1;
        if (idx < 0) idx = 0;
        return values()[idx];
    }

    /** Get the next worse status (capped at SAD). */
    public PetStatus worse() {
        int idx = ordinal() + 1;
        if (idx >= values().length) idx = values().length - 1;
        return values()[idx];
    }
}
