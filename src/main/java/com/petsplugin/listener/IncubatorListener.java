package com.petsplugin.listener;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Handles incubator block placement, interaction, and breaking.
 */
public class IncubatorListener implements Listener {

    private final PetsPlugin plugin;

    public IncubatorListener(PetsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * When placing a Pet Incubator item, create furniture.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        if (!plugin.getIncubatorManager().isIncubatorItem(hand)) return;

        // Let the block place naturally (it's a smoker), then add furniture
        Block block = event.getBlock();
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getIncubatorManager().createIncubatorFurniture(
                    block.getLocation(), event.getPlayer().getLocation().getYaw());
        }, 1L);
    }

    /**
     * Prevent placing pet eggs as blocks (they're PLAYER_HEAD items).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEggPlace(BlockPlaceEvent event) {
        if (plugin.getEggManager().isEgg(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    /**
     * Right-click an incubator block with an egg in hand.
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SMOKER) return;

        // Check if it's an incubator block (has display entities)
        if (!plugin.getIncubatorManager().isIncubatorBlock(block)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        // Use getItemInMainHand() instead of event.getItem() — more reliable with container blocks
        ItemStack hand = player.getInventory().getItemInMainHand();

        // Check if player is holding an egg
        if (plugin.getEggManager().isEgg(hand)) {
            Rarity eggRarity = plugin.getEggManager().getEggRarity(hand);
            if (eggRarity == null) eggRarity = Rarity.COMMON;

            if (plugin.getIncubatorManager().hasActiveIncubation(block)) {
                // Already has an egg
                String msg = plugin.getConfig().getString("messages.incubator_busy",
                        "&cThis incubator already has an egg!");
                player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(msg));
                return;
            }

            // Place the egg
            boolean placed = plugin.getIncubatorManager().placeEgg(block, player, eggRarity);
            if (placed) {
                hand.setAmount(hand.getAmount() - 1);

                int minutes = plugin.getConfig().getInt("incubation.duration_minutes", 20);
                String msg = plugin.getConfig().getString("messages.egg_placed",
                        "&7Egg placed in incubator. Hatching in &e%time%&7...");
                msg = msg.replace("%time%", minutes + " minutes");
                player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(msg));

                // Sound
                player.playSound(block.getLocation(), org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.5f);
                block.getWorld().spawnParticle(org.bukkit.Particle.FLAME,
                        block.getLocation().add(0.5, 1, 0.5), 10, 0.2, 0.2, 0.2, 0.01);
            }
        } else {
            // Check if there's an incubation in progress
            var state = plugin.getIncubatorManager().getIncubatorState(block);
            if (state != null) {
                long remainingMs = state.getRemainingMs();
                long remainingSec = remainingMs / 1000;
                long minutes = remainingSec / 60;
                long seconds = remainingSec % 60;

                player.sendMessage(Component.text("Incubating: ").color(NamedTextColor.GRAY)
                        .append(Component.text("Pet Egg")
                                .color(state.getEggRarity().getColor()))
                        .append(Component.text(" — " + minutes + "m " + seconds + "s remaining")
                                .color(NamedTextColor.YELLOW)));
            } else {
                player.sendMessage(Component.text("Right-click with a Pet Egg to start incubation!")
                        .color(NamedTextColor.YELLOW));
            }
        }
    }

    /**
     * When breaking an incubator block, remove furniture and drop item.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SMOKER) return;

        if (plugin.getIncubatorManager().isIncubatorBlock(block)) {
            event.setDropItems(false); // Don't drop vanilla smoker
            plugin.getIncubatorManager().removeIncubatorFurniture(block);
        }
    }
}
