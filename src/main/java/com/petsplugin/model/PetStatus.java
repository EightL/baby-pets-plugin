package com.petsplugin.model;

/**
 * Pet mood/status affects gameplay slightly and is managed via feeding and petting.
 */
public enum PetStatus {
    ECSTATIC("✦", "Ecstatic"),
    HAPPY("☺", "Happy"),
    CONTENT("◉", "Content"),
    HUNGRY("◎", "Hungry"),
    SAD("☹", "Sad");

    private final String icon;
    private final String defaultName;

    PetStatus(String icon, String defaultName) {
        this.icon = icon;
        this.defaultName = defaultName;
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
