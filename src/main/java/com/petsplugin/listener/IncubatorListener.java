package com.petsplugin.listener;

import com.petsplugin.PetsPlugin;
import com.petsplugin.gui.PetCollectionGUI;
import com.petsplugin.model.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.TrapDoor;
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
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        if (!plugin.getIncubatorManager().isIncubatorItem(hand)) return;

        // Let the smoker place, then replace it with an iron trapdoor anchor like other furniture systems.
        Block block = event.getBlock();
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (block.getType() != Material.SMOKER && block.getType() != Material.IRON_TRAPDOOR) {
                return;
            }

            if (block.getType() == Material.SMOKER) {
                block.setType(Material.IRON_TRAPDOOR, false);
                if (block.getBlockData() instanceof TrapDoor trapDoor) {
                    trapDoor.setHalf(Bisected.Half.BOTTOM);
                    trapDoor.setOpen(false);
                    block.setBlockData(trapDoor, false);
                }
            }

            if (block.getType() == Material.IRON_TRAPDOOR) {
                plugin.getIncubatorManager().createIncubatorFurniture(
                        block.getLocation(), event.getPlayer().getLocation().getYaw());
            }
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
     * Right-clicking a pet egg opens the pet collection GUI, except on incubators.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEggRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!plugin.getEggManager().isEgg(hand)) return;

        Block clicked = event.getClickedBlock();
        if (clicked != null && plugin.getIncubatorManager().isIncubatorBlock(clicked)) {
            return;
        }

        event.setCancelled(true);
        new PetCollectionGUI(plugin, player).open(player);
    }

    /**
     * Right-click an incubator block with an egg in hand.
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

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
                plugin.getPetManager().sendPetNotification(player,
                    "messages.incubator_busy",
                    "&cThis incubator already has an egg!",
                    null,
                    false);
                return;
            }

            // Place the egg
            boolean placed = plugin.getIncubatorManager().placeEgg(block, player, eggRarity);
            if (placed) {
                hand.setAmount(hand.getAmount() - 1);
                plugin.getAdvancementManager().handleEggPlaced(player);

                int minutes = plugin.getIncubationDurationMinutes();
                plugin.getPetManager().sendPetNotification(player,
                    "messages.egg_placed",
                    "&7Egg placed in incubator. Hatching in &e%time%&7...",
                    java.util.Map.of("%time%", minutes + " minutes"),
                    false);

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
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!plugin.getIncubatorManager().isIncubatorBlock(block)) return;

        event.setDropItems(false);
        plugin.getIncubatorManager().removeIncubatorFurniture(block);
    }
}
