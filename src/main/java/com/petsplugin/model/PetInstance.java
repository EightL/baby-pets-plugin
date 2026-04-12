package com.petsplugin.model;

import java.util.UUID;

/**
 * Represents a single pet instance owned by a player.
 * Stored in the database and loaded into memory.
 */
public class PetInstance {

    private int databaseId;
    private final UUID ownerUuid;
    private final String petTypeId;
    private String nickname;
    private int level;
    private double xp;
    private boolean selected;
    private final long obtainedAt;
    private PetStatus status;
    private String appearanceVariant;
    private String appearanceSoundVariant;

    // Runtime-only
    private transient UUID entityUuid;

    public PetInstance(int databaseId, UUID ownerUuid, String petTypeId,
                       String nickname, int level, double xp,
                       boolean selected, long obtainedAt, PetStatus status,
                       String appearanceVariant, String appearanceSoundVariant) {
        this.databaseId = databaseId;
        this.ownerUuid = ownerUuid;
        this.petTypeId = petTypeId;
        this.nickname = nickname;
        this.level = level;
        this.xp = xp;
        this.selected = selected;
        this.obtainedAt = obtainedAt;
        this.status = status;
        this.appearanceVariant = appearanceVariant;
        this.appearanceSoundVariant = appearanceSoundVariant;
    }

    /** Create a new pet instance (for first-time creation). */
    public static PetInstance createNew(UUID ownerUuid, String petTypeId) {
        return new PetInstance(-1, ownerUuid, petTypeId, null, 1, 0.0,
                false, System.currentTimeMillis(), PetStatus.CONTENT, null, null);
    }

    // Getters & Setters
    public int getDatabaseId() { return databaseId; }
    public void setDatabaseId(int databaseId) { this.databaseId = databaseId; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getPetTypeId() { return petTypeId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public double getXp() { return xp; }
    public void setXp(double xp) { this.xp = xp; }

    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    public long getObtainedAt() { return obtainedAt; }

    public PetStatus getStatus() { return status; }
    public void setStatus(PetStatus status) { this.status = status; }

    public String getAppearanceVariant() { return appearanceVariant; }
    public void setAppearanceVariant(String appearanceVariant) { this.appearanceVariant = appearanceVariant; }

    public String getAppearanceSoundVariant() { return appearanceSoundVariant; }
    public void setAppearanceSoundVariant(String appearanceSoundVariant) { this.appearanceSoundVariant = appearanceSoundVariant; }

    public UUID getEntityUuid() { return entityUuid; }
    public void setEntityUuid(UUID entityUuid) { this.entityUuid = entityUuid; }

    /** Returns the display name: nickname if set, otherwise the pet type display name. */
    public String getDisplayName(PetType type) {
        return nickname != null && !nickname.isBlank() ? nickname : type.getDisplayName();
    }
}
