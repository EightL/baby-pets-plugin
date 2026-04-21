package com.petsplugin.gui;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetInstance;
import com.petsplugin.model.PetMovementType;
import com.petsplugin.model.PetStatus;
import com.petsplugin.model.PetType;
import com.petsplugin.model.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Expanded pet details view with a themed 6-row layout.
 */
public class PetDetailGUI extends BaseGUI {

    private static final int[] FRAME_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17,
            18, 26,
            27, 35,
            36, 44,
            45, 46, 47, 48, 49, 50, 51, 52, 53
    };

    private static final int[] DETAIL_PANEL_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int[] ACCENT_SLOTS = {
            11, 13, 15,
            20, 24,
            29, 33,
            38, 42
    };

    private final Player player;
    private final PetInstance pet;
    private final int returnPage;
    private final PetCollectionGUI.FilterMode returnFilterMode;
    private final Set<Rarity> returnRarityFilters;

    public PetDetailGUI(PetsPlugin plugin, Player player, PetInstance pet, int returnPage,
                        PetCollectionGUI.FilterMode returnFilterMode,
                        Set<Rarity> returnRarityFilters) {
        super(plugin, 6, localizedTitle(plugin, "petdetailgui.title", "Pet Details"));
        this.player = player;
        this.pet = pet;
        this.returnPage = returnPage;
        this.returnFilterMode = returnFilterMode == null ? PetCollectionGUI.FilterMode.ALL : returnFilterMode;
        this.returnRarityFilters = EnumSet.noneOf(Rarity.class);
        if (returnRarityFilters != null) {
            this.returnRarityFilters.addAll(returnRarityFilters);
        }
        initializeItems();
    }

    private void initializeItems() {
        PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
        if (type == null) {
            fillBackground(Material.BLACK_STAINED_GLASS_PANE);
            fillBottomBar();
            setBackButton(45);
            return;
        }

        int maxLevel = plugin.getMaxLevel();
        applyTheme(type.getMovementType());
        fillBottomBar();

        inventory.setItem(20, createProgressItem(type, maxLevel));
        inventory.setItem(22, createPetSummaryItem(type, maxLevel));
        inventory.setItem(24, createCareItem(type));

        inventory.setItem(30, createAbilityItem(type, maxLevel));
        inventory.setItem(31, createStatusItem());
        inventory.setItem(32, createMetadataItem(type));

        inventory.setItem(45, createBackButton());
        inventory.setItem(49, createSelectButton());
        inventory.setItem(53, createDeleteButton());
    }

    private void applyTheme(PetMovementType movementType) {
        Material frame = switch (movementType) {
            case WATER -> Material.BLUE_STAINED_GLASS_PANE;
            case FLYING -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case GROUND -> Material.BROWN_STAINED_GLASS_PANE;
        };

        Material panel = switch (movementType) {
            case WATER -> Material.CYAN_STAINED_GLASS_PANE;
            case FLYING -> Material.WHITE_STAINED_GLASS_PANE;
            case GROUND -> Material.GRAY_STAINED_GLASS_PANE;
        };

        Material accent = switch (movementType) {
            case WATER -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case FLYING -> Material.YELLOW_STAINED_GLASS_PANE;
            case GROUND -> Material.LIME_STAINED_GLASS_PANE;
        };

        fillBackground(frame);
        for (int slot : FRAME_SLOTS) {
            inventory.setItem(slot, createFillerPane(frame));
        }
        for (int slot : DETAIL_PANEL_SLOTS) {
            inventory.setItem(slot, createFillerPane(panel));
        }
        for (int slot : ACCENT_SLOTS) {
            inventory.setItem(slot, createFillerPane(accent));
        }
    }

    private ItemStack createPetSummaryItem(PetType type, int maxLevel) {
        ItemStack item = new ItemStack(type.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(pet.getLocalizedDisplayName(type, plugin.getLanguageManager()))
                .color(type.getRarity().getColor())
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        String rarityLabel = plugin.getPetManager().getLocalizedLabel("rarity", "Rarity");
        String levelLabel = plugin.getPetManager().getLocalizedLabel("level", "Level");
        String xpLabel = plugin.getPetManager().getLocalizedLabel("xp", "XP");
        String statusLabel = plugin.getPetManager().getLocalizedLabel("status", "Status");
        String maxLevelLabel = plugin.getPetManager().getLocalizedLabel("max_level", "MAX LEVEL");
        lore.add(Component.text(type.getLocalizedDescription(plugin.getLanguageManager()))
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.empty());

        lore.add(Component.text(rarityLabel + ": ").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(plugin.getPetManager().getLocalizedRarity(type.getRarity()))
                .color(type.getRarity().getColor())));
        lore.add(Component.text(levelLabel + ": ").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(pet.getLevel() + "/" + maxLevel).color(NamedTextColor.YELLOW)));

        if (pet.getLevel() < maxLevel) {
            double nextXp = plugin.getPetManager().getXpForLevel(pet.getLevel() + 1);
            lore.add(Component.text(xpLabel + ": ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(String.format(Locale.US, "%.0f/%.0f", pet.getXp(), nextXp))
                            .color(NamedTextColor.AQUA)));
        } else {
            lore.add(Component.text(xpLabel + ": " + maxLevelLabel)
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.text(statusLabel + ": ").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(plugin.getPetManager().getLocalizedStatusDisplay(pet.getStatus()))
                .color(statusColor(pet.getStatus()))));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createProgressItem(PetType type, int maxLevel) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage("petdetailgui.progress", "Progress").color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(type.getLocalizedDisplayName(plugin.getLanguageManager()) + " " + plugin.getLanguageManager().getString("petdetailgui.growth_tracker", "growth tracker"))
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (pet.getLevel() >= maxLevel) {
            lore.add(plugin.getLanguageManager().getMessage("petdetailgui.this_pet_reached_max_level", "This pet reached max level.").color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(plugin.getLanguageManager().getMessage("petdetailgui.no_more_xp_is_required", "No more XP is required.").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            double nextXp = plugin.getPetManager().getXpForLevel(pet.getLevel() + 1);
            double currentXp = Math.max(0, pet.getXp());
            int percent = nextXp <= 0 ? 100 : clamp((int) Math.round((currentXp / nextXp) * 100.0), 0, 100);

            lore.add(plugin.getLanguageManager().getMessage("petdetailgui.current_xp", "Current XP: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(String.format(Locale.US, "%.0f", currentXp)).color(NamedTextColor.AQUA)));
            lore.add(plugin.getLanguageManager().getMessage("petdetailgui.next_level_xp", "Next Level XP: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(String.format(Locale.US, "%.0f", nextXp)).color(NamedTextColor.YELLOW)));
            lore.add(plugin.getLanguageManager().getMessage("petdetailgui.completion", "Completion: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(percent + "%").color(NamedTextColor.GREEN)));
            lore.add(buildBarComponent(percent, 16, NamedTextColor.GREEN));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCareItem(PetType type) {
        Material material = switch (type.getMovementType()) {
            case WATER -> Material.COD;
            case FLYING -> Material.WHEAT_SEEDS;
            case GROUND -> Material.HAY_BLOCK;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage("petdetailgui.care_guide", "Care Guide").color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                plugin.getLanguageManager().getMessage("petdetailgui.type", "Type: ").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(type.getMovementType().getDisplayName()).color(NamedTextColor.WHITE)),
                plugin.getLanguageManager().getMessage("petdetailgui.foods", "Foods: ").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(plugin.getPetManager().getAllowedFoodsDisplay(type))
                                .color(NamedTextColor.YELLOW))
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAbilityItem(PetType type, int maxLevel) {
        boolean abilitiesEnabled = plugin.getPetManager().arePetAbilitiesEnabled();

        ItemStack item = new ItemStack(abilitiesEnabled ? Material.NETHER_STAR : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(abilitiesEnabled ? "Abilities" : "Vanity Mode")
                .color(abilitiesEnabled ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (abilitiesEnabled) {
            if (type.getSpecialAbility() == com.petsplugin.model.PetType.SpecialAbility.STORAGE) {
                // Storage pets: show slot progression instead of a stat bonus
                int currentSlots = type.computeActiveStorageSlots(pet.getLevel(), maxLevel);
                int maxSlots = type.getStorageSize();
                lore.add(plugin.getLanguageManager().getMessage("petdetailgui.storage", "Storage: ").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(currentSlots + " / " + maxSlots + " slots")
                                .color(NamedTextColor.AQUA)));
                lore.add(plugin.getLanguageManager().getMessage("petdetailgui.slots_unlock_as_the_pet", "Slots unlock as the pet levels up")
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(plugin.getLanguageManager().getMessage("petdetailgui.rightclick_with_empty_hand_to", "Right-click with empty hand to open bag")
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                // Normal stat-based pets
                String sign = type.isNegativeAttribute() ? "" : "+";
                lore.add(Component.text(type.getLocalizedAttributeDisplay(plugin.getLanguageManager()) + ": ").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(sign + type.formatAttributeBonus(pet.getLevel()))
                                .color(NamedTextColor.GREEN)));
                lore.add(Component.text(plugin.getLanguageManager().getString("petdetailgui.growth", "Growth: ") + sign + type.formatAttributePerLevel() + plugin.getLanguageManager().getString("petdetailgui.per_level", "/level"))
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(plugin.getLanguageManager().getString("petdetailgui.at_lv", "At Lv") + maxLevel + ": ").color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(sign + type.formatAttributeBonus(maxLevel))
                                .color(NamedTextColor.YELLOW)));

                if (type.getSpecialAbility() == com.petsplugin.model.PetType.SpecialAbility.UNDERWATER_VISION) {
                    lore.add(Component.empty());
                    lore.add(plugin.getLanguageManager().getMessage("petdetailgui.special", "Special: ").color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
                            .append(plugin.getLanguageManager().getMessage("petdetailgui.underwater_night_vision", "Underwater Night Vision").color(NamedTextColor.AQUA)));
                }
            }
        } else {
            lore.add(plugin.getLanguageManager().getMessage("petdetailgui.pet_abilities_are_disabled_in", "Pet abilities are disabled in config.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(plugin.getLanguageManager().getMessage("petdetailgui.this_pet_is_currently_vanityonly", "This pet is currently vanity-only.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStatusItem() {
        PetStatus status = pet.getStatus();
        int percent = statusPercent(status);
        NamedTextColor color = statusColor(status);

        ItemStack item = new ItemStack(statusMaterial(status));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage("petdetailgui.status_meter", "Status Meter")
                .color(color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getLanguageManager().getMessage("petdetailgui.mood", "Mood: ").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(plugin.getPetManager().getLocalizedStatusDisplay(status)).color(color)));
        lore.add(plugin.getLanguageManager().getMessage("petdetailgui.meter", "Meter: ").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(percent + "%").color(color)));
        lore.add(buildBarComponent(percent, 16, color));
        lore.add(plugin.getLanguageManager().getMessage("petdetailgui.feed_and_pet_regularly_to", "Feed and pet regularly to improve mood.")
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMetadataItem(PetType type) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage("petdetailgui.profile", "Profile").color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        String date = new SimpleDateFormat("MMM dd, yyyy").format(new Date(pet.getObtainedAt()));
        meta.lore(List.of(
                Component.text(plugin.getLanguageManager().getString("petdetailgui.obtained", "Obtained: ") + date).color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                plugin.getLanguageManager().getMessage("petdetailgui.selection", "Selection: ").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(pet.isSelected()
                                ? plugin.getLanguageManager().getMessage("petdetailgui.active", "ACTIVE").color(NamedTextColor.GREEN)
                                        .decoration(TextDecoration.BOLD, true)
                                : plugin.getLanguageManager().getMessage("petdetailgui.resting", "Resting").color(NamedTextColor.DARK_GRAY)),
                Component.text(plugin.getLanguageManager().getString("petdetailgui.species", "Species: ") + type.getLocalizedDisplayName(plugin.getLanguageManager())).color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage("petdetailgui.back", "Back").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSelectButton() {
        ItemStack item = new ItemStack(pet.isSelected() ? Material.RED_DYE : Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(pet.isSelected() ? "Deselect" : "Select")
                .color(pet.isSelected() ? NamedTextColor.RED : NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDeleteButton() {
        ItemStack item = new ItemStack(Material.TNT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage("petdetailgui.delete_pet", "Delete Pet")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                plugin.getLanguageManager().getMessage("petdetailgui.permanently_delete_this_pet", "Permanently delete this pet!")
                        .color(NamedTextColor.DARK_RED)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private Component buildBarComponent(int percent, int length, NamedTextColor fillColor) {
        int filled = clamp((int) Math.round((percent / 100.0) * length), 0, length);
        Component bar = Component.empty();
        for (int i = 0; i < length; i++) {
            NamedTextColor color = i < filled ? fillColor : NamedTextColor.DARK_GRAY;
            bar = bar.append(Component.text(i < filled ? "█" : "░")
                    .color(color)
                    .decoration(TextDecoration.ITALIC, false));
        }
        return bar;
    }

    private int statusPercent(PetStatus status) {
        return switch (status) {
            case ECSTATIC -> 100;
            case HAPPY -> 80;
            case CONTENT -> 60;
            case HUNGRY -> 35;
            case SAD -> 15;
        };
    }

    private NamedTextColor statusColor(PetStatus status) {
        return switch (status) {
            case ECSTATIC -> NamedTextColor.GOLD;
            case HAPPY -> NamedTextColor.GREEN;
            case CONTENT -> NamedTextColor.YELLOW;
            case HUNGRY -> NamedTextColor.RED;
            case SAD -> NamedTextColor.DARK_RED;
        };
    }

    private Material statusMaterial(PetStatus status) {
        return switch (status) {
            case ECSTATIC -> Material.TOTEM_OF_UNDYING;
            case HAPPY -> Material.LIME_DYE;
            case CONTENT -> Material.SLIME_BALL;
            case HUNGRY -> Material.ORANGE_DYE;
            case SAD -> Material.GRAY_DYE;
        };
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 45) {
            new PetCollectionGUI(plugin, player, returnPage, returnFilterMode, returnRarityFilters).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 49) {
            if (pet.isSelected()) {
                plugin.getPetManager().deselectPet(player.getUniqueId());
            } else {
                plugin.getPetManager().selectPet(player.getUniqueId(), pet);
            }
            plugin.getPetManager().refreshCache(player.getUniqueId());
            new PetCollectionGUI(plugin, player, returnPage, returnFilterMode, returnRarityFilters).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 53) {
            new DeleteConfirmGUI(plugin, player, pet, returnPage, returnFilterMode, returnRarityFilters).open(player);
        }
    }
}
