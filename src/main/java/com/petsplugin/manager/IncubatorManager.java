package com.petsplugin.manager;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.IncubatorState;
import com.petsplugin.model.PetInstance;
import com.petsplugin.model.PetType;
import com.petsplugin.model.Rarity;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages pet incubators — placement, tracking, hatching timers, particle effects.
 * Incubator = iron trapdoor anchor + display-entity shell + egg display.
 */
public class IncubatorManager {

    private static final String[] INCUBATOR_RECIPE_SHAPE = {"CBC", "IGI", "FDF"};

    private final PetsPlugin plugin;
    private final Map<String, IncubatorState> activeIncubators = new ConcurrentHashMap<>();
    private BukkitTask tickTask;
    private BukkitTask particleTask;
    private long particleCounter = 0;
    private Material incubatorFlowerIngredient = Material.DANDELION;

    private final NamespacedKey INCUBATOR_KEY;
    private final NamespacedKey INCUBATOR_ENTITY_KEY;
    private final NamespacedKey INCUBATOR_ANCHOR_KEY;

    public IncubatorManager(PetsPlugin plugin) {
        this.plugin = plugin;
        this.INCUBATOR_KEY = new NamespacedKey(plugin, "pet_incubator");
        this.INCUBATOR_ENTITY_KEY = new NamespacedKey(plugin, "incubator_entity");
        this.INCUBATOR_ANCHOR_KEY = new NamespacedKey(plugin, "incubator_anchor");
    }

    public void initialize() {
        registerIncubatorRecipe();
        List<IncubatorState> states = plugin.getDatabaseManager().loadAllIncubators();
        for (IncubatorState state : states) {
            activeIncubators.put(state.locationKey(), state);
        }
        plugin.getLogger().info("Loaded " + activeIncubators.size() + " active incubators");

        for (Player player : Bukkit.getOnlinePlayers()) {
            discoverIncubatorRecipe(player);
        }

        // Main tick: check hatching (every second)
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        // Particle tick: every 20 seconds, check and spawn particles
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::particleTick, 100L, 400L);
    }

    public NamespacedKey getIncubatorKey() {
        return INCUBATOR_KEY;
    }

    public NamespacedKey getIncubatorEntityKey() {
        return INCUBATOR_ENTITY_KEY;
    }

    public NamespacedKey getIncubatorAnchorKey() {
        return INCUBATOR_ANCHOR_KEY;
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

    public void registerIncubatorRecipe() {
        plugin.getServer().removeRecipe(INCUBATOR_KEY);
        incubatorFlowerIngredient = resolveIncubatorFlowerIngredient(true);

        ShapedRecipe recipe = new ShapedRecipe(INCUBATOR_KEY, createIncubatorItem());
        recipe.shape(getIncubatorRecipeShape());
        for (Map.Entry<Character, Material> entry : getIncubatorRecipeIngredients().entrySet()) {
            recipe.setIngredient(entry.getKey(), entry.getValue());
        }
        plugin.getServer().addRecipe(recipe);
    }

    public String[] getIncubatorRecipeShape() {
        return INCUBATOR_RECIPE_SHAPE.clone();
    }

    public Map<Character, Material> getIncubatorRecipeIngredients() {
        Map<Character, Material> ingredients = new LinkedHashMap<>();
        ingredients.put('C', Material.COPPER_BLOCK);
        ingredients.put('B', Material.LIGHTNING_ROD);
        ingredients.put('I', Material.IRON_INGOT);
        ingredients.put('G', Material.GLASS);
        ingredients.put('F', Material.IRON_BLOCK);
        ingredients.put('D', incubatorFlowerIngredient);
        return ingredients;
    }

    private Material resolveIncubatorFlowerIngredient(boolean logFallback) {
        Material flower = Material.matchMaterial("GOLDEN_DANDELION");
        if (flower != null) {
            return flower;
        }
        if (logFallback) {
            plugin.getLogger().warning("GOLDEN_DANDELION is unavailable on this API target; incubator recipe is using DANDELION as a fallback.");
        }
        return Material.DANDELION;
    }

    public void discoverIncubatorRecipe(Player player) {
        player.discoverRecipe(INCUBATOR_KEY);
    }

    public boolean isIncubatorItem(ItemStack item) {
        if (item == null || item.getType() != Material.SMOKER) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(INCUBATOR_KEY, PersistentDataType.BYTE);
    }

    public boolean isIncubatorBlock(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 2.0, 2.0, 2.0)) {
            if (isIncubatorEntityForBlock(entity, block)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create the incubator furniture at a location using the exported Axiom shell.
     */
    public void createIncubatorFurniture(Location location, float yaw) {
        Location center = location.clone().add(0.5, 0, 0.5);
        String anchorKey = locationKey(location.getBlock());
        float snappedYaw = (Math.round(yaw / 90f) * 90f + 180f) % 360f;

        // Axiom export translated from block-corner space into center-pivot space.
        spawnPart(center, snappedYaw, Bukkit.createBlockData(Material.GLASS),
                0.875f, 0.865f, 0.875f,
            -0.4375f, 0.01625f, -0.4375f,
            anchorKey);

        float[][] legTranslations = {
                {-0.498125f, 0.01f, -0.49625f},
                {-0.498125f, 0.00625f, 0.375f},
                {0.375f, 0.001875f, 0.375f},
                {0.375f, 0.001875f, -0.498125f}
        };
        for (float[] translation : legTranslations) {
            spawnPart(center, snappedYaw, Bukkit.createBlockData(Material.IRON_BLOCK),
                    0.1155f, 0.88f, 0.1175f,
                    translation[0], translation[1], translation[2],
                    anchorKey);
        }

        spawnPart(center, snappedYaw, Bukkit.createBlockData(Material.WAXED_CUT_COPPER),
                1.0f, 0.125f, 1.0f,
            -0.506875f, 0.875f, -0.5f,
            anchorKey);
        spawnPart(center, snappedYaw, Bukkit.createBlockData("minecraft:lightning_rod[facing=down,powered=false]"),
                -0.625f, -0.5625f, 0.625f,
            0.293125f, 1.5625f, -0.321875f,
            anchorKey);

        // Egg item display (inside the shell, shown when an egg is placed)
        ItemDisplay eggDisplay = (ItemDisplay) location.getWorld().spawnEntity(
            center.clone().add(0, 0.42, 0), EntityType.ITEM_DISPLAY);
        eggDisplay.setItemStack(new ItemStack(Material.AIR));
        eggDisplay.setRotation(snappedYaw, 0);

        Transformation eggTrans = eggDisplay.getTransformation();
        eggTrans.getScale().set(0.8f, 0.8f, 0.8f);
        eggDisplay.setTransformation(eggTrans);
        eggDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        configureDisplay(eggDisplay, anchorKey);
    }

    private void spawnPart(Location center, float yaw, BlockData blockData,
                           float sx, float sy, float sz,
                           float tx, float ty, float tz,
                           String anchorKey) {
        BlockDisplay part = (BlockDisplay) center.getWorld().spawnEntity(center.clone(), EntityType.BLOCK_DISPLAY);
        part.setBlock(blockData);
        part.setRotation(yaw, 0f);

        Transformation transformation = part.getTransformation();
        transformation.getScale().set(sx, sy, sz);
        transformation.getTranslation().set(tx, ty, tz);
        part.setTransformation(transformation);
        configureDisplay(part, anchorKey);
    }

    private void configureDisplay(Display display, String anchorKey) {
        display.setPersistent(true);
        display.getPersistentDataContainer().set(INCUBATOR_ENTITY_KEY, PersistentDataType.BYTE, (byte) 1);
        display.getPersistentDataContainer().set(INCUBATOR_ANCHOR_KEY, PersistentDataType.STRING, anchorKey);
        display.setInvulnerable(true);
        display.setViewRange(1.0f);
        display.setBillboard(Display.Billboard.FIXED);
        display.setShadowRadius(0);
        display.setShadowStrength(0);
    }

    public void removeIncubatorFurniture(Block block) {
        String key = locationKey(block);
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 2.0, 2.0, 2.0)) {
            if (isIncubatorEntityForBlock(entity, block)) {
                entity.remove();
            }
        }

        // Remove the hidden anchor block used for interaction/breaking.
        if (block.getType() != Material.AIR) {
            block.setType(Material.AIR, false);
        }

        IncubatorState state = activeIncubators.remove(key);
        if (state != null) {
            plugin.getDatabaseManager().deleteIncubator(state.getDatabaseId());
            // Return the incubating egg when the incubator is broken mid-cycle.
            block.getWorld().dropItemNaturally(block.getLocation(), plugin.getEggManager().createEgg(state.getEggRarity()));
        }

        block.getWorld().dropItemNaturally(block.getLocation(), createIncubatorItem());
    }

    // ══════════════════════════════════════════════════════════
    //  Egg Placement & Hatching
    // ══════════════════════════════════════════════════════════

    public boolean placeEgg(Block block, Player player, Rarity eggRarity) {
        String key = locationKey(block);
        if (activeIncubators.containsKey(key)) return false;

        long durationMs = plugin.getIncubationDurationMinutes() * 60L * 1000L;

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
                    && isIncubatorEntityForBlock(entity, block)) {
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

            if (plugin.getConfig().getBoolean("incubation.require_simulation_distance", true)) {
                Location center = loc.clone().add(0.5, 0.5, 0.5);
                // Approximate simulation distance check via nearby players in ticking range.
                if (world.getNearbyPlayers(center, 128.0).isEmpty()) {
                    continue;
                }
            }

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
        plugin.getPetManager().ensurePersistentAppearance(pet, type);
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
            sendHatchMessage(owner, type);
            plugin.getAdvancementManager().handlePetHatched(owner, pet, type);
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
                plugin.getPetManager().ensurePersistentAppearance(pet, type);
                plugin.getDatabaseManager().insertPet(pet);
                plugin.getDatabaseManager().deleteIncubator(state.getDatabaseId());
                plugin.getPetManager().refreshCache(playerUuid);

                Player owner = Bukkit.getPlayer(playerUuid);
                if (owner != null) {
                    sendHatchMessage(owner, type);
                    plugin.getAdvancementManager().handlePetHatched(owner, pet, type);
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

    private boolean isIncubatorEntityForBlock(Entity entity, Block block) {
        if (!entity.getPersistentDataContainer().has(INCUBATOR_ENTITY_KEY, PersistentDataType.BYTE)) {
            return false;
        }

        String key = locationKey(block);
        String anchor = entity.getPersistentDataContainer().get(INCUBATOR_ANCHOR_KEY, PersistentDataType.STRING);
        return key.equals(anchor);
    }

    private void sendHatchMessage(Player owner, PetType type) {
        plugin.getPetManager().sendPetNotification(owner,
            "messages.egg_hatched",
            "&a&lHATCH! &7Your egg hatched into a &e%pet_name%&7!",
            java.util.Map.of("%pet_name%", type.getDisplayName()));
        owner.sendMessage(
                Component.text("Click to view in collection")
                        .color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false)
                        .clickEvent(ClickEvent.runCommand("/pets"))
                        .hoverEvent(HoverEvent.showText(Component.text("Open pet collection")
                                .color(NamedTextColor.YELLOW)))
        );
    }
}
