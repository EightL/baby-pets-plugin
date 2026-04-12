package com.petsplugin.model;

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
}
