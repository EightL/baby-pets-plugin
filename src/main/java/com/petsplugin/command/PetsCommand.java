package com.petsplugin.command;

import com.petsplugin.PetsPlugin;
import com.petsplugin.gui.PetCollectionGUI;
import com.petsplugin.gui.PetSettingsGUI;
import com.petsplugin.model.PetFollowMode;
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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class PetsCommand implements CommandExecutor, TabExecutor {

    private final PetsPlugin plugin;

    public PetsCommand(PetsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("petscommand.only_players_can_use_this", "Only players can use this command.").color(NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("pets.use")) {
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.you_dont_have_permission_to", "You don't have permission to use this command.")
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
            case "givepet", "addpet" -> handleGivePet(player, args);
            case "hatch" -> handleHatch(player);
            case "settings" -> handleSettings(player);
            case "select" -> handleSelect(player, args);
            case "deselect" -> handleDeselect(player);
            case "follow" -> handleFollowMode(player, PetFollowMode.FOLLOW);
            case "stay" -> handleFollowMode(player, PetFollowMode.STAY);
            case "hideothers", "hideotherpets" -> handleHideOthers(player, args);
            case "sounds", "petsounds", "mutesounds" -> handlePetSounds(player, args);
            case "notifications", "notifs", "notif" -> handlePetNotifications(player, args);
            case "info" -> handleInfo(player);
            case "setlevel" -> handleSetLevel(player, args);
            case "reload" -> handleReload(player);
            case "incubator" -> handleIncubator(player);
            default -> {
                player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.unknown_subcommand_use_pets_help", "Unknown subcommand. Use /pets help")
                        .color(NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean handleHelp(Player player) {
        player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pets_help", "─── Pets Help ───").color(NamedTextColor.GOLD));
        player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pets", "/pets").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("petscommand.open_pet_collection", " - Open pet collection").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pets_info", "/pets info").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("petscommand.show_active_pet_stats", " - Show active pet stats").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pets_settings", "/pets settings").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("petscommand.open_pet_settings", " - Open pet settings").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pets_follow", "/pets follow").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("petscommand.set_your_active_pet_to", " - Set your active pet to follow").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pets_stay", "/pets stay").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("petscommand.tell_your_active_pet_to", " - Tell your active pet to stay").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pets_hideothers_onofftoggle", "/pets hideothers [on|off|toggle]").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("petscommand.hide_other_players_pets", " - Hide other players' pets").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pets_sounds_onofftoggle", "/pets sounds [on|off|toggle]").color(NamedTextColor.YELLOW)
            .append(plugin.getLanguageManager().getMessage("petscommand.toggle_your_pet_sounds", " - Toggle your pet sounds").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pets_notifications_onofftoggle", "/pets notifications [on|off|toggle]").color(NamedTextColor.YELLOW)
            .append(plugin.getLanguageManager().getMessage("petscommand.toggle_pet_chat_notifications", " - Toggle pet chat notifications").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pets_select_id", "/pets select <id>").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("petscommand.select_a_pet_by_db", " - Select a pet by DB ID").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pets_deselect", "/pets deselect").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("petscommand.deselect_current_pet", " - Deselect current pet").color(NamedTextColor.GRAY)));

        if (player.hasPermission("pets.admin")) {
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.admin", "─── Admin ───").color(NamedTextColor.RED));
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pets_give_player_rarity", "/pets give <player> <rarity>").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("petscommand.give_an_egg", " - Give an egg").color(NamedTextColor.GRAY)));
                player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pets_givepet_pettype", "/pets givepet <pet_type>").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("petscommand.add_a_pet_directly_to", " - Add a pet directly to your collection").color(NamedTextColor.GRAY)));
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pets_setlevel_level", "/pets setlevel <level>").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("petscommand.set_selected_pets_level", " - Set selected pet's level").color(NamedTextColor.GRAY)));
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pets_incubator", "/pets incubator").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("petscommand.get_an_incubator_item", " - Get an incubator item").color(NamedTextColor.GRAY)));
                player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pets_hatch", "/pets hatch").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("petscommand.instantly_hatch_your_incubating_eggs", " - Instantly hatch your incubating eggs").color(NamedTextColor.GRAY)));
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pets_reload", "/pets reload").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("petscommand.reload_config", " - Reload config").color(NamedTextColor.GRAY)));
        }
        return true;
    }

    private boolean handleSettings(Player player) {
        new PetSettingsGUI(plugin, player, 0).open(player);
        return true;
    }

    private boolean handleGive(Player player, String[] args) {
        if (!requireAdmin(player)) {
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.usage_pets_give_player_rarity", "Usage: /pets give <player> <rarity>").color(NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.player_not_found", "Player not found.").color(NamedTextColor.RED));
            return true;
        }

        Rarity rarity;
        try {
            rarity = Rarity.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.invalid_rarity_use_common_uncommon", "Invalid rarity. Use: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY")
                    .color(NamedTextColor.RED));
            return true;
        }

        ItemStack egg = plugin.getEggManager().createEgg(rarity);
        target.getInventory().addItem(egg);
        player.sendMessage(plugin.getLanguageManager().getMessage(
                "petscommand.gave_egg_to_player",
                "Gave %rarity% egg to %player%",
                "rarity", plugin.getPetManager().getLocalizedRarity(rarity),
                "player", target.getName()
        ).color(NamedTextColor.GREEN));
        return true;
    }

    private boolean handleGivePet(Player player, String[] args) {
        if (!requireAdmin(player)) {
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.usage_pets_givepet_pettype", "Usage: /pets givepet <pet_type>").color(NamedTextColor.RED));
            return true;
        }

        String petTypeId = args[1].toLowerCase();
        PetType type = plugin.getPetTypes().get(petTypeId);
        if (type == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage(
                    "petscommand.unknown_pet_type_with_name",
                    "Unknown pet type: %type%",
                    "type", args[1]
            ).color(NamedTextColor.RED));
            player.sendMessage(plugin.getLanguageManager().getMessage(
                    "petscommand.available_types",
                    "Available: %types%",
                    "types", String.join(", ", plugin.getPetTypes().keySet())
            ).color(NamedTextColor.GRAY));
            return true;
        }

        PetInstance pet = PetInstance.createNew(player.getUniqueId(), type.getId());
        plugin.getPetManager().ensurePersistentAppearance(pet, type);
        plugin.getDatabaseManager().insertPet(pet);
        plugin.getPetManager().refreshCache(player.getUniqueId());

        player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.added", "Added ").color(NamedTextColor.GREEN)
                .append(Component.text(type.getLocalizedDisplayName(plugin.getLanguageManager())).color(type.getRarity().getColor()))
                .append(plugin.getLanguageManager().getMessage("petscommand.to_your_collection", " to your collection.").color(NamedTextColor.GREEN)));
        return true;
    }

    private boolean handleHatch(Player player) {
        if (!requireAdmin(player)) {
            return true;
        }

        int hatched = plugin.getIncubatorManager().instantHatch(player.getUniqueId());
        if (hatched > 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage(
                    "petscommand.instantly_hatched_eggs",
                    "Instantly hatched %count% egg(s)!",
                    "count", String.valueOf(hatched)
            ).color(NamedTextColor.GREEN));
            plugin.getPetManager().refreshCache(player.getUniqueId());
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.you_dont_have_any_eggs", "You don't have any eggs incubating.")
                    .color(NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleSelect(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.usage_pets_select_petid", "Usage: /pets select <pet_id>").color(NamedTextColor.RED));
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
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pet_not_found_with_that", "Pet not found with that ID.").color(NamedTextColor.RED));
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.id_must_be_a_number", "ID must be a number.").color(NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleDeselect(Player player) {
        plugin.getPetManager().deselectPet(player.getUniqueId());
        player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.pet_deselected", "Pet deselected.").color(NamedTextColor.GRAY));
        return true;
    }

    private boolean handleFollowMode(Player player, PetFollowMode mode) {
        plugin.getPetManager().setFollowMode(player, mode);
        return true;
    }

    private boolean handleHideOthers(Player player, String[] args) {
        return toggleBooleanSetting(
                player,
                args,
                "hideothers",
                () -> plugin.getSettingsManager().isHideOtherPetsEnabled(player.getUniqueId()),
                enabled -> plugin.getPetManager().setHideOtherPets(player, enabled));
    }

    private boolean handlePetSounds(Player player, String[] args) {
        return toggleBooleanSetting(
                player,
                args,
                "sounds",
                () -> plugin.getSettingsManager().isPetSoundsEnabled(player.getUniqueId()),
                enabled -> plugin.getPetManager().setPetSoundsEnabled(player, enabled));
    }

    private boolean handlePetNotifications(Player player, String[] args) {
        return toggleBooleanSetting(
                player,
                args,
                "notifications",
                () -> plugin.getSettingsManager().isPetNotificationsEnabled(player.getUniqueId()),
                enabled -> plugin.getPetManager().setPetNotificationsEnabled(player, enabled));
    }

    private boolean handleInfo(Player player) {
        PetInstance pet = plugin.getPetManager().getActivePet(player.getUniqueId());
        if (pet == null) {
            pet = plugin.getPetManager().getSelectedPet(player.getUniqueId());
        }
        if (pet == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.you_dont_have_an_active", "You don't have an active pet.").color(NamedTextColor.RED));
            return true;
        }

        PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
        if (type == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.unknown_pet_type", "Unknown pet type.").color(NamedTextColor.RED));
            return true;
        }

        int maxLevel = plugin.getMaxLevel();
        String rarityLabel = plugin.getPetManager().getLocalizedLabel("rarity", "Rarity");
        String levelLabel = plugin.getPetManager().getLocalizedLabel("level", "Level");
        String xpLabel = plugin.getPetManager().getLocalizedLabel("xp", "XP");
        String statusLabel = plugin.getPetManager().getLocalizedLabel("status", "Status");
        String bonusLabel = plugin.getPetManager().getLocalizedLabel("bonus", "Bonus");

        player.sendMessage(plugin.getLanguageManager().getMessage(
                        "petinfo.header",
                        "─── %pet_name% ───",
                        "pet_name", pet.getLocalizedDisplayName(type, plugin.getLanguageManager()))
                .color(type.getRarity().getColor()));
        player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.type", "  Type: ").color(NamedTextColor.GRAY)
                .append(Component.text(type.getLocalizedDisplayName(plugin.getLanguageManager())).color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  " + rarityLabel + ": ").color(NamedTextColor.GRAY)
            .append(Component.text(plugin.getPetManager().getLocalizedRarity(type.getRarity()))
                .color(type.getRarity().getColor())));
        player.sendMessage(Component.text("  " + levelLabel + ": ").color(NamedTextColor.GRAY)
                .append(Component.text(pet.getLevel() + "/" + maxLevel).color(NamedTextColor.YELLOW)));

        if (pet.getLevel() < maxLevel) {
            double nextXp = plugin.getPetManager().getXpForLevel(pet.getLevel() + 1);
            player.sendMessage(Component.text("  " + xpLabel + ": ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.format("%.0f/%.0f", pet.getXp(), nextXp))
                            .color(NamedTextColor.AQUA)));
        }

        player.sendMessage(Component.text("  " + statusLabel + ": ").color(NamedTextColor.GRAY)
            .append(Component.text(plugin.getPetManager().getLocalizedStatusDisplay(pet.getStatus()))
                .color(NamedTextColor.YELLOW)));
        if (plugin.getPetManager().arePetAbilitiesEnabled()) {
            if (type.getSpecialAbility() == PetType.SpecialAbility.STORAGE) {
                int activeSlots = type.computeActiveStorageSlots(pet.getLevel(), maxLevel);
                player.sendMessage(Component.text("  " + bonusLabel + ": ").color(NamedTextColor.GRAY)
                        .append(plugin.getLanguageManager().getMessage(
                                        "petinfo.storage_bonus",
                                        "+%slots% storage space",
                                        "slots", String.valueOf(activeSlots))
                                .color(NamedTextColor.GREEN)));
            } else {
                String sign = type.isNegativeAttribute() ? "" : "+";
                player.sendMessage(Component.text(plugin.getLanguageManager().getString(
                                "petinfo.attribute_line",
                                "  %attribute%: ",
                                "attribute", type.getLocalizedAttributeDisplay(plugin.getLanguageManager())))
                                .color(NamedTextColor.GRAY)
                        .append(Component.text(sign + type.formatAttributeBonus(pet.getLevel()))
                                .color(NamedTextColor.GREEN))
                        .append(plugin.getLanguageManager().getMessage(
                                        "petinfo.attribute_per_level",
                                        " (%value%/level)",
                                        "value", sign + type.formatAttributePerLevel())
                                .color(NamedTextColor.DARK_GRAY)));
            }
        }
        return true;
    }

    private boolean handleSetLevel(Player player, String[] args) {
        if (!requireAdmin(player)) {
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.usage_pets_setlevel_level", "Usage: /pets setlevel <level>").color(NamedTextColor.RED));
            return true;
        }

        PetInstance pet = plugin.getPetManager().getActivePet(player.getUniqueId());
        if (pet == null) {
            pet = plugin.getPetManager().getSelectedPet(player.getUniqueId());
        }
        if (pet == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.select_a_pet_first", "Select a pet first!").color(NamedTextColor.RED));
            return true;
        }

        try {
            int level = Integer.parseInt(args[1]);
            if (level < 1) level = 1;
            plugin.getPetManager().setLevel(player, pet, level);
            player.sendMessage(plugin.getLanguageManager().getMessage(
                    "petscommand.set_pet_level_to",
                    "Set pet level to %level%.",
                    "level", String.valueOf(pet.getLevel())
            ).color(NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.level_must_be_a_number", "Level must be a number.").color(NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleReload(Player player) {
        if (!requireAdmin(player)) {
            return true;
        }
        plugin.reload();
        player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.babypets_config_reloaded", "[BabyPets] Config reloaded!").color(NamedTextColor.GREEN));
        return true;
    }

    private boolean handleIncubator(Player player) {
        if (!requireAdmin(player)) {
            return true;
        }
        player.getInventory().addItem(plugin.getIncubatorManager().createIncubatorItem());
        player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.gave_you_a_pet_incubator", "Gave you a Pet Incubator!").color(NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(
                    List.of("help", "info", "settings", "follow", "stay", "hideothers",
                        "sounds", "notifications", "select", "deselect"));
            if (sender.hasPermission("pets.admin")) {
                completions.addAll(List.of("give", "givepet", "setlevel", "reload", "incubator", "hatch"));
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
            if (args[0].equalsIgnoreCase("givepet") || args[0].equalsIgnoreCase("addpet")) {
                return filterCompletions(new ArrayList<>(plugin.getPetTypes().keySet()), args[1]);
            }
            if (isToggleSubcommand(args[0])) {
                return filterCompletions(List.of("on", "off", "toggle"), args[1]);
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

    private boolean requireAdmin(Player player) {
        if (player.hasPermission("pets.admin")) {
            return true;
        }
        player.sendMessage(plugin.getLanguageManager().getMessage("petscommand.no_permission", "No permission.").color(NamedTextColor.RED));
        return false;
    }

    private boolean toggleBooleanSetting(Player player, String[] args, String usageKey,
                                         BooleanSupplier getter,
                                         Consumer<Boolean> setter) {
        boolean enabled;
        if (args.length >= 2) {
            String value = args[1].toLowerCase();
            if (!value.equals("on") && !value.equals("off") && !value.equals("toggle")) {
                player.sendMessage(plugin.getLanguageManager().getMessage(
                        "petscommand.usage_toggle",
                        "Usage: /pets %command% [on|off|toggle]",
                        "command", usageKey
                ).color(NamedTextColor.RED));
                return true;
            }

            boolean current = getter.getAsBoolean();
            enabled = value.equals("toggle") ? !current : value.equals("on");
        } else {
            enabled = !getter.getAsBoolean();
        }

        setter.accept(enabled);
        return true;
    }

    private boolean isToggleSubcommand(String commandName) {
        return commandName.equalsIgnoreCase("hideothers")
                || commandName.equalsIgnoreCase("hideotherpets")
                || commandName.equalsIgnoreCase("sounds")
                || commandName.equalsIgnoreCase("petsounds")
                || commandName.equalsIgnoreCase("mutesounds")
                || commandName.equalsIgnoreCase("notifications")
                || commandName.equalsIgnoreCase("notifs")
                || commandName.equalsIgnoreCase("notif");
    }
}
