package com.petsplugin.manager;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetInstance;
import com.petsplugin.model.PetFollowMode;
import com.petsplugin.model.PetMovementType;
import com.petsplugin.model.PetStatus;
import com.petsplugin.model.PetType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Core pet management — spawning, despawning, following, teleporting,
 * player attribute bonuses, time-based leveling, status, and petting.
 */
public class PetManager {

    private static final int MAX_NICKNAME_LENGTH = 64;

    private final PetsPlugin plugin;

    private final Map<UUID, UUID> activePetEntities = new ConcurrentHashMap<>();
    private final Map<UUID, PetInstance> activePets = new ConcurrentHashMap<>();
    private final Map<UUID, List<PetInstance>> playerPetsCache = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> hoverNameDisplays = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> viewerHoverTargets = new ConcurrentHashMap<>();
    private final Map<UUID, Long> idleEmoteAt = new ConcurrentHashMap<>();

    public final NamespacedKey PET_ENTITY_KEY;
    public final NamespacedKey PET_OWNER_KEY;
    public final NamespacedKey PET_TYPE_KEY;
    public final NamespacedKey PET_NAME_DISPLAY_KEY;

    /** NamespacedKey used for player attribute modifiers applied by pets. */
    public final NamespacedKey PET_ATTRIBUTE_KEY;

    private BukkitTask followTask;
    private BukkitTask xpTask;
    private BukkitTask hoverNameTask;
    private BukkitTask hoverNamePositionTask;

    public PetManager(PetsPlugin plugin) {
        this.plugin = plugin;
        this.PET_ENTITY_KEY = new NamespacedKey(plugin, "pet_entity");
        this.PET_OWNER_KEY = new NamespacedKey(plugin, "pet_owner");
        this.PET_TYPE_KEY = new NamespacedKey(plugin, "pet_type");
        this.PET_NAME_DISPLAY_KEY = new NamespacedKey(plugin, "pet_name_display");
        this.PET_ATTRIBUTE_KEY = new NamespacedKey(plugin, "pet_attribute_bonus");
    }

    public void initialize() {
        followTask = Bukkit.getScheduler().runTaskTimer(plugin, this::followTick, 10L, 5L);
        // XP task: every 60 seconds
        long xpInterval = plugin.getConfig().getLong("leveling.xp_interval_ticks", 1200L);
        xpTask = Bukkit.getScheduler().runTaskTimer(plugin, this::xpTick, xpInterval, xpInterval);
        hoverNameTask = Bukkit.getScheduler().runTaskTimer(plugin, this::hoverNameTick, 10L, 2L);
        hoverNamePositionTask = Bukkit.getScheduler().runTaskTimer(plugin, this::hoverNamePositionTick, 10L, 1L);
    }

    public void shutdown() {
        if (followTask != null) followTask.cancel();
        if (xpTask != null) xpTask.cancel();
        if (hoverNameTask != null) hoverNameTask.cancel();
        if (hoverNamePositionTask != null) hoverNamePositionTask.cancel();

        for (UUID playerUuid : new ArrayList<>(activePetEntities.keySet())) {
            despawnPet(playerUuid, false);
        }

        for (UUID viewerUuid : new ArrayList<>(viewerHoverTargets.keySet())) {
            clearViewerHoverTarget(viewerUuid);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Cache
    // ══════════════════════════════════════════════════════════

    public List<PetInstance> loadPlayerPets(UUID uuid) {
        List<PetInstance> pets = plugin.getDatabaseManager().loadPets(uuid);
        playerPetsCache.put(uuid, pets);
        
        // Re-synchronize active pet reference to prevent desyncs from DB updates
        PetInstance active = activePets.get(uuid);
        if (active != null) {
            for (PetInstance p : pets) {
                if (p.getDatabaseId() == active.getDatabaseId()) {
                    p.setEntityUuid(active.getEntityUuid());
                    activePets.put(uuid, p);
                    break;
                }
            }
        }
        return pets;
    }

    public List<PetInstance> getPlayerPets(UUID uuid) {
        return playerPetsCache.computeIfAbsent(uuid, this::loadPlayerPets);
    }

    public void clearCache(UUID uuid) { playerPetsCache.remove(uuid); }

    public void refreshCache(UUID uuid) { loadPlayerPets(uuid); }

    // ══════════════════════════════════════════════════════════
    //  Selection
    // ══════════════════════════════════════════════════════════

    public void selectPet(UUID playerUuid, PetInstance pet) {
        despawnPet(playerUuid, true);

        List<PetInstance> pets = getPlayerPets(playerUuid);
        for (PetInstance p : pets) {
            if (p.isSelected()) {
                p.setSelected(false);
                plugin.getDatabaseManager().updatePet(p);
            }
        }

        pet.setSelected(true);
        plugin.getDatabaseManager().updatePet(pet);
        activePets.put(playerUuid, pet);

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            spawnPet(player, pet);
        }
    }

    public void deselectPet(UUID playerUuid) {
        despawnPet(playerUuid, true);

        PetInstance active = activePets.remove(playerUuid);
        if (active != null) {
            active.setSelected(false);
            plugin.getDatabaseManager().updatePet(active);
        }

        List<PetInstance> pets = getPlayerPets(playerUuid);
        for (PetInstance p : pets) {
            if (p.isSelected()) {
                p.setSelected(false);
                plugin.getDatabaseManager().updatePet(p);
            }
        }

        // Remove player attribute bonus
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            removePlayerAttribute(player);
        }
    }

    public PetInstance getActivePet(UUID playerUuid) { return activePets.get(playerUuid); }

    public PetInstance getSelectedPet(UUID playerUuid) {
        for (PetInstance p : getPlayerPets(playerUuid)) {
            if (p.isSelected()) return p;
        }
        return null;
    }

    public void setFollowMode(Player player, PetFollowMode mode) {
        PetFollowMode resolved = mode == null ? PetFollowMode.FOLLOW : mode;
        plugin.getSettingsManager().setFollowMode(player.getUniqueId(), resolved);

        Entity entity = findActivePetEntity(player.getUniqueId());
        if (entity != null) {
            applyPetModePosture(player.getUniqueId(), entity);
        }

        String path = resolved == PetFollowMode.FOLLOW
                ? "messages.pet_mode_follow"
                : "messages.pet_mode_stay";
        String fallback = resolved == PetFollowMode.FOLLOW
                ? "&aYour pet will follow you again."
                : "&eYour pet is staying put.";
        String msg = plugin.getConfig().getString(path, fallback);
        player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(msg));
    }

    public void setHideOtherPets(Player player, boolean enabled) {
        plugin.getSettingsManager().setHideOtherPetsEnabled(player.getUniqueId(), enabled);
        refreshPetVisibility(player);

        String path = enabled
                ? "messages.hide_other_pets_enabled"
                : "messages.hide_other_pets_disabled";
        String fallback = enabled
                ? "&aOther players' pets are now hidden."
                : "&eOther players' pets are visible again.";
        String msg = plugin.getConfig().getString(path, fallback);
        player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(msg));
    }

    // ══════════════════════════════════════════════════════════
    //  Spawning & Despawning
    // ══════════════════════════════════════════════════════════

    public void spawnPet(Player player, PetInstance pet) {
        PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
        if (type == null) {
            plugin.getLogger().warning("Unknown pet type: " + pet.getPetTypeId());
            return;
        }

        despawnPet(player.getUniqueId(), false);

        Location spawnLoc = findSafeSpawnLocation(player.getLocation());
        Entity entity = player.getWorld().spawnEntity(spawnLoc, type.getEntityType());

        if (entity instanceof Mob mob) {
            mob.setInvulnerable(true);
            mob.setAI(true);
            mob.setAware(true);
            mob.setAggressive(false);
            mob.setTarget(null);
            mob.setCollidable(false);
            // Mute bees' built-in loop; we trigger a short quiet buzz manually instead.
            mob.setSilent(type.getEntityType() == EntityType.BEE);
            mob.setCanPickupItems(false);
            mob.setRemoveWhenFarAway(false);
            mob.setPersistent(true);

            // Baby form — default size, not scaled down
            if (type.isBaby() && mob instanceof Ageable ageable) {
                ageable.setBaby();
                ageable.setAgeLock(true);
            }

            // PDC tags
            mob.getPersistentDataContainer().set(PET_ENTITY_KEY, PersistentDataType.BYTE, (byte) 1);
            mob.getPersistentDataContainer().set(PET_OWNER_KEY, PersistentDataType.STRING,
                    player.getUniqueId().toString());
            mob.getPersistentDataContainer().set(PET_TYPE_KEY, PersistentDataType.STRING, type.getId());

            // Mob-specific config
            if (mob instanceof Fox fox) {
                fox.setFirstTrustedPlayer(player);
            }
            if (mob instanceof Bee bee) {
                bee.setHasNectar(false);
                bee.setHasStung(false);
                bee.setAnger(0);
                bee.setTarget(null);
            }
            if (mob instanceof Goat goat) {
                goat.setScreaming(false);
            }
        }

        applyPetName(entity, pet);
        ensurePersistentAppearance(pet, type);
        applyPetAppearance(entity, pet, type);
        applyPetModePosture(player.getUniqueId(), entity);

        pet.setEntityUuid(entity.getUniqueId());
        activePetEntities.put(player.getUniqueId(), entity.getUniqueId());
        activePets.put(player.getUniqueId(), pet);
        applyOwnerNoCollision(player, entity);

        // Apply player attribute bonus
        applyPlayerAttribute(player, pet, type);

        // Effects
        spawnLoc.getWorld().spawnParticle(Particle.HEART, spawnLoc.clone().add(0, 1, 0), 5, 0.3, 0.3, 0.3);
        playPetSound(spawnLoc, type, 0.8f, 1.2f);

        String msg = plugin.getConfig().getString("messages.pet_spawned",
                "&a%pet_name% &7has appeared by your side!");
        msg = msg.replace("%pet_name%", pet.getDisplayName(type));
        player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(msg));

        refreshPetVisibilityForAll();
    }

    public void despawnPet(UUID playerUuid, boolean notify) {
        UUID entityUuid = activePetEntities.remove(playerUuid);
        if (entityUuid == null) return;

        removeHoverNameDisplay(entityUuid);
        clearHoverTargetsForPet(entityUuid);

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(entityUuid)) {
                    clearOwnerNoCollision(playerUuid, entity);
                    Location loc = entity.getLocation();
                    PetInstance pet = activePets.get(playerUuid);
                    PetType type = pet == null ? null : plugin.getPetTypes().get(pet.getPetTypeId());
                    if (type != null) {
                        playPetSound(loc, type, 0.7f, 0.9f);
                    }
                    loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.5, 0), 10, 0.3, 0.3, 0.3);
                    entity.remove();
                    break;
                }
            }
        }

        // Remove player attribute
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            removePlayerAttribute(player);
        }

        if (notify) {
            PetInstance pet = activePets.get(playerUuid);
            if (player != null && pet != null) {
                PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
                if (type != null) {
                    String msg = plugin.getConfig().getString("messages.pet_despawned",
                            "&7%pet_name% &7has returned to rest.");
                    msg = msg.replace("%pet_name%", pet.getDisplayName(type));
                    player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacyAmpersand().deserialize(msg));
                }
            }
        }

        refreshPetVisibilityForAll();
    }

    public boolean isPetEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(PET_ENTITY_KEY, PersistentDataType.BYTE);
    }

    public void ensurePersistentAppearance(PetInstance pet, PetType type) {
        if (pet == null || type == null) {
            return;
        }
        if (pet.getAppearanceVariant() != null || pet.getAppearanceSoundVariant() != null) {
            return;
        }

        switch (type.getEntityType()) {
            case FOX -> pet.setAppearanceVariant(randomFoxType().name());
            case PIG -> pet.setAppearanceVariant(randomPigVariant());
            case CHICKEN -> pet.setAppearanceVariant(randomChickenVariant());
            case RABBIT -> pet.setAppearanceVariant(randomRabbitType().name());
            default -> {
                return;
            }
        }

        if (pet.getDatabaseId() > 0) {
            plugin.getDatabaseManager().updatePet(pet);
            syncCachedAppearance(pet);
        }
    }

    public UUID getPetOwner(Entity entity) {
        String uuid = entity.getPersistentDataContainer().get(PET_OWNER_KEY, PersistentDataType.STRING);
        return uuid != null ? UUID.fromString(uuid) : null;
    }

    public void renamePet(Player player, PetInstance pet, String requestedNickname) {
        String nickname = sanitizeNickname(requestedNickname);
        if (nickname == null) return;

        pet.setNickname(nickname);
        syncCachedNickname(pet);
        plugin.getDatabaseManager().updatePet(pet);

        Entity entity = findActivePetEntity(player.getUniqueId());
        if (entity != null) {
            applyPetName(entity, pet);
            entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                    entity.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3);
        }

        PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
        String displayName = type != null ? pet.getDisplayName(type) : nickname;
        String msg = plugin.getConfig().getString("messages.pet_renamed",
                "&aYour pet is now named &e%pet_name%&a!");
        msg = msg.replace("%pet_name%", displayName);
        player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(msg));
        plugin.getAdvancementManager().handlePetRenamed(player);
    }

    // ══════════════════════════════════════════════════════════
    //  Player Attribute Bonuses
    // ══════════════════════════════════════════════════════════

    /** Apply the pet's attribute bonus to the player. */
    public void applyPlayerAttribute(Player player, PetInstance pet, PetType type) {
        removePlayerAttribute(player); // Clean first

        AttributeInstance attrInst = player.getAttribute(type.getPlayerAttribute());
        if (attrInst == null) return;

        double value = type.getAttributeAtLevel(pet.getLevel());
        AttributeModifier modifier = new AttributeModifier(
                PET_ATTRIBUTE_KEY, value, AttributeModifier.Operation.ADD_NUMBER);
        attrInst.addModifier(modifier);
    }

    /** Remove any pet attribute bonus from the player. */
    public void removePlayerAttribute(Player player) {
        for (Attribute attr : Attribute.values()) {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) continue;
            for (AttributeModifier mod : new ArrayList<>(inst.getModifiers())) {
                if (PET_ATTRIBUTE_KEY.equals(mod.getKey())) {
                    inst.removeModifier(mod);
                }
            }
        }
    }

    /** Refresh the attribute after a level change. */
    public void refreshPlayerAttribute(Player player, PetInstance pet) {
        PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
        if (type == null) return;
        applyPlayerAttribute(player, pet, type);
    }

    // ══════════════════════════════════════════════════════════
    //  Following & Teleporting
    // ══════════════════════════════════════════════════════════

    private void followTick() {
        double followDist = plugin.getConfig().getDouble("pets.follow_distance", 3.0);
        double teleportDist = plugin.getConfig().getDouble("pets.teleport_distance", 20.0);

        for (Map.Entry<UUID, UUID> entry : activePetEntities.entrySet()) {
            UUID playerUuid = entry.getKey();
            UUID entityUuid = entry.getValue();

            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) continue;

            Entity petEntity = null;
            for (Entity e : player.getWorld().getEntities()) {
                if (e.getUniqueId().equals(entityUuid)) {
                    petEntity = e;
                    break;
                }
            }

            if (petEntity == null) {
                if (plugin.getConfig().getBoolean("pets.cross_dimension_teleport", true)) {
                    PetInstance pet = activePets.get(playerUuid);
                    if (pet != null) spawnPet(player, pet);
                }
                continue;
            }

            if (petEntity instanceof Bee bee) {
                if (bee.getTarget() != null || bee.getAnger() > 0 || bee.hasStung()) {
                    bee.setTarget(null);
                    bee.setAnger(0);
                    bee.setHasStung(false);
                }
            }

            if (petEntity instanceof Mob mob) {
                if (mob.getTarget() != null) {
                    mob.setTarget(null);
                }
                if (mob.isAggressive()) {
                    mob.setAggressive(false);
                }
            }

            if (plugin.getSettingsManager().isStayMode(playerUuid)) {
                applyPetModePosture(playerUuid, petEntity);
                syncHoverNamePosition(petEntity);
                continue;
            }

            double distance = petEntity.getLocation().distance(player.getLocation());

            if (distance > teleportDist) {
                Location safeLoc = findSafeSpawnLocation(player.getLocation());
                petEntity.teleport(safeLoc);
                syncHoverNamePosition(petEntity);
                continue;
            }

            if (distance > followDist && petEntity instanceof Mob mob) {
                mob.getPathfinder().moveTo(player.getLocation(), 1.2);
            } else if (distance <= followDist) {
                maybePlayIdleEmote(player, petEntity);
            }

            syncHoverNamePosition(petEntity);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Time-Based XP Leveling
    // ══════════════════════════════════════════════════════════

    private void xpTick() {
        double xpPerTick = plugin.getConfig().getDouble("leveling.xp_per_interval", 2.0);
        int maxLevel = plugin.getConfig().getInt("leveling.max_level", 10);

        for (Map.Entry<UUID, PetInstance> entry : activePets.entrySet()) {
            UUID playerUuid = entry.getKey();
            PetInstance pet = entry.getValue();
            if (pet.getLevel() >= maxLevel) continue;

            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) continue;

            pet.setXp(pet.getXp() + xpPerTick);

            // Check level up
            while (pet.getLevel() < maxLevel) {
                double required = getXpForLevel(pet.getLevel() + 1);
                if (pet.getXp() >= required) {
                    pet.setXp(pet.getXp() - required);
                    pet.setLevel(pet.getLevel() + 1);

                    PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
                    if (type != null) {
                        String msg = plugin.getConfig().getString("messages.pet_level_up",
                                "&b&lLEVEL UP! &e%pet_name% &7is now level &e%level%&7!");
                        msg = msg.replace("%pet_name%", pet.getDisplayName(type))
                                .replace("%level%", String.valueOf(pet.getLevel()));
                        player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize(msg));
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                                player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);

                        // Refresh player attribute
                        refreshPlayerAttribute(player, pet);
                        plugin.getAdvancementManager().handlePetLevel(player, pet);
                    }
                } else {
                    break;
                }
            }

            plugin.getDatabaseManager().updatePet(pet);
        }
    }

    public double getXpForLevel(int level) {
        if (level <= 1) return 0;
        double baseXp = plugin.getConfig().getDouble("leveling.base_xp", 50);
        double exponent = plugin.getConfig().getDouble("leveling.exponent", 1.3);
        return baseXp * Math.pow(level - 1, exponent);
    }

    // ══════════════════════════════════════════════════════════
    //  Feeding & Status
    // ══════════════════════════════════════════════════════════

    /** Feed a pet — improves status, plays hearts + mob sound. */
    public void feedPet(Player player, PetInstance pet) {
        PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
        if (type == null) return;

        pet.setStatus(pet.getStatus().better());
        plugin.getDatabaseManager().updatePet(pet);

        // Hearts + mob sound
        UUID entityUuid = activePetEntities.get(player.getUniqueId());
        if (entityUuid != null) {
            for (Entity e : player.getWorld().getEntities()) {
                if (e.getUniqueId().equals(entityUuid)) {
                    e.getWorld().spawnParticle(Particle.HEART, e.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3);
                    playMobSound(e, type);
                    break;
                }
            }
        }

        String msg = plugin.getConfig().getString("messages.pet_fed",
                "&a%pet_name% &7enjoyed the treat! Status: &e%status%");
        msg = msg.replace("%pet_name%", pet.getDisplayName(type))
                .replace("%status%", pet.getStatus().getDisplay());
        player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(msg));
        plugin.getAdvancementManager().handlePetFed(player);
    }

    public boolean canPetEat(PetType type, Material material) {
        return getAllowedFoods(type).contains(material);
    }

    public List<Material> getAllowedFoods(PetType type) {
        if (type == null) {
            return Collections.emptyList();
        }

        PetMovementType movementType = type.getMovementType();
        String path = "status.food_by_type." + movementType.name().toLowerCase();
        List<String> configured = plugin.getConfig().getStringList(path);
        if (configured.isEmpty()) {
            configured = defaultFoods(movementType);
        }

        List<Material> allowed = new ArrayList<>();
        for (String value : configured) {
            Material material = Material.matchMaterial(value);
            if (material != null) {
                allowed.add(material);
            }
        }
        return allowed;
    }

    public String getAllowedFoodsDisplay(PetType type) {
        return getAllowedFoods(type).stream()
                .map(this::formatMaterialName)
                .collect(Collectors.joining(", "));
    }

    // ══════════════════════════════════════════════════════════
    //  Petting (Shift + Right-Click)
    // ══════════════════════════════════════════════════════════

    /** Pet the pet — hearts, happy jump, improve status. */
    public void petThePet(Player player, PetInstance pet) {
        PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
        if (type == null) return;

        pet.setStatus(pet.getStatus().better());
        plugin.getDatabaseManager().updatePet(pet);

        UUID entityUuid = activePetEntities.get(player.getUniqueId());
        if (entityUuid != null) {
            for (Entity e : player.getWorld().getEntities()) {
                if (e.getUniqueId().equals(entityUuid)) {
                    // Hearts
                    e.getWorld().spawnParticle(Particle.HEART,
                            e.getLocation().add(0, 1, 0), 7, 0.3, 0.3, 0.3);
                    // Happy jump
                    if (e instanceof Mob mob) {
                        mob.setVelocity(mob.getVelocity().setY(0.35));
                    }
                    playMobSound(e, type);
                    break;
                }
            }
        }

        String msg = plugin.getConfig().getString("messages.pet_petted",
                "&d%pet_name% &7loves the attention! Status: &e%status%");
        msg = msg.replace("%pet_name%", pet.getDisplayName(type))
                .replace("%status%", pet.getStatus().getDisplay());
        player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(msg));
        plugin.getAdvancementManager().handlePetPetted(player);
    }

    private void playMobSound(Entity entity, PetType type) {
        playPetSound(entity.getLocation(), type, 1.0f, 1.3f);
    }

    // ══════════════════════════════════════════════════════════
    //  Set Level (admin)
    // ══════════════════════════════════════════════════════════

    public void setLevel(Player player, PetInstance pet, int level) {
        int maxLevel = plugin.getConfig().getInt("leveling.max_level", 10);
        pet.setLevel(Math.min(level, maxLevel));
        pet.setXp(0);
        plugin.getDatabaseManager().updatePet(pet);
        refreshPlayerAttribute(player, pet);
        plugin.getAdvancementManager().handlePetLevel(player, pet);
    }

    // ══════════════════════════════════════════════════════════
    //  Deletion
    // ══════════════════════════════════════════════════════════

    public void deletePet(UUID playerUuid, PetInstance pet) {
        if (pet.isSelected()) {
            despawnPet(playerUuid, false);
            activePets.remove(playerUuid);
        }

        plugin.getDatabaseManager().deletePet(pet.getDatabaseId());

        List<PetInstance> pets = playerPetsCache.get(playerUuid);
        if (pets != null) {
            pets.removeIf(p -> p.getDatabaseId() == pet.getDatabaseId());
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Utility
    // ══════════════════════════════════════════════════════════

    private Location findSafeSpawnLocation(Location playerLoc) {
        double yaw = Math.toRadians(playerLoc.getYaw() + 180 + (Math.random() * 60 - 30));
        double x = playerLoc.getX() + Math.sin(yaw) * 2;
        double z = playerLoc.getZ() + Math.cos(yaw) * 2;
        Location loc = new Location(playerLoc.getWorld(), x, playerLoc.getY(), z,
                playerLoc.getYaw(), 0);
        loc.setY(loc.getWorld().getHighestBlockYAt(loc) + 1);
        if (Math.abs(loc.getY() - playerLoc.getY()) > 10) {
            loc = playerLoc.clone().add(1, 0, 1);
        }
        return loc;
    }

    public void refreshPetVisibility(Player viewer) {
        clearViewerHoverTarget(viewer.getUniqueId());

        for (Map.Entry<UUID, UUID> entry : activePetEntities.entrySet()) {
            UUID ownerUuid = entry.getKey();
            Entity petEntity = findEntityByUuid(entry.getValue());
            if (petEntity == null) continue;

            if (shouldHidePetFromViewer(viewer, ownerUuid)) {
                viewer.hideEntity(plugin, petEntity);
            } else {
                viewer.showEntity(plugin, petEntity);
            }

            UUID displayUuid = hoverNameDisplays.get(petEntity.getUniqueId());
            if (displayUuid != null) {
                Entity display = findEntityByUuid(displayUuid);
                if (display != null) {
                    viewer.hideEntity(plugin, display);
                }
            }
        }
    }

    public void refreshPetVisibilityForAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPetVisibility(player);
        }
    }

    public void clearViewerHoverTarget(UUID viewerUuid) {
        Player viewer = Bukkit.getPlayer(viewerUuid);
        UUID previousTarget = viewerHoverTargets.remove(viewerUuid);
        if (viewer == null || previousTarget == null) return;
        hideHoverName(viewer, previousTarget);
    }

    private void applyPetName(Entity entity, PetInstance pet) {
        String nickname = sanitizeNickname(pet.getNickname());
        entity.setCustomNameVisible(false);
        entity.customName(null);
        if (nickname == null) {
            removeHoverNameDisplay(entity.getUniqueId());
            return;
        }

        ensureHoverNameDisplay(entity, nickname);
    }

    private String sanitizeNickname(String nickname) {
        if (nickname == null) return null;

        String sanitized = nickname.trim();
        if (sanitized.isEmpty()) return null;

        if (sanitized.length() > MAX_NICKNAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_NICKNAME_LENGTH);
        }
        return sanitized;
    }

    private void syncCachedNickname(PetInstance pet) {
        List<PetInstance> pets = playerPetsCache.get(pet.getOwnerUuid());
        if (pets == null) return;

        for (PetInstance cachedPet : pets) {
            if (cachedPet.getDatabaseId() == pet.getDatabaseId()) {
                cachedPet.setNickname(pet.getNickname());
                break;
            }
        }
    }

    private void syncCachedAppearance(PetInstance pet) {
        List<PetInstance> pets = playerPetsCache.get(pet.getOwnerUuid());
        if (pets == null) return;

        for (PetInstance cachedPet : pets) {
            if (cachedPet.getDatabaseId() == pet.getDatabaseId()) {
                cachedPet.setAppearanceVariant(pet.getAppearanceVariant());
                cachedPet.setAppearanceSoundVariant(pet.getAppearanceSoundVariant());
                break;
            }
        }
    }

    private Entity findActivePetEntity(UUID playerUuid) {
        UUID entityUuid = activePetEntities.get(playerUuid);
        return findEntityByUuid(entityUuid);
    }

    private Entity findEntityByUuid(UUID entityUuid) {
        if (entityUuid == null) return null;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(entityUuid)) {
                    return entity;
                }
            }
        }

        return null;
    }

    private boolean shouldHidePetFromViewer(Player viewer, UUID ownerUuid) {
        return !viewer.getUniqueId().equals(ownerUuid)
                && plugin.getSettingsManager().isHideOtherPetsEnabled(viewer.getUniqueId());
    }

    private void applyPetModePosture(UUID playerUuid, Entity entity) {
        boolean stayMode = plugin.getSettingsManager().isStayMode(playerUuid);

        if (entity instanceof Fox fox) {
            fox.setSitting(stayMode);
        }

        if (stayMode && entity instanceof Mob mob) {
            try {
                mob.getPathfinder().stopPathfinding();
            } catch (Exception ignored) {
            }
        }
    }

    private void maybePlayIdleEmote(Player player, Entity entity) {
        PetInstance pet = activePets.get(player.getUniqueId());
        if (pet == null) return;

        long now = System.currentTimeMillis();
        Long lastAt = idleEmoteAt.get(entity.getUniqueId());
        if (lastAt != null && now - lastAt < 20000L) return;
        if (Math.random() > 0.03) return;

        idleEmoteAt.put(entity.getUniqueId(), now);
        if (entity instanceof Mob mob) {
            mob.lookAt(player);
        }
        entity.getWorld().spawnParticle(
                Math.random() < 0.5 ? Particle.HEART : Particle.HAPPY_VILLAGER,
                entity.getLocation().add(0, 1, 0),
                3, 0.25, 0.2, 0.25, 0.02
        );

        PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
        if (type != null && Math.random() < 0.35) {
            playMobSound(entity, type);
        }
    }

    private void applyPetAppearance(Entity entity, PetInstance pet, PetType type) {
        if (pet == null || type == null) {
            return;
        }

        switch (type.getEntityType()) {
            case FOX -> {
                if (entity instanceof Fox fox && pet.getAppearanceVariant() != null) {
                    try {
                        fox.setFoxType(Fox.Type.valueOf(pet.getAppearanceVariant()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            case PIG -> {
                if (entity instanceof Pig pig) {
                    Pig.Variant variant = parsePigVariant(pet.getAppearanceVariant());
                    if (variant != null) {
                        pig.setVariant(variant);
                    }
                }
            }
            case CHICKEN -> {
                if (entity instanceof Chicken chicken) {
                    Chicken.Variant variant = parseChickenVariant(pet.getAppearanceVariant());
                    if (variant != null) {
                        chicken.setVariant(variant);
                    }
                }
            }
            case RABBIT -> {
                if (entity instanceof Rabbit rabbit && pet.getAppearanceVariant() != null) {
                    try {
                        rabbit.setRabbitType(Rabbit.Type.valueOf(pet.getAppearanceVariant()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            default -> {
            }
        }
    }

    private List<String> defaultFoods(PetMovementType movementType) {
        return switch (movementType) {
            case GROUND -> List.of("WHEAT", "CARROT", "APPLE", "BREAD");
            case FLYING -> List.of("WHEAT_SEEDS", "MELON_SEEDS", "PUMPKIN_SEEDS", "BEETROOT_SEEDS", "TORCHFLOWER_SEEDS", "PITCHER_POD");
            case WATER -> List.of("COD", "SALMON", "TROPICAL_FISH", "KELP");
        };
    }

    private String formatMaterialName(Material material) {
        String[] words = material.name().toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private void applyOwnerNoCollision(Player player, Entity petEntity) {
        if (!(petEntity instanceof LivingEntity livingEntity)) {
            return;
        }

        livingEntity.setCollidable(false);
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(collisionTeamName(player.getUniqueId()));
        if (team == null) {
            team = scoreboard.registerNewTeam(collisionTeamName(player.getUniqueId()));
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            team.setCanSeeFriendlyInvisibles(false);
            team.setAllowFriendlyFire(false);
        }

        if (!team.hasEntity(player)) {
            team.addEntity(player);
        }
        if (!team.hasEntity(petEntity)) {
            team.addEntity(petEntity);
        }
    }

    private void clearOwnerNoCollision(UUID playerUuid, Entity petEntity) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(collisionTeamName(playerUuid));
        if (team == null) {
            return;
        }

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && team.hasEntity(player)) {
            team.removeEntity(player);
        }
        if (petEntity != null && team.hasEntity(petEntity)) {
            team.removeEntity(petEntity);
        }
        if (team.getSize() == 0) {
            team.unregister();
        }
    }

    private String collisionTeamName(UUID playerUuid) {
        String compact = playerUuid.toString().replace("-", "");
        return "petnc_" + compact.substring(0, 10);
    }

    private void hoverNameTick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Entity target = resolveHoverTarget(getViewedPetTarget(player));
            UUID nextTarget = null;
            if (target != null && isPetEntity(target)) {
                UUID ownerUuid = getPetOwner(target);
                PetInstance pet = ownerUuid == null ? null : activePets.get(ownerUuid);
                if (ownerUuid != null
                        && pet != null
                        && sanitizeNickname(pet.getNickname()) != null
                        && !shouldHidePetFromViewer(player, ownerUuid)) {
                    nextTarget = target.getUniqueId();
                }
            }

            UUID previousTarget = viewerHoverTargets.get(player.getUniqueId());
            if (Objects.equals(previousTarget, nextTarget)) continue;

            if (previousTarget != null) {
                hideHoverName(player, previousTarget);
            }

            if (nextTarget != null) {
                showHoverName(player, nextTarget);
                viewerHoverTargets.put(player.getUniqueId(), nextTarget);
            } else {
                viewerHoverTargets.remove(player.getUniqueId());
            }
        }
    }

    private void hoverNamePositionTick() {
        Set<UUID> visibleTargets = new HashSet<>(viewerHoverTargets.values());
        for (UUID petEntityUuid : visibleTargets) {
            Entity petEntity = findEntityByUuid(petEntityUuid);
            if (petEntity != null) {
                syncHoverNamePosition(petEntity);
            }
        }
    }

    private Entity resolveHoverTarget(Entity target) {
        if (target == null) return null;
        if (isPetEntity(target)) return target;

        String parentUuid = target.getPersistentDataContainer().get(PET_NAME_DISPLAY_KEY, PersistentDataType.STRING);
        if (parentUuid == null) return null;

        try {
            return findEntityByUuid(UUID.fromString(parentUuid));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Entity getViewedPetTarget(Player player) {
        RayTraceResult rayTrace = player.rayTraceEntities(8, false);
        if (rayTrace != null && rayTrace.getHitEntity() != null) {
            Entity resolved = resolveHoverTarget(rayTrace.getHitEntity());
            if (resolved != null) {
                return resolved;
            }
        }

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Entity bestMatch = null;
        double bestAlignment = 0.965;

        for (UUID petEntityUuid : activePetEntities.values()) {
            Entity entity = findEntityByUuid(petEntityUuid);
            if (entity == null || !entity.getWorld().equals(player.getWorld())) {
                continue;
            }

            Location targetLocation = entity.getLocation().add(0, entity.getHeight() * 0.6, 0);
            Vector toTarget = targetLocation.toVector().subtract(eye.toVector());
            double distance = toTarget.length();
            if (distance > 8 || distance <= 0.001) {
                continue;
            }
            if (!player.hasLineOfSight(entity)) {
                continue;
            }

            double alignment = direction.dot(toTarget.normalize());
            if (alignment > bestAlignment) {
                bestAlignment = alignment;
                bestMatch = entity;
            }
        }

        return bestMatch;
    }

    private void ensureHoverNameDisplay(Entity entity, String nickname) {
        Entity existing = findEntityByUuid(hoverNameDisplays.get(entity.getUniqueId()));
        TextDisplay display;
        if (existing instanceof TextDisplay textDisplay) {
            display = textDisplay;
        } else {
            display = (TextDisplay) entity.getWorld().spawnEntity(getHoverNameLocation(entity), EntityType.TEXT_DISPLAY);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setPersistent(false);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setViewRange(24f);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(1);
            display.setTeleportDuration(1);
            display.getPersistentDataContainer().set(
                    PET_NAME_DISPLAY_KEY,
                    PersistentDataType.STRING,
                    entity.getUniqueId().toString()
            );
            hoverNameDisplays.put(entity.getUniqueId(), display.getUniqueId());

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                viewer.hideEntity(plugin, display);
            }
        }

        display.text(Component.text(nickname).color(NamedTextColor.WHITE));
        syncHoverNamePosition(entity);
    }

    private void removeHoverNameDisplay(UUID petEntityUuid) {
        UUID displayUuid = hoverNameDisplays.remove(petEntityUuid);
        if (displayUuid == null) return;

        Entity display = findEntityByUuid(displayUuid);
        if (display != null) {
            display.remove();
        }
    }

    private void syncHoverNamePosition(Entity petEntity) {
        UUID displayUuid = hoverNameDisplays.get(petEntity.getUniqueId());
        if (displayUuid == null) return;

        Entity display = findEntityByUuid(displayUuid);
        if (display == null) {
            hoverNameDisplays.remove(petEntity.getUniqueId());
            return;
        }

        display.teleport(getHoverNameLocation(petEntity));
    }

    private Location getHoverNameLocation(Entity entity) {
        return entity.getLocation().add(0, entity.getHeight() + 0.25, 0);
    }

    private void clearHoverTargetsForPet(UUID petEntityUuid) {
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(viewerHoverTargets.entrySet())) {
            if (!petEntityUuid.equals(entry.getValue())) continue;
            clearViewerHoverTarget(entry.getKey());
        }
    }

    private void showHoverName(Player viewer, UUID petEntityUuid) {
        UUID displayUuid = hoverNameDisplays.get(petEntityUuid);
        if (displayUuid == null) return;

        Entity display = findEntityByUuid(displayUuid);
        if (display != null) {
            Entity petEntity = findEntityByUuid(petEntityUuid);
            if (petEntity != null) {
                syncHoverNamePosition(petEntity);
            }
            viewer.showEntity(plugin, display);
        }
    }

    private void hideHoverName(Player viewer, UUID petEntityUuid) {
        UUID displayUuid = hoverNameDisplays.get(petEntityUuid);
        if (displayUuid == null) return;

        Entity display = findEntityByUuid(displayUuid);
        if (display != null) {
            viewer.hideEntity(plugin, display);
        }
    }

    private Sound getPetAmbientSound(PetType type) {
        return switch (type.getEntityType()) {
            case CHICKEN -> Sound.ENTITY_CHICKEN_AMBIENT;
            case PIG -> Sound.ENTITY_PIG_AMBIENT;
            case BEE -> Sound.ENTITY_BEE_LOOP;
            case DOLPHIN -> Sound.ENTITY_DOLPHIN_AMBIENT;
            case FOX -> Sound.ENTITY_FOX_AMBIENT;
            case GOAT -> Sound.ENTITY_GOAT_AMBIENT;
            case RABBIT -> Sound.ENTITY_RABBIT_AMBIENT;
            case NAUTILUS -> Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE;
            default -> Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM;
        };
    }

    private void playPetSound(Location location, PetType type, float volume, float pitch) {
        if (location.getWorld() == null || type == null) {
            return;
        }

        float adjustedVolume = volume;
        float adjustedPitch = pitch;
        if (type.getEntityType() == EntityType.BEE) {
            adjustedVolume = Math.min(volume, 0.15f);
            adjustedPitch = 1.0f;
        }

        location.getWorld().playSound(location, getPetAmbientSound(type), adjustedVolume, adjustedPitch);
    }

    private Fox.Type randomFoxType() {
        Fox.Type[] values = Fox.Type.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    private String randomPigVariant() {
        return switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0 -> "COLD";
            case 1 -> "TEMPERATE";
            default -> "WARM";
        };
    }

    private String randomChickenVariant() {
        return switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0 -> "COLD";
            case 1 -> "TEMPERATE";
            default -> "WARM";
        };
    }

    private Rabbit.Type randomRabbitType() {
        Rabbit.Type[] values = {
                Rabbit.Type.BROWN,
                Rabbit.Type.WHITE,
                Rabbit.Type.BLACK,
                Rabbit.Type.BLACK_AND_WHITE,
                Rabbit.Type.GOLD,
                Rabbit.Type.SALT_AND_PEPPER
        };
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    private Pig.Variant parsePigVariant(String value) {
        if (value == null) return null;
        return switch (value) {
            case "COLD" -> Pig.Variant.COLD;
            case "TEMPERATE" -> Pig.Variant.TEMPERATE;
            case "WARM" -> Pig.Variant.WARM;
            default -> null;
        };
    }

    private Chicken.Variant parseChickenVariant(String value) {
        if (value == null) return null;
        return switch (value) {
            case "COLD" -> Chicken.Variant.COLD;
            case "TEMPERATE" -> Chicken.Variant.TEMPERATE;
            case "WARM" -> Chicken.Variant.WARM;
            default -> null;
        };
    }
}
