package com.petsplugin.manager;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetInstance;
import com.petsplugin.model.PetFollowMode;
import com.petsplugin.model.PetMovementType;
import com.petsplugin.model.PetStatus;
import com.petsplugin.model.PetType;
import com.petsplugin.model.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
    private final Map<UUID, Attribute> appliedPlayerAttributes = new ConcurrentHashMap<>();
    private final Map<UUID, Location> stayAnchors = new ConcurrentHashMap<>();
    private final Map<String, List<Material>> allowedFoodsByPetType = new ConcurrentHashMap<>();
    private final Map<UUID, Object> selectionLocks = new ConcurrentHashMap<>();

    private final NamespacedKey PET_ENTITY_KEY;
    private final NamespacedKey PET_OWNER_KEY;
    private final NamespacedKey PET_TYPE_KEY;
    private final NamespacedKey PET_NAME_DISPLAY_KEY;

    /** NamespacedKey used for player attribute modifiers applied by pets. */
    private final NamespacedKey PET_ATTRIBUTE_KEY;

    private BukkitTask followTask;
    private BukkitTask xpTask;
    private BukkitTask hoverNameTask;
    private BukkitTask hoverNamePositionTask;
    private BukkitTask abilityTask;

    private static final double STAY_MAX_HORIZONTAL_DRIFT = 0.65;
    private static final double STAY_MAX_VERTICAL_DRIFT = 1.0;
    private static final double STAY_VELOCITY_EPSILON_SQUARED = 0.000001;

    public PetManager(PetsPlugin plugin) {
        this.plugin = plugin;
        this.PET_ENTITY_KEY = new NamespacedKey(plugin, "pet_entity");
        this.PET_OWNER_KEY = new NamespacedKey(plugin, "pet_owner");
        this.PET_TYPE_KEY = new NamespacedKey(plugin, "pet_type");
        this.PET_NAME_DISPLAY_KEY = new NamespacedKey(plugin, "pet_name_display");
        this.PET_ATTRIBUTE_KEY = new NamespacedKey(plugin, "pet_attribute_bonus");
    }

    public void initialize() {
        reloadConfigCache();
        cleanupPersistedPetArtifacts();
        followTask = Bukkit.getScheduler().runTaskTimer(plugin, this::followTick, 10L, 5L);
        // XP task: every 60 seconds
        long xpInterval = plugin.getConfig().getLong("leveling.xp_interval_ticks", 1200L);
        xpTask = Bukkit.getScheduler().runTaskTimer(plugin, this::xpTick, xpInterval, xpInterval);
        hoverNameTask = Bukkit.getScheduler().runTaskTimer(plugin, this::hoverNameTick, 10L, 2L);
        hoverNamePositionTask = Bukkit.getScheduler().runTaskTimer(plugin, this::hoverNamePositionTick, 10L, 1L);
        // Special ability tick every 2 seconds (e.g. squid underwater vision)
        abilityTask = Bukkit.getScheduler().runTaskTimer(plugin, this::abilityTick, 20L, 40L);
        restoreOnlinePlayers();
    }

    public void shutdown() {
        if (followTask != null) followTask.cancel();
        if (xpTask != null) xpTask.cancel();
        if (hoverNameTask != null) hoverNameTask.cancel();
        if (hoverNamePositionTask != null) hoverNamePositionTask.cancel();
        if (abilityTask != null) abilityTask.cancel();

        for (UUID playerUuid : new ArrayList<>(activePetEntities.keySet())) {
            despawnPet(playerUuid, false);
        }

        for (UUID viewerUuid : new ArrayList<>(viewerHoverTargets.keySet())) {
            clearViewerHoverTarget(viewerUuid);
        }

        appliedPlayerAttributes.clear();
        cleanupCollisionTeams();
    }

    // ══════════════════════════════════════════════════════════
    //  Cache
    // ══════════════════════════════════════════════════════════

    public List<PetInstance> loadPlayerPets(UUID uuid) {
        List<PetInstance> pets = plugin.getDatabaseManager().loadPets(uuid);
        PetInstance normalizedSelected = enforceSingleSelection(uuid, pets);
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
        } else if (normalizedSelected != null) {
            activePets.put(uuid, normalizedSelected);
        }
        return pets;
    }

    public List<PetInstance> getPlayerPets(UUID uuid) {
        return playerPetsCache.computeIfAbsent(uuid, this::loadPlayerPets);
    }

    public void clearCache(UUID uuid) { playerPetsCache.remove(uuid); }

    public void clearPlayerSessionState(UUID uuid) {
        activePets.remove(uuid);
        appliedPlayerAttributes.remove(uuid);
        stayAnchors.remove(uuid);
        selectionLocks.remove(uuid);
    }

    public void refreshCache(UUID uuid) { loadPlayerPets(uuid); }

    public NamespacedKey getPetEntityKey() {
        return PET_ENTITY_KEY;
    }

    public NamespacedKey getPetOwnerKey() {
        return PET_OWNER_KEY;
    }

    public NamespacedKey getPetTypeKey() {
        return PET_TYPE_KEY;
    }

    public NamespacedKey getPetNameDisplayKey() {
        return PET_NAME_DISPLAY_KEY;
    }

    public NamespacedKey getPetAttributeKey() {
        return PET_ATTRIBUTE_KEY;
    }

    public void reloadConfigCache() {
        Map<PetMovementType, List<Material>> foodsByMovement = new EnumMap<>(PetMovementType.class);
        for (PetMovementType movementType : PetMovementType.values()) {
            foodsByMovement.put(movementType, loadFoodsForMovement(movementType));
        }

        allowedFoodsByPetType.clear();
        for (PetType type : plugin.getPetTypes().values()) {
            List<Material> allowed = foodsByMovement.getOrDefault(type.getMovementType(), Collections.emptyList());
            allowedFoodsByPetType.put(type.getId(), allowed);
        }
    }

    private List<Material> loadFoodsForMovement(PetMovementType movementType) {
        String path = "status.food_by_type." + movementType.name().toLowerCase(Locale.ROOT);
        List<Material> allowed = new ArrayList<>();
        for (String value : plugin.getConfig().getStringList(path)) {
            Material material = Material.matchMaterial(value);
            if (material != null) {
                allowed.add(material);
            }
        }
        return Collections.unmodifiableList(allowed);
    }

    // ══════════════════════════════════════════════════════════
    //  Selection
    // ══════════════════════════════════════════════════════════

    public void selectPet(UUID playerUuid, PetInstance pet) {
        if (pet == null) {
            return;
        }

        synchronized (selectionLock(playerUuid)) {
            List<PetInstance> pets = getPlayerPets(playerUuid);
            PetInstance target = findPetById(pets, pet.getDatabaseId());
            if (target == null) {
                return;
            }

            despawnPet(playerUuid, true);

            setSelectionInCache(pets, target.getDatabaseId());
            plugin.getDatabaseManager().setSelectedPet(playerUuid, target.getDatabaseId());
            activePets.put(playerUuid, target);

            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                spawnPet(player, target);
            }
        }
    }

    public void deselectPet(UUID playerUuid) {
        synchronized (selectionLock(playerUuid)) {
            despawnPet(playerUuid, true);

            activePets.remove(playerUuid);

            List<PetInstance> pets = getPlayerPets(playerUuid);
            setSelectionInCache(pets, -1);
            plugin.getDatabaseManager().clearSelectedPet(playerUuid);

            // Remove player attribute bonus
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                removePlayerAttribute(player);
            }
        }
    }

    public PetInstance getActivePet(UUID playerUuid) { return activePets.get(playerUuid); }

    public PetInstance getSelectedPet(UUID playerUuid) {
        synchronized (selectionLock(playerUuid)) {
            return enforceSingleSelection(playerUuid, getPlayerPets(playerUuid));
        }
    }

    public void setFollowMode(Player player, PetFollowMode mode) {
        PetFollowMode resolved = mode == null ? PetFollowMode.FOLLOW : mode;
        plugin.getSettingsManager().setFollowMode(player.getUniqueId(), resolved);

        Entity entity = findActivePetEntity(player.getUniqueId());
        if (entity != null) {
            if (resolved == PetFollowMode.STAY) {
                stayAnchors.put(player.getUniqueId(), entity.getLocation().clone());
            } else {
                stayAnchors.remove(player.getUniqueId());
            }
            applyPetModePosture(player.getUniqueId(), entity);
        } else if (resolved == PetFollowMode.FOLLOW) {
            stayAnchors.remove(player.getUniqueId());
        }

        String path = resolved == PetFollowMode.FOLLOW
                ? "messages.pet_mode_follow"
                : "messages.pet_mode_stay";
        String fallback = resolved == PetFollowMode.FOLLOW
                ? "&aYour pet will follow you again."
                : "&eYour pet is staying put.";
        sendPetNotification(player, path, fallback, null, false);
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
        sendPetNotification(player, path, fallback, null, false);
    }

    public boolean isPetSoundsEnabled(UUID playerUuid) {
        return plugin.getSettingsManager().isPetSoundsEnabled(playerUuid);
    }

    public boolean isPetNotificationsEnabled(UUID playerUuid) {
        return plugin.getSettingsManager().isPetNotificationsEnabled(playerUuid);
    }

    public void setPetSoundsEnabled(Player player, boolean enabled) {
        plugin.getSettingsManager().setPetSoundsEnabled(player.getUniqueId(), enabled);

        String path = enabled
                ? "messages.pet_sounds_enabled"
                : "messages.pet_sounds_disabled";
        String fallback = enabled
                ? "&aYour pet sounds are now enabled."
                : "&eYour pet sounds are now muted.";
        sendPetNotification(player, path, fallback, null, false);
    }

    public void setPetNotificationsEnabled(Player player, boolean enabled) {
        plugin.getSettingsManager().setPetNotificationsEnabled(player.getUniqueId(), enabled);

        String path = enabled
                ? "messages.pet_notifications_enabled"
                : "messages.pet_notifications_disabled";
        String fallback = enabled
                ? "&aYour pet notifications are now enabled."
                : "&eYour pet notifications are now muted.";
        sendPetNotification(player, path, fallback, null, false);
    }

    public void sendPetNotification(Player player, String path, String fallback, Map<String, String> replacements) {
        sendPetNotification(player, path, fallback, replacements, true);
    }

    public void sendPetNotification(Player player, String path, String fallback,
                                    Map<String, String> replacements,
                                    boolean requireNotificationsEnabled) {
        if (player == null) {
            return;
        }
        if (requireNotificationsEnabled && !isPetNotificationsEnabled(player.getUniqueId())) {
            return;
        }

        String msg = plugin.getConfig().getString(path, fallback);
        if (replacements != null) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                msg = msg.replace(entry.getKey(), entry.getValue());
            }
        }

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
            // Keep bees silent to avoid ambient buzzing noise.
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

        if (plugin.getSettingsManager().isStayMode(player.getUniqueId())) {
            stayAnchors.put(player.getUniqueId(), entity.getLocation().clone());
        }
        applyPetModePosture(player.getUniqueId(), entity);

        pet.setEntityUuid(entity.getUniqueId());
        activePetEntities.put(player.getUniqueId(), entity.getUniqueId());
        activePets.put(player.getUniqueId(), pet);
        applyOwnerNoCollision(player, entity);

        // Apply player attribute bonus
        applyPlayerAttribute(player, pet, type);

        // Effects
        spawnLoc.getWorld().spawnParticle(Particle.HEART, spawnLoc.clone().add(0, 1, 0), 5, 0.3, 0.3, 0.3);
        playPetSound(spawnLoc, type, 0.8f, 1.2f, player.getUniqueId());

        sendPetNotification(player,
            "messages.pet_spawned",
            "&a%pet_name% &7has appeared by your side!",
            Map.of("%pet_name%", pet.getDisplayName(type)));

        refreshPetVisibilityForAll();
    }

    public void despawnPet(UUID playerUuid, boolean notify) {
        UUID entityUuid = activePetEntities.remove(playerUuid);
        stayAnchors.remove(playerUuid);

        Entity entity = findEntityByUuid(entityUuid);
        if (entity != null) {
            Location loc = entity.getLocation();
            PetInstance pet = activePets.get(playerUuid);
            PetType type = pet == null ? null : plugin.getPetTypes().get(pet.getPetTypeId());
            if (type != null) {
                playPetSound(loc, type, 0.7f, 0.9f, playerUuid);
            }
            loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.5, 0), 10, 0.3, 0.3, 0.3);
        }

        if (entityUuid != null) {
            removePetArtifacts(entity, playerUuid, entityUuid);
        }
        cleanupDuplicatePetsForOwner(playerUuid, entityUuid);

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
                    sendPetNotification(player,
                        "messages.pet_despawned",
                        "&7%pet_name% &7has returned to rest.",
                        Map.of("%pet_name%", pet.getDisplayName(type)));
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
        if (pet.getAppearanceVariant() != null) {
            return;
        }

        switch (type.getEntityType()) {
            case FOX -> pet.setAppearanceVariant(randomFoxType().name());
            case PIG, CHICKEN -> pet.setAppearanceVariant(randomClimateVariant());
            case SHEEP -> pet.setAppearanceVariant(randomSheepColor().name());
            case RABBIT -> pet.setAppearanceVariant(randomRabbitType().name());
            case HORSE -> pet.setAppearanceVariant(randomHorseVariant());
            default -> {
                return;
            }
        }

        if (pet.getDatabaseId() > 0) {
            plugin.getDatabaseManager().updatePetAsync(pet);
            syncCachedAppearance(pet);
        }
    }

    public UUID getPetOwner(Entity entity) {
        String uuid = entity.getPersistentDataContainer().get(PET_OWNER_KEY, PersistentDataType.STRING);
        if (uuid == null) {
            return null;
        }

        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException invalidUuid) {
            return null;
        }
    }

    public void renamePet(Player player, PetInstance pet, String requestedNickname) {
        String nickname = sanitizeNickname(requestedNickname);
        if (nickname == null) return;

        pet.setNickname(nickname);
        syncCachedNickname(pet);
        plugin.getDatabaseManager().updatePetAsync(pet);

        Entity entity = findActivePetEntity(player.getUniqueId());
        if (entity != null) {
            applyPetName(entity, pet);
            entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                    entity.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3);
        }

        PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
        String displayName = type != null ? pet.getDisplayName(type) : nickname;
        sendPetNotification(player,
            "messages.pet_renamed",
            "&aYour pet is now named &e%pet_name%&a!",
            Map.of("%pet_name%", displayName));
        plugin.getAdvancementManager().handlePetRenamed(player);
    }

    // ══════════════════════════════════════════════════════════
    //  Player Attribute Bonuses
    // ══════════════════════════════════════════════════════════

    public boolean arePetAbilitiesEnabled() {
        return plugin.getConfig().getBoolean("pets.abilities.enabled", true);
    }

    /** Apply the pet's attribute bonus to the player. */
    public void applyPlayerAttribute(Player player, PetInstance pet, PetType type) {
        removePlayerAttribute(player); // Clean first
        if (!arePetAbilitiesEnabled()) return;
        if (!type.hasPlayerAttribute()) return; // Storage pets have no stat bonus

        AttributeInstance attrInst = player.getAttribute(type.getPlayerAttribute());
        if (attrInst == null) return;

        double value = type.getAttributeAtLevel(pet.getLevel());
        AttributeModifier.Operation operation = AttributeModifier.Operation.ADD_NUMBER;

        // Gravity is treated as a scalar from base 1.0 (1 + amount).
        // Example: amount -0.09 => 0.91x gravity, amount -0.9 => 0.1x gravity.
        if (type.getPlayerAttribute() == Attribute.GRAVITY) {
            operation = AttributeModifier.Operation.MULTIPLY_SCALAR_1;
        }

        AttributeModifier modifier = new AttributeModifier(
                PET_ATTRIBUTE_KEY, value, operation);
        attrInst.addModifier(modifier);
        appliedPlayerAttributes.put(player.getUniqueId(), type.getPlayerAttribute());
    }

    /** Remove any pet attribute bonus from the player. */
    public void removePlayerAttribute(Player player) {
        Set<Attribute> candidateAttrs = new HashSet<>();
        Attribute tracked = appliedPlayerAttributes.remove(player.getUniqueId());
        if (tracked != null) {
            candidateAttrs.add(tracked);
        }

        for (PetType type : plugin.getPetTypes().values()) {
            if (type.hasPlayerAttribute()) {
                candidateAttrs.add(type.getPlayerAttribute());
            }
        }

        for (Attribute attr : candidateAttrs) {
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

    /** Re-apply or clear player ability modifiers after config reloads. */
    public void refreshAbilityStateForOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removePlayerAttribute(player);
        }

        if (!arePetAbilitiesEnabled()) {
            return;
        }

        for (Map.Entry<UUID, PetInstance> entry : activePets.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            refreshPlayerAttribute(player, entry.getValue());
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Following & Teleporting
    // ══════════════════════════════════════════════════════════

    private void followTick() {
        double followDist = plugin.getFollowDistance();
        double teleportDist = plugin.getTeleportDistance();

        for (Map.Entry<UUID, UUID> entry : activePetEntities.entrySet()) {
            UUID playerUuid = entry.getKey();
            UUID entityUuid = entry.getValue();

            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) continue;

            Entity petEntity = findEntityByUuid(entityUuid);

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
                enforceStayPosition(playerUuid, petEntity);
                syncHoverNamePosition(petEntity);
                continue;
            }

            stayAnchors.remove(playerUuid);

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
        int maxLevel = plugin.getMaxLevel();

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
                        sendPetNotification(player,
                            "messages.pet_level_up",
                            "&b&lLEVEL UP! &e%pet_name% &7is now level &e%level%&7!",
                            Map.of(
                                "%pet_name%", pet.getDisplayName(type),
                                "%level%", String.valueOf(pet.getLevel())
                            ));
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

            plugin.getDatabaseManager().updatePetAsync(pet);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Special Ability Tick
    // ══════════════════════════════════════════════════════════

    private void abilityTick() {
        if (!arePetAbilitiesEnabled()) return;

        for (Map.Entry<UUID, PetInstance> entry : activePets.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;

            PetType type = plugin.getPetTypes().get(entry.getValue().getPetTypeId());
            if (type == null) continue;

            if (type.getSpecialAbility() == PetType.SpecialAbility.UNDERWATER_VISION) {
                applyUnderwaterVision(player);
            }
        }
    }

    /**
     * Applies a short-duration Night Vision effect while the player's eyes are submerged.
     * Re-applied every 2 seconds so it never expires while underwater, with no icon or particles
     * to keep it seamless. Naturally wears off ~5 seconds after leaving water.
     */
    private void applyUnderwaterVision(Player player) {
        if (player.getEyeLocation().getBlock().isLiquid()) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.NIGHT_VISION,
                    100,    // 5 seconds — refreshed every 2s while underwater
                    0,
                    true,   // ambient: subtler particles
                    false,  // no particles
                    false   // no HUD icon
            ));
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
        plugin.getDatabaseManager().updatePetAsync(pet);

        // Hearts + mob sound
        UUID entityUuid = activePetEntities.get(player.getUniqueId());
        if (entityUuid != null) {
            Entity entity = findEntityByUuid(entityUuid);
            if (entity != null) {
                entity.getWorld().spawnParticle(Particle.HEART, entity.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3);
                playPetAmbientSound(entity, type);
            }
        }

        sendPetNotification(player,
            "messages.pet_fed",
            "&a%pet_name% &7enjoyed the treat! Status: &e%status%",
            Map.of(
                "%pet_name%", pet.getDisplayName(type),
                "%status%", getLocalizedStatusDisplay(pet.getStatus())
            ));
        plugin.getAdvancementManager().handlePetFed(player);
    }

    public boolean canPetEat(PetType type, Material material) {
        return getAllowedFoods(type).contains(material);
    }

    public List<Material> getAllowedFoods(PetType type) {
        if (type == null) {
            return Collections.emptyList();
        }
        return allowedFoodsByPetType.getOrDefault(type.getId(), Collections.emptyList());
    }

    public String getAllowedFoodsDisplay(PetType type) {
        return getAllowedFoods(type).stream()
                .map(this::formatMaterialName)
                .collect(Collectors.joining(", "));
    }

    public String getLocalizedLabel(String key, String fallback) {
        return plugin.getLanguageManager().getString("ui.labels." + key, fallback);
    }

    public String getLocalizedRarity(Rarity rarity) {
        if (rarity == null) {
            return "";
        }
        String path = "ui.rarity." + rarity.name().toLowerCase(Locale.ROOT);
        return plugin.getLanguageManager().getString(path, defaultRarityName(rarity));
    }

    public String getLocalizedStatusName(PetStatus status) {
        if (status == null) {
            return "";
        }
        String path = "ui.status." + status.name().toLowerCase(Locale.ROOT);
        return plugin.getLanguageManager().getString(path, status.getDefaultName());
    }

    public String getLocalizedStatusDisplay(PetStatus status) {
        if (status == null) {
            return "";
        }
        return status.getIcon() + " " + getLocalizedStatusName(status);
    }

    private String defaultRarityName(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> "Common";
            case UNCOMMON -> "Uncommon";
            case RARE -> "Rare";
            case EPIC -> "Epic";
            case LEGENDARY -> "Legendary";
        };
    }

    // ══════════════════════════════════════════════════════════
    //  Petting (Shift + Right-Click)
    // ══════════════════════════════════════════════════════════

    /** Pet the pet — hearts, happy jump, improve status. */
    public void petThePet(Player player, PetInstance pet) {
        PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
        if (type == null) return;

        pet.setStatus(pet.getStatus().better());
        plugin.getDatabaseManager().updatePetAsync(pet);

        UUID entityUuid = activePetEntities.get(player.getUniqueId());
        if (entityUuid != null) {
            Entity entity = findEntityByUuid(entityUuid);
            if (entity != null) {
                // Hearts
                entity.getWorld().spawnParticle(Particle.HEART,
                        entity.getLocation().add(0, 1, 0), 7, 0.3, 0.3, 0.3);
                // Happy jump
                if (entity instanceof Mob mob) {
                    mob.setVelocity(mob.getVelocity().setY(0.35));
                }
            }
        }

        sendPetNotification(player,
            "messages.pet_petted",
            "&d%pet_name% &7loves the attention! Status: &e%status%",
            Map.of(
                "%pet_name%", pet.getDisplayName(type),
                "%status%", getLocalizedStatusDisplay(pet.getStatus())
            ));
        plugin.getAdvancementManager().handlePetPetted(player);
    }

    public void playPetAmbientSound(Entity entity, PetType type) {
        if (entity == null || type == null) {
            return;
        }

        UUID ownerUuid = getPetOwner(entity);
        playPetSound(entity.getLocation(), type, 1.0f, 1.3f, ownerUuid);
    }

    // ══════════════════════════════════════════════════════════
    //  Set Level (admin)
    // ══════════════════════════════════════════════════════════

    public void setLevel(Player player, PetInstance pet, int level) {
        int maxLevel = plugin.getMaxLevel();
        pet.setLevel(Math.min(level, maxLevel));
        pet.setXp(0);
        plugin.getDatabaseManager().updatePetAsync(pet);
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
                break;
            }
        }
    }

    private Entity findActivePetEntity(UUID playerUuid) {
        UUID entityUuid = activePetEntities.get(playerUuid);
        return findEntityByUuid(entityUuid);
    }

    private void cleanupPersistedPetArtifacts() {
        int removedPets = 0;
        int removedDisplays = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (isPetEntity(entity)) {
                    UUID ownerUuid = getPetOwner(entity);
                    removePetArtifacts(entity, ownerUuid, entity.getUniqueId());
                    removedPets++;
                    continue;
                }

                if (isPetNameDisplay(entity)) {
                    entity.remove();
                    removedDisplays++;
                }
            }
        }

        activePetEntities.clear();
        hoverNameDisplays.clear();
        viewerHoverTargets.clear();
        idleEmoteAt.clear();
        cleanupCollisionTeams();

        if (removedPets > 0 || removedDisplays > 0) {
            plugin.getLogger().info("Removed " + removedPets + " stale pet entities and "
                    + removedDisplays + " stale pet name displays during startup cleanup.");
        }
    }

    private void restoreOnlinePlayers() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                loadPlayerPets(player.getUniqueId());
                plugin.getSettingsManager().loadPlayerSettings(player.getUniqueId());
                refreshPetVisibility(player);

                if (!plugin.getConfig().getBoolean("pets.respawn_on_join", true)) {
                    continue;
                }

                PetInstance selected = getSelectedPet(player.getUniqueId());
                if (selected != null) {
                    spawnPet(player, selected);
                }
            }
        }, 1L);
    }

    private Object selectionLock(UUID playerUuid) {
        return selectionLocks.computeIfAbsent(playerUuid, ignored -> new Object());
    }

    private PetInstance findPetById(List<PetInstance> pets, int databaseId) {
        for (PetInstance candidate : pets) {
            if (candidate.getDatabaseId() == databaseId) {
                return candidate;
            }
        }
        return null;
    }

    private void setSelectionInCache(List<PetInstance> pets, int selectedPetId) {
        for (PetInstance pet : pets) {
            pet.setSelected(pet.getDatabaseId() == selectedPetId);
        }
    }

    private PetInstance enforceSingleSelection(UUID playerUuid, List<PetInstance> pets) {
        if (pets == null || pets.isEmpty()) {
            return null;
        }

        PetInstance active = activePets.get(playerUuid);
        PetInstance activeMatch = active == null ? null : findPetById(pets, active.getDatabaseId());

        int selectedCount = 0;
        PetInstance selected = null;
        for (PetInstance pet : pets) {
            if (!pet.isSelected()) {
                continue;
            }
            selectedCount++;
            if (selected == null || pet.getObtainedAt() > selected.getObtainedAt()) {
                selected = pet;
            }
        }

        boolean needsRepair = false;
        PetInstance resolved = selected;

        if (activeMatch != null && (selectedCount != 1 || selected == null
                || activeMatch.getDatabaseId() != selected.getDatabaseId())) {
            resolved = activeMatch;
            needsRepair = true;
        } else if (selectedCount > 1) {
            needsRepair = true;
        }

        if (needsRepair) {
            if (resolved != null) {
                setSelectionInCache(pets, resolved.getDatabaseId());
                plugin.getDatabaseManager().setSelectedPet(playerUuid, resolved.getDatabaseId());
            } else {
                setSelectionInCache(pets, -1);
                plugin.getDatabaseManager().clearSelectedPet(playerUuid);
            }
        }

        return resolved;
    }

    private Entity findEntityByUuid(UUID entityUuid) {
        if (entityUuid == null) return null;

        Entity direct = Bukkit.getEntity(entityUuid);
        if (direct != null) {
            return direct;
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(entityUuid)) {
                    return entity;
                }
            }
        }

        return null;
    }

    private boolean isPetNameDisplay(Entity entity) {
        return entity.getPersistentDataContainer().has(PET_NAME_DISPLAY_KEY, PersistentDataType.STRING);
    }

    private void cleanupDuplicatePetsForOwner(UUID ownerUuid, UUID preservedEntityUuid) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (!isPetEntity(entity)) {
                    continue;
                }

                UUID entityOwner = getPetOwner(entity);
                if (!ownerUuid.equals(entityOwner)) {
                    continue;
                }

                if (preservedEntityUuid != null && preservedEntityUuid.equals(entity.getUniqueId())) {
                    continue;
                }

                removePetArtifacts(entity, ownerUuid, entity.getUniqueId());
            }
        }
    }

    private void removePetArtifacts(Entity entity, UUID ownerUuid, UUID entityUuid) {
        if (entityUuid != null) {
            removeHoverNameDisplay(entityUuid);
            clearHoverTargetsForPet(entityUuid);
            idleEmoteAt.remove(entityUuid);
        }

        if (entity != null) {
            if (ownerUuid != null) {
                clearOwnerNoCollision(ownerUuid, entity);
            }
            entity.remove();
        }
    }

    private boolean shouldHidePetFromViewer(Player viewer, UUID ownerUuid) {
        return !viewer.getUniqueId().equals(ownerUuid)
                && plugin.getSettingsManager().isHideOtherPetsEnabled(viewer.getUniqueId());
    }

    private void applyPetModePosture(UUID playerUuid, Entity entity) {
        boolean stayMode = plugin.getSettingsManager().isStayMode(playerUuid);

        if (entity instanceof Panda panda) {
            // Panda sit/back poses read oddly at this pet scale; keep a neutral idle stance.
            panda.setSitting(false);
            panda.setOnBack(false);
        } else if (entity instanceof Fox fox) {
            // Fox exposes a dedicated sleeping state in API, which reads better than sit for stay mode.
            fox.setSleeping(stayMode);
            if (!stayMode) {
                fox.setSitting(false);
            }
        } else if (entity instanceof Sittable sittable) {
            sittable.setSitting(stayMode);
        }

        if (entity instanceof AbstractHorse horse) {
            // Horse-like pets have idle/eating animations that fit a "stay" posture.
            horse.setEating(stayMode);
            horse.setEatingGrass(stayMode);
        }

        if (entity instanceof Armadillo armadillo) {
            if (stayMode) {
                armadillo.rollUp();
            } else {
                armadillo.rollOut();
            }
        }

        if (entity instanceof Mob mob) {
            mob.setAI(!stayMode);
            mob.setAware(!stayMode);
            if (stayMode) {
                mob.setTarget(null);
                try {
                    mob.getPathfinder().stopPathfinding();
                } catch (Exception ignored) {
                }
            }
        }

        if (stayMode && entity.getVelocity().lengthSquared() > STAY_VELOCITY_EPSILON_SQUARED) {
            entity.setVelocity(new Vector(0, 0, 0));
        }
    }

    private void enforceStayPosition(UUID playerUuid, Entity entity) {
        Location current = entity.getLocation();
        Location anchor = stayAnchors.get(playerUuid);

        if (anchor == null || anchor.getWorld() == null || !anchor.getWorld().equals(current.getWorld())) {
            stayAnchors.put(playerUuid, current.clone());
            return;
        }

        if (entity instanceof Mob mob) {
            try {
                mob.getPathfinder().stopPathfinding();
            } catch (Exception ignored) {
            }
        }

        if (entity.getVelocity().lengthSquared() > STAY_VELOCITY_EPSILON_SQUARED) {
            entity.setVelocity(new Vector(0, 0, 0));
        }

        double horizontal = horizontalDistance(current, anchor);
        double vertical = Math.abs(current.getY() - anchor.getY());
        if (horizontal <= STAY_MAX_HORIZONTAL_DRIFT && vertical <= STAY_MAX_VERTICAL_DRIFT) {
            return;
        }

        // Safety snap only for large displacement (e.g. piston/water/explosion push).
        Location target = anchor.clone();
        target.setYaw(current.getYaw());
        target.setPitch(current.getPitch());
        entity.teleport(target);
        entity.setVelocity(new Vector(0, 0, 0));
    }

    private double horizontalDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
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
            playPetAmbientSound(entity, type);
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
            case SHEEP -> {
                if (entity instanceof Sheep sheep) {
                    DyeColor color = parseSheepColor(pet.getAppearanceVariant());
                    if (color != null) {
                        sheep.setColor(color);
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
            case HORSE -> {
                if (entity instanceof Horse horse) {
                    HorseAppearance appearance = parseHorseAppearance(pet.getAppearanceVariant());
                    if (appearance != null) {
                        horse.setColor(appearance.color());
                        horse.setStyle(appearance.style());
                    }
                }
            }
            default -> {
            }
        }
    }

    public String formatMaterialName(Material material) {
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
        if (!(petEntity instanceof LivingEntity livingEntity)) return;

        livingEntity.setCollidable(false);

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = collisionTeamName(player.getUniqueId());
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        if (!team.hasEntity(petEntity)) {
            team.addEntity(petEntity);
        }
    }

    private void clearOwnerNoCollision(UUID playerUuid, Entity petEntity) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(collisionTeamName(playerUuid));
        if (team == null) return;

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
        Entity display = findHoverNameDisplay(petEntityUuid);
        if (display != null) {
            display.remove();
        }
        hoverNameDisplays.remove(petEntityUuid);
    }

    private void syncHoverNamePosition(Entity petEntity) {
        Entity display = findHoverNameDisplay(petEntity.getUniqueId());
        if (display == null) {
            hoverNameDisplays.remove(petEntity.getUniqueId());
            return;
        }

        display.teleport(getHoverNameLocation(petEntity));
    }

    private Location getHoverNameLocation(Entity entity) {
        return entity.getLocation().add(0, entity.getHeight() + 0.25, 0);
    }

    private Entity findHoverNameDisplay(UUID petEntityUuid) {
        UUID displayUuid = hoverNameDisplays.get(petEntityUuid);
        Entity display = findEntityByUuid(displayUuid);
        if (display != null) {
            return display;
        }

        String parentUuid = petEntityUuid.toString();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                String storedParent = entity.getPersistentDataContainer().get(PET_NAME_DISPLAY_KEY, PersistentDataType.STRING);
                if (parentUuid.equals(storedParent)) {
                    hoverNameDisplays.put(petEntityUuid, entity.getUniqueId());
                    return entity;
                }
            }
        }

        return null;
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
        boolean baby = type.isBaby();

        return switch (type.getEntityType()) {
            case CHICKEN -> chooseAmbientSound(baby, "ENTITY_CHICKEN_AMBIENT", "ENTITY_BABY_CHICKEN_AMBIENT");
            case PIG -> chooseAmbientSound(baby, "ENTITY_PIG_AMBIENT", "ENTITY_BABY_PIG_AMBIENT");
            case COW -> chooseAmbientSound(baby, "ENTITY_COW_AMBIENT", "ENTITY_BABY_COW_AMBIENT");
            case SHEEP -> chooseAmbientSound(baby, "ENTITY_SHEEP_AMBIENT", "ENTITY_BABY_SHEEP_AMBIENT");
            case SQUID -> chooseAmbientSound(baby, "ENTITY_SQUID_AMBIENT");
            case RABBIT -> chooseAmbientSound(baby, "ENTITY_RABBIT_AMBIENT", "ENTITY_BABY_RABBIT_AMBIENT");
            case HORSE -> chooseAmbientSound(baby, "ENTITY_HORSE_AMBIENT", "ENTITY_BABY_HORSE_AMBIENT");
            case DONKEY -> chooseAmbientSound(baby, "ENTITY_DONKEY_AMBIENT", "ENTITY_BABY_DONKEY_AMBIENT");
            case MULE -> chooseAmbientSound(baby, "ENTITY_MULE_AMBIENT", "ENTITY_BABY_MULE_AMBIENT");
            case DOLPHIN -> chooseAmbientSound(baby, "ENTITY_DOLPHIN_AMBIENT", "ENTITY_BABY_DOLPHIN_AMBIENT");
            case NAUTILUS -> chooseAmbientSound(baby, "ENTITY_NAUTILUS_AMBIENT", "ENTITY_BABY_NAUTILUS_AMBIENT");
            case ARMADILLO -> chooseAmbientSound(baby, "ENTITY_ARMADILLO_AMBIENT", "ENTITY_BABY_ARMADILLO_AMBIENT");
            case MOOSHROOM -> chooseAmbientSound(baby, "ENTITY_MOOSHROOM_AMBIENT", "ENTITY_BABY_MOOSHROOM_AMBIENT");
            case TURTLE -> chooseAmbientSound(baby, "ENTITY_TURTLE_AMBIENT_LAND", "ENTITY_TURTLE_SHAMBLE_BABY");
            case LLAMA, TRADER_LLAMA -> chooseAmbientSound(baby, "ENTITY_LLAMA_AMBIENT", "ENTITY_BABY_LLAMA_AMBIENT");
            case FOX -> chooseAmbientSound(baby, "ENTITY_FOX_AMBIENT", "ENTITY_BABY_FOX_AMBIENT");
            case OCELOT -> chooseAmbientSound(baby, "ENTITY_OCELOT_AMBIENT", "ENTITY_BABY_OCELOT_AMBIENT");
            case POLAR_BEAR -> chooseAmbientSound(baby, "ENTITY_POLAR_BEAR_AMBIENT", "ENTITY_BABY_POLAR_BEAR_AMBIENT");
            case CAMEL -> chooseAmbientSound(baby, "ENTITY_CAMEL_AMBIENT", "ENTITY_BABY_CAMEL_AMBIENT");
            case GOAT -> chooseAmbientSound(baby, "ENTITY_GOAT_AMBIENT", "ENTITY_BABY_GOAT_AMBIENT");
            case PANDA -> chooseAmbientSound(baby, "ENTITY_PANDA_AMBIENT", "ENTITY_BABY_PANDA_AMBIENT");
            default -> null;
        };
    }

    private void cleanupCollisionTeams() {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : new ArrayList<>(scoreboard.getTeams())) {
            if (team.getName().startsWith("petnc_")) {
                team.unregister();
            }
        }
    }

    private Sound chooseAmbientSound(boolean baby, String adultSoundName, String... babySoundNames) {
        if (baby) {
            Sound babySound = resolveAmbientSound(babySoundNames);
            if (babySound != null) {
                return babySound;
            }
        }
        return resolveAmbientSound(adultSoundName);
    }

    private Sound resolveAmbientSound(String... soundNames) {
        for (String soundName : soundNames) {
            try {
                Object candidate = Sound.class.getField(soundName).get(null);
                if (candidate instanceof Sound sound) {
                    return sound;
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // Server API can differ by version; use the next candidate.
            }
        }
        return null;
    }

    private void playPetSound(Location location, PetType type, float volume, float pitch, UUID ownerUuid) {
        if (location.getWorld() == null || type == null) {
            return;
        }

        if (ownerUuid != null && !isPetSoundsEnabled(ownerUuid)) {
            return;
        }

        Sound ambientSound = getPetAmbientSound(type);
        if (ambientSound == null) {
            return;
        }

        location.getWorld().playSound(location, ambientSound, volume, pitch);
    }

    private Fox.Type randomFoxType() {
        Fox.Type[] values = Fox.Type.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    private String randomClimateVariant() {
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

    private String randomHorseVariant() {
        Horse.Color[] colors = Horse.Color.values();
        Horse.Style[] styles = Horse.Style.values();

        Horse.Color color = colors[ThreadLocalRandom.current().nextInt(colors.length)];
        Horse.Style style = styles[ThreadLocalRandom.current().nextInt(styles.length)];
        return color.name() + ":" + style.name();
    }

    private DyeColor randomSheepColor() {
        DyeColor[] colors = DyeColor.values();
        return colors[ThreadLocalRandom.current().nextInt(colors.length)];
    }

    private HorseAppearance parseHorseAppearance(String value) {
        if (value == null || value.isBlank()) return null;

        String[] parts = value.split(":", 2);
        if (parts.length != 2) return null;

        try {
            Horse.Color color = Horse.Color.valueOf(parts[0]);
            Horse.Style style = Horse.Style.valueOf(parts[1]);
            return new HorseAppearance(color, style);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record HorseAppearance(Horse.Color color, Horse.Style style) {}

    private Pig.Variant parsePigVariant(String value) {
        return parseClimateVariant(value,
                Pig.Variant.COLD,
                Pig.Variant.TEMPERATE,
                Pig.Variant.WARM);
    }

    private Chicken.Variant parseChickenVariant(String value) {
        return parseClimateVariant(value,
                Chicken.Variant.COLD,
                Chicken.Variant.TEMPERATE,
                Chicken.Variant.WARM);
    }

    private DyeColor parseSheepColor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DyeColor.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private <T> T parseClimateVariant(String value, T cold, T temperate, T warm) {
        if (value == null) return null;
        return switch (value) {
            case "COLD" -> cold;
            case "TEMPERATE" -> temperate;
            case "WARM" -> warm;
            default -> null;
        };
    }
}
