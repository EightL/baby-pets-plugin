package com.petsplugin.manager;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetInstance;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core pet management — spawning, despawning, following, teleporting,
 * player attribute bonuses, time-based leveling, status, and petting.
 */
public class PetManager {

    private final PetsPlugin plugin;

    private final Map<UUID, UUID> activePetEntities = new ConcurrentHashMap<>();
    private final Map<UUID, PetInstance> activePets = new ConcurrentHashMap<>();
    private final Map<UUID, List<PetInstance>> playerPetsCache = new ConcurrentHashMap<>();

    public final NamespacedKey PET_ENTITY_KEY;
    public final NamespacedKey PET_OWNER_KEY;
    public final NamespacedKey PET_TYPE_KEY;

    /** NamespacedKey used for player attribute modifiers applied by pets. */
    public final NamespacedKey PET_ATTRIBUTE_KEY;

    private BukkitTask followTask;
    private BukkitTask xpTask;

    public PetManager(PetsPlugin plugin) {
        this.plugin = plugin;
        this.PET_ENTITY_KEY = new NamespacedKey(plugin, "pet_entity");
        this.PET_OWNER_KEY = new NamespacedKey(plugin, "pet_owner");
        this.PET_TYPE_KEY = new NamespacedKey(plugin, "pet_type");
        this.PET_ATTRIBUTE_KEY = new NamespacedKey(plugin, "pet_attribute_bonus");
    }

    public void initialize() {
        followTask = Bukkit.getScheduler().runTaskTimer(plugin, this::followTick, 10L, 5L);
        // XP task: every 60 seconds
        long xpInterval = plugin.getConfig().getLong("leveling.xp_interval_ticks", 1200L);
        xpTask = Bukkit.getScheduler().runTaskTimer(plugin, this::xpTick, xpInterval, xpInterval);
    }

    public void shutdown() {
        if (followTask != null) followTask.cancel();
        if (xpTask != null) xpTask.cancel();

        for (UUID playerUuid : new ArrayList<>(activePetEntities.keySet())) {
            despawnPet(playerUuid, false);
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
            mob.setCollidable(false);
            mob.setSilent(false);
            mob.setCanPickupItems(false);
            mob.setRemoveWhenFarAway(false);
            mob.setPersistent(true);

            // Baby form — default size, not scaled down
            if (type.isBaby() && mob instanceof Ageable ageable) {
                ageable.setBaby();
                ageable.setAgeLock(true);
            }

            // No custom name tag
            mob.setCustomNameVisible(false);
            mob.customName(null);

            // PDC tags
            mob.getPersistentDataContainer().set(PET_ENTITY_KEY, PersistentDataType.BYTE, (byte) 1);
            mob.getPersistentDataContainer().set(PET_OWNER_KEY, PersistentDataType.STRING,
                    player.getUniqueId().toString());
            mob.getPersistentDataContainer().set(PET_TYPE_KEY, PersistentDataType.STRING, type.getId());

            // Mob-specific config
            if (mob instanceof Fox fox) {
                fox.setFirstTrustedPlayer(player);
                fox.setSitting(false);
            }
            if (mob instanceof Bee bee) {
                bee.setHasNectar(false);
                bee.setHasStung(false);
                bee.setAnger(0);
            }
            if (mob instanceof Goat goat) {
                goat.setScreaming(false);
            }
        }

        pet.setEntityUuid(entity.getUniqueId());
        activePetEntities.put(player.getUniqueId(), entity.getUniqueId());
        activePets.put(player.getUniqueId(), pet);

        // Apply player attribute bonus
        applyPlayerAttribute(player, pet, type);

        // Effects
        spawnLoc.getWorld().spawnParticle(Particle.HEART, spawnLoc.clone().add(0, 1, 0), 5, 0.3, 0.3, 0.3);
        spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_CAT_AMBIENT, 0.8f, 1.4f);

        String msg = plugin.getConfig().getString("messages.pet_spawned",
                "&a%pet_name% &7has appeared by your side!");
        msg = msg.replace("%pet_name%", pet.getDisplayName(type));
        player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(msg));
    }

    public void despawnPet(UUID playerUuid, boolean notify) {
        UUID entityUuid = activePetEntities.remove(playerUuid);
        if (entityUuid == null) return;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(entityUuid)) {
                    Location loc = entity.getLocation();
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
    }

    public boolean isPetEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(PET_ENTITY_KEY, PersistentDataType.BYTE);
    }

    public UUID getPetOwner(Entity entity) {
        String uuid = entity.getPersistentDataContainer().get(PET_OWNER_KEY, PersistentDataType.STRING);
        return uuid != null ? UUID.fromString(uuid) : null;
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

            double distance = petEntity.getLocation().distance(player.getLocation());

            if (distance > teleportDist) {
                Location safeLoc = findSafeSpawnLocation(player.getLocation());
                petEntity.teleport(safeLoc);
                petEntity.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                        safeLoc.clone().add(0, 0.5, 0), 8, 0.2, 0.2, 0.2);
                continue;
            }

            if (distance > followDist && petEntity instanceof Mob mob) {
                mob.getPathfinder().moveTo(player.getLocation(), 1.2);
            }
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
    }

    private void playMobSound(Entity entity, PetType type) {
        Sound sound = switch (type.getEntityType()) {
            case CHICKEN -> Sound.ENTITY_CHICKEN_AMBIENT;
            case PIG -> Sound.ENTITY_PIG_AMBIENT;
            case BEE -> Sound.ENTITY_BEE_LOOP;
            case DOLPHIN -> Sound.ENTITY_DOLPHIN_AMBIENT;
            case FOX -> Sound.ENTITY_FOX_AMBIENT;
            case GOAT -> Sound.ENTITY_GOAT_AMBIENT;
            default -> Sound.ENTITY_CAT_PURREOW;
        };
        entity.getWorld().playSound(entity.getLocation(), sound, 1.0f, 1.3f);
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
}
