package com.petsplugin.manager;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetFollowMode;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PetSettingsManager {

    public static final String FOLLOW_MODE_KEY = "follow_mode";
    public static final String HIDE_OTHER_PETS_KEY = "hide_other_pets";
    public static final String PET_SOUNDS_ENABLED_KEY = "pet_sounds_enabled";
    public static final String PET_NOTIFICATIONS_ENABLED_KEY = "pet_notifications_enabled";
    public static final String LANGUAGE_KEY = "language";

    private final PetsPlugin plugin;
    private final Map<UUID, PetFollowMode> followModes = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> hideOtherPets = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> petSoundsEnabled = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> petNotificationsEnabled = new ConcurrentHashMap<>();
    private final Map<UUID, String> languageLocales = new ConcurrentHashMap<>();

    public PetSettingsManager(PetsPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadPlayerSettings(UUID uuid) {
        followModes.put(uuid, loadFollowMode(uuid));
        hideOtherPets.put(uuid, loadHideOtherPets(uuid));
        petSoundsEnabled.put(uuid, loadPetSoundsEnabled(uuid));
        petNotificationsEnabled.put(uuid, loadPetNotificationsEnabled(uuid));
        languageLocales.put(uuid, loadLanguageLocale(uuid));
    }

    public void unloadPlayerSettings(UUID uuid) {
        followModes.remove(uuid);
        hideOtherPets.remove(uuid);
        petSoundsEnabled.remove(uuid);
        petNotificationsEnabled.remove(uuid);
        languageLocales.remove(uuid);
    }

    public PetFollowMode getFollowMode(UUID uuid) {
        return followModes.computeIfAbsent(uuid, this::loadFollowMode);
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

    public boolean isPetSoundsEnabled(UUID uuid) {
        return petSoundsEnabled.computeIfAbsent(uuid, this::loadPetSoundsEnabled);
    }

    public void setPetSoundsEnabled(UUID uuid, boolean enabled) {
        petSoundsEnabled.put(uuid, enabled);
        plugin.getDatabaseManager().saveSetting(uuid, PET_SOUNDS_ENABLED_KEY, String.valueOf(enabled));
    }

    public boolean isPetNotificationsEnabled(UUID uuid) {
        return petNotificationsEnabled.computeIfAbsent(uuid, this::loadPetNotificationsEnabled);
    }

    public void setPetNotificationsEnabled(UUID uuid, boolean enabled) {
        petNotificationsEnabled.put(uuid, enabled);
        plugin.getDatabaseManager().saveSetting(uuid, PET_NOTIFICATIONS_ENABLED_KEY, String.valueOf(enabled));
    }

    public String getLanguageLocale(UUID uuid) {
        return languageLocales.computeIfAbsent(uuid, this::loadLanguageLocale);
    }

    public void setLanguageLocale(UUID uuid, String locale) {
        String resolved = locale == null || locale.isBlank()
                ? plugin.getConfig().getString("locale", "en")
                : locale;
        languageLocales.put(uuid, resolved);
        plugin.getDatabaseManager().saveSetting(uuid, LANGUAGE_KEY, resolved);
    }

    private PetFollowMode loadFollowMode(UUID uuid) {
        String raw = plugin.getDatabaseManager().loadSetting(uuid, FOLLOW_MODE_KEY, PetFollowMode.FOLLOW.getId());
        PetFollowMode mode = PetFollowMode.fromInput(raw);
        return mode == null ? PetFollowMode.FOLLOW : mode;
    }

    private boolean loadHideOtherPets(UUID uuid) {
        return Boolean.parseBoolean(plugin.getDatabaseManager().loadSetting(uuid, HIDE_OTHER_PETS_KEY, "false"));
    }

    private boolean loadPetSoundsEnabled(UUID uuid) {
        return Boolean.parseBoolean(plugin.getDatabaseManager().loadSetting(uuid, PET_SOUNDS_ENABLED_KEY, "true"));
    }

    private boolean loadPetNotificationsEnabled(UUID uuid) {
        return Boolean.parseBoolean(plugin.getDatabaseManager().loadSetting(uuid, PET_NOTIFICATIONS_ENABLED_KEY, "true"));
    }

    private String loadLanguageLocale(UUID uuid) {
        return plugin.getDatabaseManager().loadSetting(uuid, LANGUAGE_KEY, plugin.getConfig().getString("locale", "en"));
    }
}
