package com.petsplugin.command;

import com.petsplugin.PetsPlugin;
import com.petsplugin.gui.PetCollectionGUI;
import com.petsplugin.model.PetInstance;
import com.petsplugin.model.PetType;
import com.petsplugin.model.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PetsCommand implements CommandExecutor, TabExecutor {

    private final PetsPlugin plugin;

    public PetsCommand(PetsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("pets.use")) {
            player.sendMessage(Component.text("You don't have permission to use this command.")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            new PetCollectionGUI(plugin, player).open(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "help" -> handleHelp(player);
            case "give" -> handleGive(player, args);
            case "hatch" -> handleHatch(player);
            case "select" -> handleSelect(player, args);
            case "deselect" -> handleDeselect(player);
            case "info" -> handleInfo(player);
            case "setlevel" -> handleSetLevel(player, args);
            case "reload" -> handleReload(player);
            case "incubator" -> handleIncubator(player);
            default -> {
                player.sendMessage(Component.text("Unknown subcommand. Use /pets help")
                        .color(NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean handleHelp(Player player) {
        player.sendMessage(Component.text("─── Pets Help ───").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("/pets").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Open pet collection").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/pets info").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Show active pet stats").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/pets select <id>").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Select a pet by DB ID").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/pets deselect").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Deselect current pet").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/pets hatch").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Instantly hatch incubating eggs").color(NamedTextColor.GRAY)));

        if (player.hasPermission("pets.admin")) {
            player.sendMessage(Component.text("─── Admin ───").color(NamedTextColor.RED));
            player.sendMessage(Component.text("/pets give <player> <rarity>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Give an egg").color(NamedTextColor.GRAY)));
            player.sendMessage(Component.text("/pets setlevel <level>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Set selected pet's level").color(NamedTextColor.GRAY)));
            player.sendMessage(Component.text("/pets incubator").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Get an incubator item").color(NamedTextColor.GRAY)));
            player.sendMessage(Component.text("/pets reload").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Reload config").color(NamedTextColor.GRAY)));
        }
        return true;
    }

    private boolean handleGive(Player player, String[] args) {
        if (!player.hasPermission("pets.admin")) {
            player.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /pets give <player> <rarity>").color(NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED));
            return true;
        }

        Rarity rarity;
        try {
            rarity = Rarity.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Invalid rarity. Use: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY")
                    .color(NamedTextColor.RED));
            return true;
        }

        ItemStack egg = plugin.getEggManager().createEgg(rarity);
        target.getInventory().addItem(egg);
        player.sendMessage(Component.text("Gave " + rarity.name() + " egg to " + target.getName())
                .color(NamedTextColor.GREEN));
        return true;
    }

    private boolean handleHatch(Player player) {
        int hatched = plugin.getIncubatorManager().instantHatch(player.getUniqueId());
        if (hatched > 0) {
            player.sendMessage(Component.text("Instantly hatched " + hatched + " egg(s)!")
                    .color(NamedTextColor.GREEN));
            plugin.getPetManager().refreshCache(player.getUniqueId());
        } else {
            player.sendMessage(Component.text("You don't have any eggs incubating.")
                    .color(NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleSelect(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /pets select <pet_id>").color(NamedTextColor.RED));
            return true;
        }

        try {
            int petId = Integer.parseInt(args[1]);
            List<PetInstance> pets = plugin.getPetManager().getPlayerPets(player.getUniqueId());
            for (PetInstance pet : pets) {
                if (pet.getDatabaseId() == petId) {
                    plugin.getPetManager().selectPet(player.getUniqueId(), pet);
                    return true;
                }
            }
            player.sendMessage(Component.text("Pet not found with that ID.").color(NamedTextColor.RED));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("ID must be a number.").color(NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleDeselect(Player player) {
        plugin.getPetManager().deselectPet(player.getUniqueId());
        player.sendMessage(Component.text("Pet deselected.").color(NamedTextColor.GRAY));
        return true;
    }

    private boolean handleInfo(Player player) {
        PetInstance pet = plugin.getPetManager().getActivePet(player.getUniqueId());
        if (pet == null) {
            pet = plugin.getPetManager().getSelectedPet(player.getUniqueId());
        }
        if (pet == null) {
            player.sendMessage(Component.text("You don't have an active pet.").color(NamedTextColor.RED));
            return true;
        }

        PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
        if (type == null) {
            player.sendMessage(Component.text("Unknown pet type.").color(NamedTextColor.RED));
            return true;
        }

        int maxLevel = plugin.getConfig().getInt("leveling.max_level", 10);

        player.sendMessage(Component.text("─── " + pet.getDisplayName(type) + " ───")
                .color(type.getRarity().getColor()));
        player.sendMessage(Component.text("  Type: ").color(NamedTextColor.GRAY)
                .append(Component.text(type.getDisplayName()).color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  Rarity: ").color(NamedTextColor.GRAY)
                .append(Component.text(type.getRarity().name()).color(type.getRarity().getColor())));
        player.sendMessage(Component.text("  Level: ").color(NamedTextColor.GRAY)
                .append(Component.text(pet.getLevel() + "/" + maxLevel).color(NamedTextColor.YELLOW)));

        if (pet.getLevel() < maxLevel) {
            double nextXp = plugin.getPetManager().getXpForLevel(pet.getLevel() + 1);
            player.sendMessage(Component.text("  XP: ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.format("%.0f/%.0f", pet.getXp(), nextXp))
                            .color(NamedTextColor.AQUA)));
        }

        player.sendMessage(Component.text("  Status: ").color(NamedTextColor.GRAY)
                .append(Component.text(pet.getStatus().getDisplay()).color(NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("  " + type.getAttributeDisplay() + ": ").color(NamedTextColor.GRAY)
                .append(Component.text("+" + String.format("%.2f", type.getAttributeAtLevel(pet.getLevel())))
                        .color(NamedTextColor.GREEN)));
        return true;
    }

    private boolean handleSetLevel(Player player, String[] args) {
        if (!player.hasPermission("pets.admin")) {
            player.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /pets setlevel <level>").color(NamedTextColor.RED));
            return true;
        }

        PetInstance pet = plugin.getPetManager().getActivePet(player.getUniqueId());
        if (pet == null) {
            pet = plugin.getPetManager().getSelectedPet(player.getUniqueId());
        }
        if (pet == null) {
            player.sendMessage(Component.text("Select a pet first!").color(NamedTextColor.RED));
            return true;
        }

        try {
            int level = Integer.parseInt(args[1]);
            if (level < 1) level = 1;
            plugin.getPetManager().setLevel(player, pet, level);
            player.sendMessage(Component.text("Set pet level to " + pet.getLevel() + ".")
                    .color(NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Level must be a number.").color(NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleReload(Player player) {
        if (!player.hasPermission("pets.admin")) {
            player.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
            return true;
        }
        plugin.reload();
        player.sendMessage(Component.text("[BabyPets] Config reloaded!").color(NamedTextColor.GREEN));
        return true;
    }

    private boolean handleIncubator(Player player) {
        if (!player.hasPermission("pets.admin")) {
            player.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
            return true;
        }
        player.getInventory().addItem(plugin.getIncubatorManager().createIncubatorItem());
        player.sendMessage(Component.text("Gave you a Pet Incubator!").color(NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(
                    List.of("help", "info", "select", "deselect", "hatch"));
            if (sender.hasPermission("pets.admin")) {
                completions.addAll(List.of("give", "setlevel", "reload", "incubator"));
            }
            return filterCompletions(completions, args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give")) {
                return null; // Default player name completion
            }
            if (args[0].equalsIgnoreCase("select") && sender instanceof Player player) {
                List<String> ids = new ArrayList<>();
                for (PetInstance pet : plugin.getPetManager().getPlayerPets(player.getUniqueId())) {
                    ids.add(String.valueOf(pet.getDatabaseId()));
                }
                return filterCompletions(ids, args[1]);
            }
            if (args[0].equalsIgnoreCase("setlevel")) {
                return List.of("1", "5", "10");
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> rarities = new ArrayList<>();
            for (Rarity r : Rarity.values()) {
                rarities.add(r.name());
            }
            return filterCompletions(rarities, args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filterCompletions(List<String> options, String current) {
        List<String> filtered = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(current.toLowerCase())) {
                filtered.add(option);
            }
        }
        return filtered;
    }
}
