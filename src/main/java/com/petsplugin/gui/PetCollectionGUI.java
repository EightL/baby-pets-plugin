package com.petsplugin.gui;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetFollowMode;
import com.petsplugin.model.PetInstance;
import com.petsplugin.model.PetMovementType;
import com.petsplugin.model.PetType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Main pet collection GUI opened with /pets.
 * Shows all owned pets as spawn egg icons with stats.
 * Click to select/deselect. Selected pet has enchant glint.
 */
public class PetCollectionGUI extends BaseGUI {

    public enum FilterMode {
        ALL("ALL"),
        LEVEL_DESC("Level: Desc."),
        LEVEL_ASC("Level: Asc."),
        TYPE_GROUND("Type: Ground"),
        TYPE_FLYING("Type: Flying"),
        TYPE_WATER("Type: Water"),
        NAME_ASC("Name: A -> Z"),
        NAME_DESC("Name: Z -> A");

        private final String displayName;

        FilterMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public FilterMode next() {
            return switch (this) {
                case ALL -> LEVEL_DESC;
                case LEVEL_DESC -> LEVEL_ASC;
                case LEVEL_ASC -> TYPE_GROUND;
                case TYPE_GROUND -> TYPE_FLYING;
                case TYPE_FLYING -> TYPE_WATER;
                case TYPE_WATER -> NAME_ASC;
                case NAME_ASC -> NAME_DESC;
                case NAME_DESC -> ALL;
            };
        }
    }

    private final Player player;
    private final FilterMode filterMode;
    private int page;

    /** Content slots: 3 rows × 7 columns. */
    private static final int[] PET_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int PETS_PER_PAGE = PET_SLOTS.length;
    private static final int[] EMPTY_RECIPE_PATTERN_SLOTS = {
            10, 11, 12,
            19, 20, 21,
            28, 29, 30
    };

    private List<PetInstance> playerPets;
    private List<PetInstance> visiblePets;

    public PetCollectionGUI(PetsPlugin plugin, Player player) {
        this(plugin, player, 0, FilterMode.ALL);
    }

    public PetCollectionGUI(PetsPlugin plugin, Player player, int page) {
        this(plugin, player, page, FilterMode.ALL);
    }

    public PetCollectionGUI(PetsPlugin plugin, Player player, int page, FilterMode filterMode) {
        super(plugin, 6, "Pet Collection");
        this.player = player;
        this.page = page;
        this.filterMode = filterMode == null ? FilterMode.ALL : filterMode;
        initializeItems();
    }

    private void initializeItems() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, null);
        }

        fillRow(0, Material.GRAY_STAINED_GLASS_PANE);
        fillRow(4, Material.PINK_STAINED_GLASS_PANE);
        fillRow(5, Material.BLACK_STAINED_GLASS_PANE);
        fillContentSides();

        playerPets = plugin.getPetManager().getPlayerPets(player.getUniqueId());
        visiblePets = buildVisiblePets(playerPets);

        int totalPages = Math.max(1, (int) Math.ceil((double) visiblePets.size() / PETS_PER_PAGE));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        if (playerPets.isEmpty()) {
            renderEmptyStateRecipe();
        } else if (visiblePets.isEmpty()) {
            renderNoResultsState();
        }

        int startIndex = page * PETS_PER_PAGE;
        int endIndex = Math.min(startIndex + PETS_PER_PAGE, visiblePets.size());

        for (int i = startIndex; i < endIndex; i++) {
            int slotIdx = i - startIndex;
            if (slotIdx >= PET_SLOTS.length) break;

            PetInstance pet = visiblePets.get(i);
            PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
            if (type == null) continue;

            ItemStack item = new ItemStack(type.getIcon());
            ItemMeta meta = item.getItemMeta();

            Component name = Component.text(pet.getDisplayName(type))
                    .color(type.getRarity().getColor())
                    .decoration(TextDecoration.ITALIC, false);

            if (pet.isSelected()) {
                name = Component.text("✦ ").color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true)
                        .append(name)
                        .append(Component.text(" ✦").color(NamedTextColor.GREEN));
            }
            meta.displayName(name);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(type.getDescription()).color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, true));
            lore.add(Component.empty());
            lore.add(Component.text("Rarity: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(type.getRarity().name()).color(type.getRarity().getColor())));

            int maxLevel = plugin.getConfig().getInt("leveling.max_level", 10);
            lore.add(Component.text("Level: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(pet.getLevel() + "/" + maxLevel).color(NamedTextColor.YELLOW)));

            if (pet.getLevel() < maxLevel) {
                double nextXp = plugin.getPetManager().getXpForLevel(pet.getLevel() + 1);
                lore.add(Component.text("XP: ").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(String.format("%.0f/%.0f", pet.getXp(), nextXp))
                                .color(NamedTextColor.AQUA)));

                int barLength = 20;
                int filled = (int) ((pet.getXp() / nextXp) * barLength);
                StringBuilder bar = new StringBuilder();
                for (int b = 0; b < barLength; b++) {
                    bar.append(b < filled ? "█" : "░");
                }
                lore.add(Component.text(bar.toString()).color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("MAX LEVEL").color(NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true));
            }

            lore.add(Component.empty());
            lore.add(Component.text("Bonus: ").color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" " + type.getAttributeDisplay() + ": ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + type.formatAttributeBonus(pet.getLevel()))
                            .color(NamedTextColor.GREEN)));
            lore.add(Component.text(" Growth: ").color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + type.formatAttributePerLevel() + "/level")
                            .color(NamedTextColor.GRAY)));
            lore.add(Component.text(" Status: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(pet.getStatus().getDisplay()).color(NamedTextColor.YELLOW)));
            lore.add(Component.empty());

            if (pet.isSelected()) {
                lore.add(Component.text("Left-click to deselect").color(NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Right-click to view details").color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Left-click to select").color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Right-click to view details").color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);

            if (pet.isSelected()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);
            inventory.setItem(PET_SLOTS[slotIdx], item);
        }

        inventory.setItem(40, createFollowModeItem());
        inventory.setItem(45, createPageArrow(page > 0, false));
        inventory.setItem(47, createSettingsButton());
        inventory.setItem(49, createPageInfo(page, totalPages));
        inventory.setItem(51, createFilterItem());
        inventory.setItem(53, createPageArrow(page < totalPages - 1, true));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 40) {
            PetFollowMode current = plugin.getSettingsManager().getFollowMode(player.getUniqueId());
            PetFollowMode next = current == PetFollowMode.FOLLOW ? PetFollowMode.STAY : PetFollowMode.FOLLOW;
            plugin.getPetManager().setFollowMode(player, next);
            new PetCollectionGUI(plugin, player, page, filterMode).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 45) {
            if (page > 0) {
                new PetCollectionGUI(plugin, player, page - 1, filterMode).open(player);
            }
            return;
        }

        if (slot == 47) {
            new PetSettingsGUI(plugin, player, page, filterMode).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 51) {
            new PetCollectionGUI(plugin, player, 0, filterMode.next()).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 53) {
            int totalPages = Math.max(1, (int) Math.ceil((double) visiblePets.size() / PETS_PER_PAGE));
            if (page < totalPages - 1) {
                new PetCollectionGUI(plugin, player, page + 1, filterMode).open(player);
            }
            return;
        }

        for (int i = 0; i < PET_SLOTS.length; i++) {
            if (slot != PET_SLOTS[i]) continue;

            int petIndex = page * PETS_PER_PAGE + i;
            if (petIndex >= visiblePets.size()) return;

            PetInstance pet = visiblePets.get(petIndex);
            if (event.isLeftClick()) {
                if (pet.isSelected()) {
                    plugin.getPetManager().deselectPet(player.getUniqueId());
                    String msg = plugin.getConfig().getString("messages.pet_deselected",
                            "&7You deselected &e%pet_name%&7.");
                    PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
                    msg = msg.replace("%pet_name%", pet.getDisplayName(type));
                    player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacyAmpersand().deserialize(msg));
                } else {
                    plugin.getPetManager().selectPet(player.getUniqueId(), pet);
                    String msg = plugin.getConfig().getString("messages.pet_selected",
                            "&aYou selected &e%pet_name%&a!");
                    PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
                    msg = msg.replace("%pet_name%", pet.getDisplayName(type));
                    player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacyAmpersand().deserialize(msg));
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                plugin.getPetManager().refreshCache(player.getUniqueId());
                new PetCollectionGUI(plugin, player, page, filterMode).open(player);
            } else if (event.isRightClick()) {
                new PetDetailGUI(plugin, player, pet, page, filterMode).open(player);
            }
            return;
        }
    }

    private void fillRow(int row, Material material) {
        ItemStack pane = createBlankPane(material);
        int start = row * 9;
        for (int slot = start; slot < start + 9; slot++) {
            inventory.setItem(slot, pane);
        }
    }

    private void fillContentSides() {
        for (int row = 1; row <= 3; row++) {
            inventory.setItem(row * 9, createBlankPane(Material.GRAY_STAINED_GLASS_PANE));
            inventory.setItem(row * 9 + 8, createBlankPane(Material.GRAY_STAINED_GLASS_PANE));
        }
    }

    private ItemStack createBlankPane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPageArrow(boolean enabled, boolean next) {
        if (!enabled) {
            return createBlankPane(Material.BLACK_STAINED_GLASS_PANE);
        }

        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        meta.displayName(Component.text(next ? "Next Page" : "Previous Page")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        arrow.setItemMeta(meta);
        return arrow;
    }

    private ItemStack createPageInfo(int page, int totalPages) {
        ItemStack pageInfo = new ItemStack(Material.BOOK);
        ItemMeta pageMeta = pageInfo.getItemMeta();
        pageMeta.displayName(Component.text("Page " + (page + 1) + "/" + totalPages)
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        pageMeta.lore(List.of(
                Component.text("Showing: " + visiblePets.size() + "/" + playerPets.size())
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        pageInfo.setItemMeta(pageMeta);
        return pageInfo;
    }

    private ItemStack createFilterItem() {
        ItemStack filterItem = new ItemStack(Material.HOPPER);
        ItemMeta filterMeta = filterItem.getItemMeta();
        filterMeta.displayName(Component.text("Filter: " + filterMode.getDisplayName())
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        filterMeta.lore(List.of(
                Component.text("Click to cycle filters").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Level, type, name, then back to all.").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        filterItem.setItemMeta(filterMeta);
        return filterItem;
    }

    private ItemStack createSettingsButton() {
        ItemStack settingsBtn = new ItemStack(Material.COMPARATOR);
        ItemMeta settingsMeta = settingsBtn.getItemMeta();
        settingsMeta.displayName(Component.text("Settings").color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        settingsMeta.lore(List.of(
                Component.empty(),
                Component.text("Manage hide-other-pets and").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("review naming controls.").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Click to open").color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        settingsBtn.setItemMeta(settingsMeta);
        return settingsBtn;
    }

    private ItemStack createFollowModeItem() {
        PetFollowMode mode = plugin.getSettingsManager().getFollowMode(player.getUniqueId());
        Material material = mode == PetFollowMode.FOLLOW ? Material.LEAD : Material.BELL;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(mode == PetFollowMode.FOLLOW ? "Mode: Follow" : "Mode: Stay")
                .color(mode == PetFollowMode.FOLLOW ? NamedTextColor.GREEN : NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text("Follow keeps your pet near you.").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Stay keeps it in place.").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Click to toggle").color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private void renderEmptyStateRecipe() {
        Material flower = Material.matchMaterial("GOLDEN_DANDELION");
        if (flower == null) {
            flower = Material.DANDELION;
        }

        fillEmptyRecipeBackground();

        Material[] ingredients = {
                Material.COPPER_BLOCK, Material.LIGHTNING_ROD, Material.COPPER_BLOCK,
                Material.IRON_INGOT, Material.GLASS, Material.IRON_INGOT,
                Material.IRON_BLOCK, flower, Material.IRON_BLOCK
        };

        for (int i = 0; i < EMPTY_RECIPE_PATTERN_SLOTS.length; i++) {
            int slot = EMPTY_RECIPE_PATTERN_SLOTS[i];
            ItemStack ingredient = new ItemStack(ingredients[i]);
            ItemMeta meta = ingredient.getItemMeta();
            meta.displayName(Component.text(formatMaterialName(ingredients[i]))
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            ingredient.setItemMeta(meta);
            inventory.setItem(slot, ingredient);
        }

        inventory.setItem(23, createRecipeArrow());

        ItemStack result = plugin.getIncubatorManager().createIncubatorItem().clone();
        ItemMeta resultMeta = result.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Craft this to start hatching eggs.").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("The recipe works in a normal crafting table").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("and shows in the vanilla recipe book.").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        resultMeta.lore(lore);
        result.setItemMeta(resultMeta);
        inventory.setItem(25, result);
    }

    private void fillEmptyRecipeBackground() {
        ItemStack filler = createBlankPane(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot : PET_SLOTS) {
            inventory.setItem(slot, filler);
        }
    }

    private void renderNoResultsState() {
        fillEmptyRecipeBackground();

        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("No Pets Match")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Cycle the filter to view a different slice.").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        inventory.setItem(22, item);
    }

    private ItemStack createRecipeArrow() {
        ItemStack arrow = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = arrow.getItemMeta();
        meta.displayName(Component.text("Crafting Output")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        arrow.setItemMeta(meta);
        return arrow;
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

    private List<PetInstance> buildVisiblePets(List<PetInstance> pets) {
        List<PetInstance> filtered = new ArrayList<>();
        for (PetInstance pet : pets) {
            PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
            if (type == null) {
                continue;
            }
            if (!matchesFilter(type)) {
                continue;
            }
            filtered.add(pet);
        }

        Comparator<PetInstance> baseComparator = Comparator.comparingLong(PetInstance::getObtainedAt);
        switch (filterMode) {
            case LEVEL_DESC ->
                    filtered.sort(Comparator.comparingInt(PetInstance::getLevel).reversed().thenComparing(baseComparator));
            case LEVEL_ASC ->
                    filtered.sort(Comparator.comparingInt(PetInstance::getLevel).thenComparing(baseComparator));
            case NAME_ASC ->
                    filtered.sort(Comparator.comparing(this::getSortableName, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(baseComparator));
            case NAME_DESC ->
                    filtered.sort(Comparator.comparing(this::getSortableName, String.CASE_INSENSITIVE_ORDER)
                            .reversed()
                            .thenComparing(baseComparator));
            default -> {
            }
        }
        return filtered;
    }

    private boolean matchesFilter(PetType type) {
        return switch (filterMode) {
            case TYPE_GROUND -> type.getMovementType() == PetMovementType.GROUND;
            case TYPE_FLYING -> type.getMovementType() == PetMovementType.FLYING;
            case TYPE_WATER -> type.getMovementType() == PetMovementType.WATER;
            default -> true;
        };
    }

    private String getSortableName(PetInstance pet) {
        PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
        if (type == null) {
            return "";
        }

        String name = pet.getDisplayName(type);
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
