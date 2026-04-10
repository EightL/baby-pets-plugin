package com.petsplugin.model;

import java.util.UUID;

/**
 * Tracks an egg currently incubating in an incubator block.
 * Persisted to DB so it survives server restarts.
 */
public class IncubatorState {

    private int databaseId;
    private final String world;
    private final int x, y, z;
    private final UUID ownerUuid;
    private final Rarity eggRarity;
    private final long startTime;
    private final long durationMs;

    public IncubatorState(int databaseId, String world, int x, int y, int z,
                          UUID ownerUuid, Rarity eggRarity,
                          long startTime, long durationMs) {
        this.databaseId = databaseId;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.ownerUuid = ownerUuid;
        this.eggRarity = eggRarity;
        this.startTime = startTime;
        this.durationMs = durationMs;
    }

    public int getDatabaseId() { return databaseId; }
    public void setDatabaseId(int id) { this.databaseId = id; }
    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public Rarity getEggRarity() { return eggRarity; }
    public long getStartTime() { return startTime; }
    public long getDurationMs() { return durationMs; }

    public boolean isReady() {
        return System.currentTimeMillis() >= startTime + durationMs;
    }

    public long getRemainingMs() {
        return Math.max(0, (startTime + durationMs) - System.currentTimeMillis());
    }

    /** Returns incubation progress from 0.0 (just started) to 1.0 (ready). */
    public double getProgress() {
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.min(1.0, Math.max(0.0, (double) elapsed / durationMs));
    }

    /** Location key for map storage. */
    public String locationKey() {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
