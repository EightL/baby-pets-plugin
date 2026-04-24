package com.petsplugin;

import com.petsplugin.command.PetsCommand;
import com.petsplugin.gui.GuiListener;
import com.petsplugin.listener.*;
import com.petsplugin.manager.EggManager;
import com.petsplugin.manager.IncubatorManager;
import com.petsplugin.manager.PetAdvancementManager;
import com.petsplugin.manager.PetManager;
import com.petsplugin.manager.LanguageManager;
import com.petsplugin.manager.PetSettingsManager;
import com.petsplugin.model.PetType;
import com.petsplugin.storage.PetDatabaseManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Main plugin class for PetsPlugin.
 * Standalone pet companion system with optional Fish Rework integration.
 */
public class PetsPlugin extends JavaPlugin {

    private static final int CURRENT_CONFIG_VERSION = 2;

    private PetDatabaseManager databaseManager;
    private EggManager eggManager;
    private IncubatorManager incubatorManager;
    private PetManager petManager;
    private PetSettingsManager settingsManager;
    private PetAdvancementManager advancementManager;
    private LanguageManager languageManager;

    private Map<String, PetType> petTypes = new LinkedHashMap<>();

    private int maxLevel;
    private double followDistance;
    private double teleportDistance;
    private int incubationDurationMinutes;
    private long feedCooldownMillis;
    private long petCooldownMillis;

    @Override
    public void onEnable() {
        // Save default configs
        saveDefaultConfig();
        ensureConfigDefaults();
        loadRuntimeConfigCache();

        // Load pet type definitions
        loadPetTypes();

        // Initialize managers
        databaseManager = new PetDatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("BabyPets failed to start because the database could not be initialized.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        eggManager = new EggManager(this);
        incubatorManager = new IncubatorManager(this);
        settingsManager = new PetSettingsManager(this);
        languageManager = new LanguageManager(this);
        languageManager.initialize();
        petManager = new PetManager(this);
        advancementManager = new PetAdvancementManager(this);
        petManager.reloadConfigCache();

        // Start managers
        incubatorManager.initialize();
        petManager.initialize();
        advancementManager.loadAdvancements();

        // Register listeners
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new PetInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new IncubatorListener(this), this);
        getServer().getPluginManager().registerEvents(new LootInjectListener(this), this);

        // Register commands
        PetsCommand command = new PetsCommand(this);
        getCommand("pets").setExecutor(command);
        getCommand("pets").setTabCompleter(command);

        getLogger().info("BabyPets enabled! Loaded " + petTypes.size() + " pet types.");
    }

    @Override
    public void onDisable() {
        if (advancementManager != null) advancementManager.unloadAdvancements();
        // Shutdown managers
        if (petManager != null) petManager.shutdown();
        if (incubatorManager != null) incubatorManager.shutdown();
        if (databaseManager != null) databaseManager.close();

        getLogger().info("BabyPets disabled.");
    }

    /**
     * Reload configuration and pet types.
     */
    public void reload() {
        reloadConfig();
        ensureConfigDefaults();
        loadRuntimeConfigCache();
        loadPetTypes();
        if (petManager != null) {
            petManager.reloadConfigCache();
            petManager.refreshAbilityStateForOnlinePlayers();
        }
        if (languageManager != null) {
            languageManager.reload();
        }
        if (advancementManager != null) {
            advancementManager.loadAdvancements();
        }
        getLogger().info("Configuration reloaded. " + petTypes.size() + " pet types loaded.");
    }

    private void loadRuntimeConfigCache() {
        maxLevel = getConfig().getInt("leveling.max_level", 10);
        followDistance = getConfig().getDouble("pets.follow_distance", 3.0);
        teleportDistance = getConfig().getDouble("pets.teleport_distance", 20.0);
        incubationDurationMinutes = getConfig().getInt("incubation.duration_minutes", 20);
        feedCooldownMillis = getConfig().getLong("status.feed_cooldown_seconds", 3L) * 1000L;
        petCooldownMillis = getConfig().getLong("status.pet_cooldown_seconds", 5L) * 1000L;
    }

    private void ensureConfigDefaults() {
        int version = getConfig().getInt("config_version", 0);
        boolean migrated = version < CURRENT_CONFIG_VERSION;

        // Add newly bundled config keys while preserving server-owner values.
        getConfig().options().copyDefaults(true);
        boolean addedDefaults = copyMissingConfigDefaults();

        if (migrated) {
            getConfig().set("config_version", CURRENT_CONFIG_VERSION);
        }

        if (migrated || addedDefaults) {
            saveConfig();
            reloadConfig();
        }

        if (migrated) {
            getLogger().info("Config migrated to v" + CURRENT_CONFIG_VERSION + ".");
        }
    }

    private boolean copyMissingConfigDefaults() {
        try (InputStream in = getResource("config.yml")) {
            if (in == null) {
                return false;
            }

            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String path : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(path)) {
                    continue;
                }
                if (!getConfig().isSet(path)) {
                    getConfig().set(path, defaults.get(path));
                    changed = true;
                }
            }
            return changed;
        } catch (IOException e) {
            getLogger().warning("Failed to update config defaults: " + e.getMessage());
            return false;
        }
    }

    /**
     * Load pet type definitions from pets.yml.
     */
    private void loadPetTypes() {
        FileConfiguration petsConfig = YamlContentSupport.loadYaml(this, "pets.yml");
        ConfigurationSection section = petsConfig.getConfigurationSection("pets");
        if (section == null) {
            getLogger().warning("No pet definitions found in pets.yml!");
            petTypes = Collections.emptyMap();
            return;
        }

        Map<String, PetType> types = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection petSection = section.getConfigurationSection(key);
            if (petSection != null) {
                try {
                    PetType type = new PetType(key, petSection);
                    types.put(key, type);
                } catch (Exception e) {
                    getLogger().warning("Failed to load pet type '" + key + "': " + e.getMessage());
                }
            }
        }
        petTypes = types;
    }

    // ── Getters ──

    public PetDatabaseManager getDatabaseManager() { return databaseManager; }
    public EggManager getEggManager() { return eggManager; }
    public IncubatorManager getIncubatorManager() { return incubatorManager; }
    public PetManager getPetManager() { return petManager; }
    public PetSettingsManager getSettingsManager() { return settingsManager; }
    public PetAdvancementManager getAdvancementManager() { return advancementManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public Map<String, PetType> getPetTypes() { return petTypes; }
    public int getMaxLevel() { return maxLevel; }
    public double getFollowDistance() { return followDistance; }
    public double getTeleportDistance() { return teleportDistance; }
    public int getIncubationDurationMinutes() { return incubationDurationMinutes; }
    public long getFeedCooldownMillis() { return feedCooldownMillis; }
    public long getPetCooldownMillis() { return petCooldownMillis; }
}
