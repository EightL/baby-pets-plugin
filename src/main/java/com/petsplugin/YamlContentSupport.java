package com.petsplugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class YamlContentSupport {

    private static final String VERSION_KEY = "content_version";
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private YamlContentSupport() {
    }

    static FileConfiguration loadYaml(PetsPlugin plugin, String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource(fileName)) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                boolean migrated = migrate(plugin, fileName, file, config, defaults);
                boolean addedDefaults = copyMissingPaths(config, defaults);
                if (migrated || addedDefaults) {
                    config.save(file);
                    if (addedDefaults) {
                        plugin.getLogger().info("Added missing defaults to " + fileName + ".");
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to update defaults for " + fileName + ": " + e.getMessage());
        }

        return config;
    }

    private static boolean migrate(PetsPlugin plugin, String fileName, File file,
                                   YamlConfiguration config, YamlConfiguration defaults) throws IOException {
        int currentVersion = defaults.getInt(VERSION_KEY, 1);
        int fileVersion = config.getInt(VERSION_KEY, 1);
        if (fileVersion >= currentVersion) {
            return false;
        }

        backup(plugin, fileName, file, fileVersion);
        config.set(VERSION_KEY, currentVersion);
        plugin.getLogger().info("Migrated " + fileName + " content from v" + fileVersion + " to v" + currentVersion + ".");
        return true;
    }

    private static boolean copyMissingPaths(YamlConfiguration target, YamlConfiguration defaults) {
        boolean changed = false;
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path)) {
                continue;
            }
            if (!target.contains(path)) {
                target.set(path, defaults.get(path));
                changed = true;
            }
        }
        return changed;
    }

    private static void backup(PetsPlugin plugin, String fileName, File file, int fileVersion) throws IOException {
        Path backupDir = plugin.getDataFolder().toPath().resolve("backups");
        Files.createDirectories(backupDir);

        String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP);
        Path backup = backupDir.resolve(fileName + ".v" + fileVersion + "." + timestamp + ".bak");
        Files.copy(file.toPath(), backup, StandardCopyOption.COPY_ATTRIBUTES);
        plugin.getLogger().info("Backed up " + fileName + " before migration to " + backup.getFileName() + ".");
    }
}
