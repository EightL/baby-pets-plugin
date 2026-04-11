package com.petsplugin.manager;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetFollowMode;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PetSettingsManager {

    public static final String FOLLOW_MODE_KEY = "follow_mode";
    public static final String HIDE_OTHER_PETS_KEY = "hide_other_pets";

    private final PetsPlugin plugin;
    private final Map<UUID, PetFollowMode> followModes = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> hideOtherPets = new ConcurrentHashMap<>();

    public PetSettingsManager(PetsPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadPlayerSettings(UUID uuid) {
        followModes.put(uuid, loadFollowMode(uuid));
        hideOtherPets.put(uuid, loadHideOtherPets(uuid));
    }

    public void unloadPlayerSettings(UUID uuid) {
        followModes.remove(uuid);
        hideOtherPets.remove(uuid);
    }

    public PetFollowMode getFollowMode(UUID uuid) {
        return followModes.computeIfAbsent(uuid, this::loadFollowMode);
    }

    public boolean isFollowMode(UUID uuid) {
        return getFollowMode(uuid) == PetFollowMode.FOLLOW;
    }

    public boolean isStayMode(UUID uuid) {
        return getFollowMode(uuid) == PetFollowMode.STAY;
    }

    public void setFollowMode(UUID uuid, PetFollowMode mode) {
        PetFollowMode resolved = mode == null ? PetFollowMode.FOLLOW : mode;
        followModes.put(uuid, resolved);
        plugin.getDatabaseManager().saveSetting(uuid, FOLLOW_MODE_KEY, resolved.getId());
    }

    public boolean isHideOtherPetsEnabled(UUID uuid) {
        return hideOtherPets.computeIfAbsent(uuid, this::loadHideOtherPets);
    }

    public void setHideOtherPetsEnabled(UUID uuid, boolean enabled) {
        hideOtherPets.put(uuid, enabled);
        plugin.getDatabaseManager().saveSetting(uuid, HIDE_OTHER_PETS_KEY, String.valueOf(enabled));
    }

    private PetFollowMode loadFollowMode(UUID uuid) {
        String raw = plugin.getDatabaseManager().loadSetting(uuid, FOLLOW_MODE_KEY, PetFollowMode.FOLLOW.getId());
        PetFollowMode mode = PetFollowMode.fromInput(raw);
        return mode == null ? PetFollowMode.FOLLOW : mode;
    }

    private boolean loadHideOtherPets(UUID uuid) {
        return Boolean.parseBoolean(plugin.getDatabaseManager().loadSetting(uuid, HIDE_OTHER_PETS_KEY, "false"));
    }
}
