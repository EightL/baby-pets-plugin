package com.petsplugin.storage;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.IncubatorState;
import com.petsplugin.model.PetInstance;
import com.petsplugin.model.PetStatus;
import com.petsplugin.model.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * SQLite database manager for pet data persistence.
 * Thread-safe via synchronized blocks on dbLock.
 */
public class PetDatabaseManager {

    private final PetsPlugin plugin;
    private Connection connection;
    private final Object dbLock = new Object();

    public PetDatabaseManager(PetsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        try {
            String dbFile = plugin.getConfig().getString("database.file", "pets.db");
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            String url = "jdbc:sqlite:" + new File(dataFolder, dbFile).getAbsolutePath();
            connection = DriverManager.getConnection(url);

            createTables();
            plugin.getLogger().info("Database initialized: " + dbFile);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
            return false;
        }
    }

    private boolean isConnectionReady(String operation) {
        if (connection != null) {
            return true;
        }
        plugin.getLogger().severe("Database connection unavailable during " + operation + ".");
        return false;
    }

    private void createTables() throws SQLException {
        synchronized (dbLock) {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_pets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid VARCHAR(36) NOT NULL,
                        pet_type VARCHAR(32) NOT NULL,
                        nickname VARCHAR(64),
                        level INTEGER NOT NULL DEFAULT 1,
                        xp DOUBLE NOT NULL DEFAULT 0,
                        is_selected BOOLEAN NOT NULL DEFAULT 0,
                        obtained_at BIGINT NOT NULL DEFAULT 0,
                        status VARCHAR(16) NOT NULL DEFAULT 'CONTENT',
                        appearance_variant VARCHAR(64)
                    )
                """);
                stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_player_pets_uuid ON player_pets(uuid)
                """);
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS incubators (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        world VARCHAR(64) NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        owner_uuid VARCHAR(36) NOT NULL,
                        egg_rarity VARCHAR(16) NOT NULL,
                        start_time BIGINT NOT NULL,
                        duration_ms BIGINT NOT NULL,
                        UNIQUE(world, x, y, z)
                    )
                """);
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pet_settings (
                        uuid VARCHAR(36) NOT NULL,
                        setting_key VARCHAR(64) NOT NULL,
                        setting_value VARCHAR(128) NOT NULL,
                        PRIMARY KEY (uuid, setting_key)
                    )
                """);
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_storage (
                        uuid VARCHAR(36) NOT NULL,
                        storage_group VARCHAR(32) NOT NULL,
                        slot INTEGER NOT NULL,
                        item_data BLOB NOT NULL,
                        PRIMARY KEY (uuid, storage_group, slot)
                    )
                """);
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Player Pets CRUD
    // ══════════════════════════════════════════════════════════

    /** Save a new pet and return the generated ID. */
    public int insertPet(PetInstance pet) {
        if (!isConnectionReady("insertPet")) {
            return -1;
        }

        synchronized (dbLock) {
            String sql = """
                INSERT INTO player_pets (uuid, pet_type, nickname, level, xp, is_selected, obtained_at, status, appearance_variant)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, pet.getOwnerUuid().toString());
                ps.setString(2, pet.getPetTypeId());
                ps.setString(3, pet.getNickname());
                ps.setInt(4, pet.getLevel());
                ps.setDouble(5, pet.getXp());
                ps.setBoolean(6, pet.isSelected());
                ps.setLong(7, pet.getObtainedAt());
                ps.setString(8, pet.getStatus().name());
                ps.setString(9, pet.getAppearanceVariant());
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) {
                    int id = keys.getInt(1);
                    pet.setDatabaseId(id);
                    return id;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to insert pet", e);
            }
            return -1;
        }
    }

    /** Update an existing pet's mutable fields. */
    private void updatePet(PetInstance pet) {
        if (!isConnectionReady("updatePet")) {
            return;
        }

        synchronized (dbLock) {
            String sql = """
                UPDATE player_pets SET nickname=?, level=?, xp=?, is_selected=?, status=?, appearance_variant=?
                WHERE id=?
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, pet.getNickname());
                ps.setInt(2, pet.getLevel());
                ps.setDouble(3, pet.getXp());
                ps.setBoolean(4, pet.isSelected());
                ps.setString(5, pet.getStatus().name());
                ps.setString(6, pet.getAppearanceVariant());
                ps.setInt(7, pet.getDatabaseId());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update pet", e);
            }
        }
    }

    /**
     * Queue a pet update on Bukkit's async scheduler to avoid blocking the main thread.
     * Uses a snapshot so later PetInstance mutations do not race the write payload.
     */
    public void updatePetAsync(PetInstance pet) {
        if (pet == null) {
            return;
        }

        PetInstance snapshot = new PetInstance(
                pet.getDatabaseId(),
                pet.getOwnerUuid(),
                pet.getPetTypeId(),
                pet.getNickname(),
                pet.getLevel(),
                pet.getXp(),
                pet.isSelected(),
                pet.getObtainedAt(),
                pet.getStatus(),
                pet.getAppearanceVariant()
        );

        Runnable task = () -> updatePet(snapshot);
        if (!plugin.isEnabled()) {
            task.run();
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    /** Delete a pet by its database ID. */
    public void deletePet(int petId) {
        if (!isConnectionReady("deletePet")) {
            return;
        }

        synchronized (dbLock) {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_pets WHERE id=?")) {
                ps.setInt(1, petId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete pet", e);
            }
        }
    }

    /** Load all pets for a player. */
    public List<PetInstance> loadPets(UUID uuid) {
        List<PetInstance> pets = new ArrayList<>();
        if (!isConnectionReady("loadPets")) {
            return pets;
        }

        synchronized (dbLock) {
            String sql = "SELECT * FROM player_pets WHERE uuid=? ORDER BY obtained_at ASC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    PetStatus status;
                    try {
                        status = PetStatus.valueOf(rs.getString("status"));
                    } catch (Exception e) {
                        status = PetStatus.CONTENT;
                    }
                    pets.add(new PetInstance(
                        rs.getInt("id"),
                        uuid,
                        rs.getString("pet_type"),
                        rs.getString("nickname"),
                        rs.getInt("level"),
                        rs.getDouble("xp"),
                        rs.getBoolean("is_selected"),
                        rs.getLong("obtained_at"),
                        status,
                        rs.getString("appearance_variant")
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load pets for " + uuid, e);
            }
        }
        return pets;
    }

    // ══════════════════════════════════════════════════════════
    //  Incubators
    // ══════════════════════════════════════════════════════════

    public int insertIncubator(IncubatorState state) {
        if (!isConnectionReady("insertIncubator")) {
            return -1;
        }

        synchronized (dbLock) {
            String sql = """
                INSERT INTO incubators (world, x, y, z, owner_uuid, egg_rarity, start_time, duration_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, state.getWorld());
                ps.setInt(2, state.getX());
                ps.setInt(3, state.getY());
                ps.setInt(4, state.getZ());
                ps.setString(5, state.getOwnerUuid().toString());
                ps.setString(6, state.getEggRarity().name());
                ps.setLong(7, state.getStartTime());
                ps.setLong(8, state.getDurationMs());
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) {
                    int id = keys.getInt(1);
                    state.setDatabaseId(id);
                    return id;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to insert incubator", e);
            }
            return -1;
        }
    }

    public void deleteIncubator(int incubatorId) {
        if (!isConnectionReady("deleteIncubator")) {
            return;
        }

        synchronized (dbLock) {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM incubators WHERE id=?")) {
                ps.setInt(1, incubatorId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete incubator", e);
            }
        }
    }

    public List<IncubatorState> loadAllIncubators() {
        List<IncubatorState> states = new ArrayList<>();
        if (!isConnectionReady("loadAllIncubators")) {
            return states;
        }

        synchronized (dbLock) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM incubators")) {
                while (rs.next()) {
                    try {
                        UUID ownerUuid = UUID.fromString(rs.getString("owner_uuid"));
                        Rarity eggRarity = Rarity.valueOf(rs.getString("egg_rarity"));
                        states.add(new IncubatorState(
                            rs.getInt("id"),
                            rs.getString("world"),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z"),
                            ownerUuid,
                            eggRarity,
                            rs.getLong("start_time"),
                            rs.getLong("duration_ms")
                        ));
                    } catch (IllegalArgumentException badData) {
                        plugin.getLogger().warning("Skipping malformed incubator row id="
                                + rs.getInt("id") + ": " + badData.getMessage());
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load incubators", e);
            }
        }
        return states;
    }

    // ══════════════════════════════════════════════════════════
    //  Settings
    // ══════════════════════════════════════════════════════════

    public void saveSetting(UUID uuid, String key, String value) {
        if (!isConnectionReady("saveSetting")) {
            return;
        }

        synchronized (dbLock) {
            String sql = """
                INSERT OR REPLACE INTO pet_settings (uuid, setting_key, setting_value)
                VALUES (?, ?, ?)
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, key);
                ps.setString(3, value);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save setting", e);
            }
        }
    }

    public String loadSetting(UUID uuid, String key, String defaultValue) {
        if (!isConnectionReady("loadSetting")) {
            return defaultValue;
        }

        synchronized (dbLock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT setting_value FROM pet_settings WHERE uuid=? AND setting_key=?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, key);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getString("setting_value");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load setting", e);
            }
            return defaultValue;
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Player Storage (shared per player+group, not per pet)
    // ══════════════════════════════════════════════════════════

    /**
     * Save all items for a player's storage group (replaces existing rows).
     * Keyed by slot index 0-based within the storage slot array.
     */
    public void savePlayerStorage(UUID uuid, String storageGroup, Map<Integer, ItemStack> items) {
        if (!isConnectionReady("savePlayerStorage")) {
            return;
        }

        synchronized (dbLock) {
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM player_storage WHERE uuid=? AND storage_group=?")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, storageGroup);
                    ps.executeUpdate();
                }
                if (!items.isEmpty()) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO player_storage (uuid, storage_group, slot, item_data) VALUES (?, ?, ?, ?)")) {
                        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
                            ps.setString(1, uuid.toString());
                            ps.setString(2, storageGroup);
                            ps.setInt(3, entry.getKey());
                            ps.setBytes(4, entry.getValue().serializeAsBytes());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to save player storage [" + storageGroup + "] for " + uuid, e);
            }
        }
    }

    /** Load stored items for a player's storage group, keyed by slot index. */
    public Map<Integer, ItemStack> loadPlayerStorage(UUID uuid, String storageGroup) {
        Map<Integer, ItemStack> items = new HashMap<>();
        if (!isConnectionReady("loadPlayerStorage")) {
            return items;
        }

        synchronized (dbLock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT slot, item_data FROM player_storage WHERE uuid=? AND storage_group=?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, storageGroup);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    byte[] data = rs.getBytes("item_data");
                    try {
                        items.put(slot, ItemStack.deserializeBytes(data));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Skipping corrupt storage item at slot " + slot
                                + " [group=" + storageGroup + "] for " + uuid);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to load player storage [" + storageGroup + "] for " + uuid, e);
            }
        }
        return items;
    }

    public void close() {
        synchronized (dbLock) {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to close database", e);
            }
        }
    }
}
