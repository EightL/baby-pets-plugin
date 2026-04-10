package com.petsplugin.integration;

import com.petsplugin.PetsPlugin;
import org.bukkit.Bukkit;

/**
 * Optional integration hook for Fish Rework.
 * Detects if Fish Rework is installed and enables cross-plugin features.
 */
public class FishReworkHook {

    private final PetsPlugin plugin;
    private boolean fishReworkPresent;

    public FishReworkHook(PetsPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        fishReworkPresent = Bukkit.getPluginManager().getPlugin("FishRework") != null;
        if (fishReworkPresent) {
            plugin.getLogger().info("Fish Rework detected! Enabling integration features.");
        } else {
            plugin.getLogger().info("Fish Rework not found. Running in standalone mode.");
        }
    }

    public boolean isFishReworkPresent() {
        return fishReworkPresent;
    }

    /**
     * Check if we should accept display_case blocks as incubators.
     */
    public boolean useDisplayCaseAsIncubator() {
        return fishReworkPresent
                && plugin.getConfig().getBoolean("integration.fish_rework.use_display_case_as_incubator", true);
    }
}
