package com.petsplugin;

import com.petsplugin.command.PetsCommand;
import com.petsplugin.gui.GuiListener;
import com.petsplugin.integration.FishReworkHook;
import com.petsplugin.listener.*;
import com.petsplugin.manager.EggManager;
import com.petsplugin.manager.IncubatorManager;
import com.petsplugin.manager.PetAdvancementManager;
import com.petsplugin.manager.PetManager;
import com.petsplugin.manager.PetSettingsManager;
import com.petsplugin.model.PetType;
import com.petsplugin.storage.PetDatabaseManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Main plugin class for PetsPlugin.
 * Standalone pet companion system with optional Fish Rework integration.
 */
public class PetsPlugin extends JavaPlugin {

    private PetDatabaseManager databaseManager;
    private EggManager eggManager;
    private IncubatorManager incubatorManager;
    private PetManager petManager;
    private PetSettingsManager settingsManager;
    private PetAdvancementManager advancementManager;
    private FishReworkHook fishReworkHook;

    private Map<String, PetType> petTypes = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        // Save default configs
        saveDefaultConfig();
        saveResource("pets.yml", false);

        // Load pet type definitions
        loadPetTypes();

        // Initialize managers
        databaseManager = new PetDatabaseManager(this);
        databaseManager.initialize();

        eggManager = new EggManager(this);
        incubatorManager = new IncubatorManager(this);
        settingsManager = new PetSettingsManager(this);
        petManager = new PetManager(this);
        advancementManager = new PetAdvancementManager(this);

        // Initialize integration
        fishReworkHook = new FishReworkHook(this);
        fishReworkHook.initialize();

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
        loadPetTypes();
        if (advancementManager != null) {
            advancementManager.loadAdvancements();
        }
        getLogger().info("Configuration reloaded. " + petTypes.size() + " pet types loaded.");
    }

    /**
     * Load pet type definitions from pets.yml.
     */
    private void loadPetTypes() {
        File petsFile = new File(getDataFolder(), "pets.yml");
        if (!petsFile.exists()) {
            saveResource("pets.yml", false);
        }

        FileConfiguration petsConfig = YamlConfiguration.loadConfiguration(petsFile);
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
    public FishReworkHook getFishReworkHook() { return fishReworkHook; }
    public Map<String, PetType> getPetTypes() { return petTypes; }
}
