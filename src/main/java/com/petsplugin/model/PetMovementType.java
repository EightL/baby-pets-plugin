package com.petsplugin.model;

import com.petsplugin.manager.LanguageManager;

public enum PetMovementType {
    GROUND("Ground"),
    FLYING("Flying"),
    WATER("Water");

    private final String displayName;

    PetMovementType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLocalizedDisplayName(LanguageManager languageManager) {
        return languageManager.getString("petmovement." + name().toLowerCase(), displayName);
    }
}
