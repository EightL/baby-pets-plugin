package com.petsplugin.storage;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.IncubatorState;
import com.petsplugin.model.PetInstance;
import com.petsplugin.model.PetStatus;
import com.petsplugin.model.Rarity;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
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

    public void initialize() {
        try {
            String dbFile = plugin.getConfig().getString("database.file", "pets.db");
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            String url = "jdbc:sqlite:" + new File(dataFolder, dbFile).getAbsolutePath();
            connection = DriverManager.getConnection(url);

            createTables();
            plugin.getLogger().info("Database initialized: " + dbFile);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
        }
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
                        appearance_variant VARCHAR(64),
                        appearance_sound_variant VARCHAR(64)
                    )
                """);
                // Migration: add status column to existing tables
                try {
                    stmt.executeUpdate("ALTER TABLE player_pets ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'CONTENT'");
                } catch (SQLException ignored) { /* column already exists */ }
                try {
                    stmt.executeUpdate("ALTER TABLE player_pets ADD COLUMN appearance_variant VARCHAR(64)");
                } catch (SQLException ignored) { /* column already exists */ }
                try {
                    stmt.executeUpdate("ALTER TABLE player_pets ADD COLUMN appearance_sound_variant VARCHAR(64)");
                } catch (SQLException ignored) { /* column already exists */ }
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
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Player Pets CRUD
    // ══════════════════════════════════════════════════════════

    /** Save a new pet and return the generated ID. */
    public int insertPet(PetInstance pet) {
        synchronized (dbLock) {
            String sql = """
                INSERT INTO player_pets (uuid, pet_type, nickname, level, xp, is_selected, obtained_at, status, appearance_variant, appearance_sound_variant)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                ps.setString(10, pet.getAppearanceSoundVariant());
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
    public void updatePet(PetInstance pet) {
        synchronized (dbLock) {
            String sql = """
                UPDATE player_pets SET nickname=?, level=?, xp=?, is_selected=?, status=?, appearance_variant=?, appearance_sound_variant=?
                WHERE id=?
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, pet.getNickname());
                ps.setInt(2, pet.getLevel());
                ps.setDouble(3, pet.getXp());
                ps.setBoolean(4, pet.isSelected());
                ps.setString(5, pet.getStatus().name());
                ps.setString(6, pet.getAppearanceVariant());
                ps.setString(7, pet.getAppearanceSoundVariant());
                ps.setInt(8, pet.getDatabaseId());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update pet", e);
            }
        }
    }

    /** Delete a pet by its database ID. */
    public void deletePet(int petId) {
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
                        rs.getString("appearance_variant"),
                        rs.getString("appearance_sound_variant")
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load pets for " + uuid, e);
            }
        }
        return pets;
    }

    /** Deselect all pets for a player. */
    public void deselectAll(UUID uuid) {
        synchronized (dbLock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE player_pets SET is_selected=0 WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to deselect all pets", e);
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Incubators
    // ══════════════════════════════════════════════════════════

    public int insertIncubator(IncubatorState state) {
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
        synchronized (dbLock) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM incubators")) {
                while (rs.next()) {
                    states.add(new IncubatorState(
                        rs.getInt("id"),
                        rs.getString("world"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        UUID.fromString(rs.getString("owner_uuid")),
                        Rarity.valueOf(rs.getString("egg_rarity")),
                        rs.getLong("start_time"),
                        rs.getLong("duration_ms")
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load incubators", e);
            }
        }
        return states;
    }

    /** Load incubators for a specific player. */
    public List<IncubatorState> loadIncubators(UUID uuid) {
        List<IncubatorState> states = new ArrayList<>();
        synchronized (dbLock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM incubators WHERE owner_uuid=?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    states.add(new IncubatorState(
                        rs.getInt("id"),
                        rs.getString("world"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        uuid,
                        Rarity.valueOf(rs.getString("egg_rarity")),
                        rs.getLong("start_time"),
                        rs.getLong("duration_ms")
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load incubators for " + uuid, e);
            }
        }
        return states;
    }

    // ══════════════════════════════════════════════════════════
    //  Settings
    // ══════════════════════════════════════════════════════════

    public void saveSetting(UUID uuid, String key, String value) {
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
