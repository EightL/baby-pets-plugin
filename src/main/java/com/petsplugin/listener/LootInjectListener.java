package com.petsplugin.listener;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.Rarity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * Injects pet eggs into structure chest loot tables.
 */
public class LootInjectListener implements Listener {

    private final PetsPlugin plugin;
    private final Random random = new Random();

    public LootInjectListener(PetsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        if (!plugin.getConfig().getBoolean("eggs.loot_injection.enabled", true)) return;

        double baseChance = plugin.getConfig().getDouble("eggs.loot_injection.base_chance", 8.0);
        double roll = random.nextDouble() * 100.0;

        if (roll >= baseChance) return;

        // Roll rarity
        Rarity rarity = plugin.getEggManager().rollRarity();

        // Create egg and add to loot
        ItemStack egg = plugin.getEggManager().createEgg(rarity);
        event.getLoot().add(egg);
    }
}
