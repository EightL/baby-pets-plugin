package com.petsplugin.model;

/**
 * Pet mood/status affects gameplay slightly and is managed via feeding and petting.
 */
public enum PetStatus {
    ECSTATIC("✦ Ecstatic"),
    HAPPY("☺ Happy"),
    CONTENT("◉ Content"),
    HUNGRY("◎ Hungry"),
    SAD("☹ Sad");

    private final String display;

    PetStatus(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
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
