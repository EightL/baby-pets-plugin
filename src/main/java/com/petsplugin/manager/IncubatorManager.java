package com.petsplugin.manager;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.IncubatorState;
import com.petsplugin.model.PetInstance;
import com.petsplugin.model.PetType;
import com.petsplugin.model.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages pet incubators — placement, tracking, hatching timers, particle effects.
 * Incubator = smoker base + iron block mask + glass dome + egg display.
 */
public class IncubatorManager {

    private final PetsPlugin plugin;
    private final Map<String, IncubatorState> activeIncubators = new ConcurrentHashMap<>();
    private BukkitTask tickTask;
    private BukkitTask particleTask;
    private long particleCounter = 0;

    public final NamespacedKey INCUBATOR_KEY;
    public final NamespacedKey INCUBATOR_ENTITY_KEY;

    public IncubatorManager(PetsPlugin plugin) {
        this.plugin = plugin;
        this.INCUBATOR_KEY = new NamespacedKey(plugin, "pet_incubator");
        this.INCUBATOR_ENTITY_KEY = new NamespacedKey(plugin, "incubator_entity");
    }

    public void initialize() {
        List<IncubatorState> states = plugin.getDatabaseManager().loadAllIncubators();
        for (IncubatorState state : states) {
            activeIncubators.put(state.locationKey(), state);
        }
        plugin.getLogger().info("Loaded " + activeIncubators.size() + " active incubators");

        // Main tick: check hatching (every second)
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        // Particle tick: every 20 seconds, check and spawn particles
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::particleTick, 100L, 400L);
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        if (particleTask != null) particleTask.cancel();
    }

    /** Creates the Pet Incubator item. */
    public ItemStack createIncubatorItem() {
        ItemStack item = new ItemStack(Material.SMOKER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Pet Incubator").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("A warm chamber for hatching").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("mysterious pet eggs.").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Place and right-click with").color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("an egg to start incubation!").color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(INCUBATOR_KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isIncubatorItem(ItemStack item) {
        if (item == null || item.getType() != Material.SMOKER) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(INCUBATOR_KEY, PersistentDataType.BYTE);
    }

    public boolean isIncubatorBlock(Block block) {
        if (block.getType() != Material.SMOKER) return false;
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 1.5, 2.0, 1.5)) {
            if (entity.getPersistentDataContainer().has(INCUBATOR_ENTITY_KEY, PersistentDataType.BYTE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create the incubator furniture at a location.
     * Design: iron block masking lower half of smoker + glass dome on top + egg display inside.
     */
    public void createIncubatorFurniture(Location location, float yaw) {
        Location center = location.clone().add(0.5, 0, 0.5);
        float snappedYaw = (Math.round(yaw / 90f) * 90f + 180f) % 360f;

        // Iron block base — covers bottom half of the smoker for an industrial look
        BlockDisplay ironBase = (BlockDisplay) location.getWorld().spawnEntity(
                center.clone().add(0, 0, 0), EntityType.BLOCK_DISPLAY);
        ironBase.setBlock(Bukkit.createBlockData(Material.IRON_BLOCK));
        ironBase.setRotation(snappedYaw, 0);

        Transformation ironTrans = ironBase.getTransformation();
        ironTrans.getScale().set(1.02f, 0.5f, 1.02f);
        ironTrans.getTranslation().set(-0.51f, 0.0f, -0.51f);
        ironBase.setTransformation(ironTrans);
        configureDisplay(ironBase);

        // Glass dome — sits on top of the smoker, short and wide
        BlockDisplay glass = (BlockDisplay) location.getWorld().spawnEntity(
                center.clone().add(0, 1.0, 0), EntityType.BLOCK_DISPLAY);
        glass.setBlock(Bukkit.createBlockData(Material.GLASS));
        glass.setRotation(snappedYaw, 0);

        Transformation glassTrans = glass.getTransformation();
        glassTrans.getScale().set(0.6f, 0.55f, 0.6f);
        glassTrans.getTranslation().set(-0.3f, 0.0f, -0.3f);
        glass.setTransformation(glassTrans);
        configureDisplay(glass);

        // Egg item display (inside the glass, shown when an egg is placed)
        ItemDisplay eggDisplay = (ItemDisplay) location.getWorld().spawnEntity(
                center.clone().add(0, 1.15, 0), EntityType.ITEM_DISPLAY);
        eggDisplay.setItemStack(new ItemStack(Material.AIR));
        eggDisplay.setRotation(snappedYaw, 0);

        Transformation eggTrans = eggDisplay.getTransformation();
        eggTrans.getScale().set(0.35f, 0.35f, 0.35f);
        eggDisplay.setTransformation(eggTrans);
        eggDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        configureDisplay(eggDisplay);
    }

    private void configureDisplay(Display display) {
        display.setPersistent(true);
        display.getPersistentDataContainer().set(INCUBATOR_ENTITY_KEY, PersistentDataType.BYTE, (byte) 1);
        display.setInvulnerable(true);
        display.setViewRange(1.0f);
        display.setBillboard(Display.Billboard.FIXED);
        display.setShadowRadius(0);
        display.setShadowStrength(0);
    }

    public void removeIncubatorFurniture(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 2.0, 2.0, 2.0)) {
            if (entity.getPersistentDataContainer().has(INCUBATOR_ENTITY_KEY, PersistentDataType.BYTE)) {
                entity.remove();
            }
        }

        String key = locationKey(block);
        IncubatorState state = activeIncubators.remove(key);
        if (state != null) {
            plugin.getDatabaseManager().deleteIncubator(state.getDatabaseId());
        }

        block.getWorld().dropItemNaturally(block.getLocation(), createIncubatorItem());
    }

    // ══════════════════════════════════════════════════════════
    //  Egg Placement & Hatching
    // ══════════════════════════════════════════════════════════

    public boolean placeEgg(Block block, Player player, Rarity eggRarity) {
        String key = locationKey(block);
        if (activeIncubators.containsKey(key)) return false;

        long durationMs = plugin.getConfig().getInt("incubation.duration_minutes", 20) * 60L * 1000L;

        IncubatorState state = new IncubatorState(
                -1, block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(),
                player.getUniqueId(), eggRarity,
                System.currentTimeMillis(), durationMs);
        plugin.getDatabaseManager().insertIncubator(state);
        activeIncubators.put(key, state);

        // Show the egg in the display — it stays visible throughout incubation
        updateEggDisplay(block, eggRarity);

        return true;
    }

    /** Update the egg item display visual. */
    private void updateEggDisplay(Block block, Rarity rarity) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 2.0, 2.0, 2.0)) {
            if (entity instanceof ItemDisplay itemDisplay
                    && entity.getPersistentDataContainer().has(INCUBATOR_ENTITY_KEY, PersistentDataType.BYTE)) {
                if (rarity != null) {
                    itemDisplay.setItemStack(plugin.getEggManager().createEgg(rarity));
                } else {
                    itemDisplay.setItemStack(new ItemStack(Material.AIR));
                }
                break;
            }
        }
    }

    /** Main hatch check tick. */
    private void tick() {
        Iterator<Map.Entry<String, IncubatorState>> it = activeIncubators.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, IncubatorState> entry = it.next();
            IncubatorState state = entry.getValue();

            if (!state.isReady()) continue;

            World world = Bukkit.getWorld(state.getWorld());
            if (world == null) continue;

            Location loc = new Location(world, state.getX(), state.getY(), state.getZ());
            if (!loc.getChunk().isLoaded()) continue;

            hatch(state, loc);
            it.remove();
        }
    }

    /** Incubation particle effects — minimal at first, increasing over time. */
    private void particleTick() {
        particleCounter++;

        for (IncubatorState state : activeIncubators.values()) {
            World world = Bukkit.getWorld(state.getWorld());
            if (world == null) continue;

            Location loc = new Location(world, state.getX(), state.getY(), state.getZ());
            if (!loc.getChunk().isLoaded()) continue;

            // Calculate progress (0.0 to 1.0)
            double progress = state.getProgress();

            // Particle intensity scales with progress
            int particleCount;
            if (progress < 0.25) {
                // Every 3rd tick cycle (60s), 2 particles
                if (particleCounter % 3 != 0) continue;
                particleCount = 2;
            } else if (progress < 0.50) {
                if (particleCounter % 2 != 0) continue;
                particleCount = 4;
            } else if (progress < 0.75) {
                particleCount = 6;
            } else {
                particleCount = 10;
            }

            Location center = loc.clone().add(0.5, 1.3, 0.5);
            world.spawnParticle(Particle.FLAME, center, particleCount, 0.15, 0.1, 0.15, 0.005);

            // Near completion: add enchant glint particles
            if (progress > 0.9) {
                world.spawnParticle(Particle.ENCHANT, center, 5, 0.2, 0.2, 0.2, 0.5);
            }
        }
    }

    private void hatch(IncubatorState state, Location location) {
        PetType type = plugin.getEggManager().rollPetType(state.getEggRarity());

        PetInstance pet = PetInstance.createNew(state.getOwnerUuid(), type.getId());
        plugin.getDatabaseManager().insertPet(pet);
        plugin.getDatabaseManager().deleteIncubator(state.getDatabaseId());
        plugin.getPetManager().refreshCache(state.getOwnerUuid());

        // Clear the egg display
        updateEggDisplay(location.getBlock(), null);

        // Effects
        location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                location.clone().add(0.5, 1, 0.5), 30, 0.5, 0.5, 0.5);
        location.getWorld().playSound(location, Sound.ENTITY_CHICKEN_EGG, 1.0f, 1.2f);
        location.getWorld().playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);

        Player owner = Bukkit.getPlayer(state.getOwnerUuid());
        if (owner != null && owner.isOnline()) {
            String msg = plugin.getConfig().getString("messages.egg_hatched",
                    "&a&lHATCH! &7Your egg hatched into a &e%pet_name%&7!");
            msg = msg.replace("%pet_name%", type.getDisplayName());
            owner.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize(msg));
        }
    }

    public int instantHatch(UUID playerUuid) {
        int hatched = 0;
        Iterator<Map.Entry<String, IncubatorState>> it = activeIncubators.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, IncubatorState> entry = it.next();
            IncubatorState state = entry.getValue();
            if (!state.getOwnerUuid().equals(playerUuid)) continue;

            World world = Bukkit.getWorld(state.getWorld());
            if (world != null) {
                Location loc = new Location(world, state.getX(), state.getY(), state.getZ());
                hatch(state, loc);
            } else {
                PetType type = plugin.getEggManager().rollPetType(state.getEggRarity());
                PetInstance pet = PetInstance.createNew(playerUuid, type.getId());
                plugin.getDatabaseManager().insertPet(pet);
                plugin.getDatabaseManager().deleteIncubator(state.getDatabaseId());
                plugin.getPetManager().refreshCache(playerUuid);

                Player owner = Bukkit.getPlayer(playerUuid);
                if (owner != null) {
                    String msg = plugin.getConfig().getString("messages.egg_hatched",
                            "&a&lHATCH! &7Your egg hatched into a &e%pet_name%&7!");
                    msg = msg.replace("%pet_name%", type.getDisplayName());
                    owner.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacyAmpersand().deserialize(msg));
                }
            }
            it.remove();
            hatched++;
        }
        return hatched;
    }

    public IncubatorState getIncubatorState(Block block) {
        return activeIncubators.get(locationKey(block));
    }

    public boolean hasActiveIncubation(Block block) {
        return activeIncubators.containsKey(locationKey(block));
    }

    private String locationKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
