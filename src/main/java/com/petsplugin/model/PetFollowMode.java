package com.petsplugin.model;

import com.petsplugin.manager.LanguageManager;

public enum PetFollowMode {
    FOLLOW("follow", "Follow"),
    STAY("stay", "Stay");

    private final String id;
    private final String displayName;

    PetFollowMode(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLocalizedDisplayName(LanguageManager lm) {
        return lm.getString("pet.mode." + this.name(), displayName);
    }

    public static PetFollowMode fromInput(String input) {
        if (input == null) return null;

        String normalized = input.trim().toLowerCase();
        return switch (normalized) {
            case "follow", "following", "1" -> FOLLOW;
            case "stay", "staying", "wait", "2" -> STAY;
            default -> null;
        };
    }
}
