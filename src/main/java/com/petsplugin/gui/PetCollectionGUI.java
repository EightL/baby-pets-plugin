package com.petsplugin.gui;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetFollowMode;
import com.petsplugin.model.PetInstance;
import com.petsplugin.model.PetMovementType;
import com.petsplugin.model.Rarity;
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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Main pet collection GUI opened with /pets.
 * Shows all owned pets as spawn egg icons with stats.
 * Click to select/deselect. Selected pet has enchant glint.
 */
public class PetCollectionGUI extends BaseGUI {

    public enum FilterMode {
        ALL("petcollectiongui.filter.all", "ALL"),
        LEVEL_DESC("petcollectiongui.filter.level_desc", "Level: Desc."),
        LEVEL_ASC("petcollectiongui.filter.level_asc", "Level: Asc."),
        TYPE_GROUND("petcollectiongui.filter.type_ground", "Type: Ground"),
        TYPE_FLYING("petcollectiongui.filter.type_flying", "Type: Flying"),
        TYPE_WATER("petcollectiongui.filter.type_water", "Type: Water"),
        NAME_ASC("petcollectiongui.filter.name_asc", "Name: A -> Z"),
        NAME_DESC("petcollectiongui.filter.name_desc", "Name: Z -> A");

        private final String langKey;
        private final String fallback;

        FilterMode(String langKey, String fallback) {
            this.langKey = langKey;
            this.fallback = fallback;
        }

        public String getDisplayName(PetsPlugin plugin) {
            return plugin.getLanguageManager().getString(langKey, fallback);
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
    private final Set<Rarity> activeRarityFilters;
    private int page;

    /** Content slots: 3 rows × 7 columns. */
    private static final int[] PET_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int[] RARITY_FILTER_SLOTS = {2, 3, 4, 5, 6};
    private static final Rarity[] RARITY_FILTER_ORDER = {
            Rarity.COMMON,
            Rarity.UNCOMMON,
            Rarity.RARE,
            Rarity.EPIC,
            Rarity.LEGENDARY
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
        this(plugin, player, 0, FilterMode.ALL, EnumSet.noneOf(Rarity.class));
    }

    public PetCollectionGUI(PetsPlugin plugin, Player player, int page, FilterMode filterMode,
                            Set<Rarity> activeRarityFilters) {
        super(plugin, 6, localizedTitle(plugin, "petcollectiongui.title", "Pet Collection"));
        this.player = player;
        this.page = page;
        this.filterMode = filterMode == null ? FilterMode.ALL : filterMode;
        this.activeRarityFilters = EnumSet.noneOf(Rarity.class);
        if (activeRarityFilters != null) {
            this.activeRarityFilters.addAll(activeRarityFilters);
        }
        initializeItems();
    }

    private void initializeItems() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, null);
        }

        fillRow(0, Material.GRAY_STAINED_GLASS_PANE);
        fillRow(4, Material.PINK_STAINED_GLASS_PANE);
        fillBottomBar();
        fillContentSides();

        playerPets = plugin.getPetManager().getPlayerPets(player.getUniqueId());
        visiblePets = buildVisiblePets(playerPets);
        renderRaritySummary();

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
        boolean abilitiesEnabled = plugin.getPetManager().arePetAbilitiesEnabled();
        String rarityLabel = plugin.getPetManager().getLocalizedLabel("rarity", "Rarity");
        String levelLabel = plugin.getPetManager().getLocalizedLabel("level", "Level");
        String xpLabel = plugin.getPetManager().getLocalizedLabel("xp", "XP");
        String statusLabel = plugin.getPetManager().getLocalizedLabel("status", "Status");
        String bonusLabel = plugin.getPetManager().getLocalizedLabel("bonus", "Bonus");
        String maxLevelLabel = plugin.getPetManager().getLocalizedLabel("max_level", "MAX LEVEL");

        for (int i = startIndex; i < endIndex; i++) {
            int slotIdx = i - startIndex;
            if (slotIdx >= PET_SLOTS.length) break;

            PetInstance pet = visiblePets.get(i);
            PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
            if (type == null) continue;

            ItemStack item = new ItemStack(type.getIcon());
            ItemMeta meta = item.getItemMeta();

            Component name = Component.text(pet.getLocalizedDisplayName(type, plugin.getLanguageManager()))
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
            lore.add(Component.text(type.getLocalizedDescription(plugin.getLanguageManager())).color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, true));
            lore.add(Component.empty());
                lore.add(Component.text(rarityLabel + ": ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(plugin.getPetManager().getLocalizedRarity(type.getRarity()))
                        .color(type.getRarity().getColor())));

            int maxLevel = plugin.getMaxLevel();
                lore.add(Component.text(levelLabel + ": ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(pet.getLevel() + "/" + maxLevel).color(NamedTextColor.YELLOW)));

            if (pet.getLevel() < maxLevel) {
                double nextXp = plugin.getPetManager().getXpForLevel(pet.getLevel() + 1);
                lore.add(Component.text(xpLabel + ": ").color(NamedTextColor.GRAY)
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
                lore.add(Component.text(maxLevelLabel).color(NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true));
            }

            lore.add(Component.empty());
            if (abilitiesEnabled) {
                lore.add(Component.text(bonusLabel + ": ").color(NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
                if (type.getSpecialAbility() == PetType.SpecialAbility.STORAGE) {
                    int activeSlots = type.computeActiveStorageSlots(pet.getLevel(), maxLevel);
                    lore.add(plugin.getLanguageManager().getMessage(
                                    "petcollectiongui.storage_bonus",
                                    " +%slots% storage space",
                                    "slots", String.valueOf(activeSlots))
                            .color(NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false));
                } else {
                    String sign = type.isNegativeAttribute() ? "" : "+";
                    lore.add(Component.text(plugin.getLanguageManager().getString(
                                    "petcollectiongui.attribute_line",
                                    " %attribute%: ",
                                    "attribute", type.getLocalizedAttributeDisplay(plugin.getLanguageManager())))
                                    .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(sign + type.formatAttributeBonus(pet.getLevel()))
                            .color(NamedTextColor.GREEN)));
                }
            }
                lore.add(Component.text(statusLabel + ": ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(plugin.getPetManager().getLocalizedStatusDisplay(pet.getStatus()))
                        .color(NamedTextColor.YELLOW)));
            lore.add(Component.empty());

            if (pet.isSelected()) {
                lore.add(plugin.getLanguageManager().getMessage("petcollectiongui.leftclick_to_deselect", "Left-click to deselect").color(NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(plugin.getLanguageManager().getMessage("petcollectiongui.rightclick_to_view_details", "Right-click to view details").color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(plugin.getLanguageManager().getMessage("petcollectiongui.leftclick_to_select", "Left-click to select").color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(plugin.getLanguageManager().getMessage("petcollectiongui.rightclick_to_view_details", "Right-click to view details").color(NamedTextColor.YELLOW)
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

        inventory.setItem(40, createFollowModeItem(player.getUniqueId(), false));
        inventory.setItem(43, createFillerPane(Material.PINK_STAINED_GLASS_PANE));
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

        Rarity rarityFilter = rarityFromSlot(slot);
        if (rarityFilter != null) {
            Set<Rarity> nextRarityFilters = copyRarityFilters();
            if (event.isRightClick()) {
                nextRarityFilters.clear();
            } else if (!nextRarityFilters.remove(rarityFilter)) {
                nextRarityFilters.add(rarityFilter);
            }

            new PetCollectionGUI(plugin, player, 0, filterMode, nextRarityFilters).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 40) {
            PetFollowMode current = plugin.getSettingsManager().getFollowMode(player.getUniqueId());
            PetFollowMode next = current == PetFollowMode.FOLLOW ? PetFollowMode.STAY : PetFollowMode.FOLLOW;
            plugin.getPetManager().setFollowMode(player, next);
            new PetCollectionGUI(plugin, player, page, filterMode, copyRarityFilters()).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 43) {
            return;
        }

        if (slot == 45) {
            if (page > 0) {
                new PetCollectionGUI(plugin, player, page - 1, filterMode, copyRarityFilters()).open(player);
            }
            return;
        }

        if (slot == 47) {
            new PetSettingsGUI(plugin, player, page, filterMode, copyRarityFilters()).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 51) {
            new PetCollectionGUI(plugin, player, 0, filterMode.next(), copyRarityFilters()).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 53) {
            int totalPages = Math.max(1, (int) Math.ceil((double) visiblePets.size() / PETS_PER_PAGE));
            if (page < totalPages - 1) {
                new PetCollectionGUI(plugin, player, page + 1, filterMode, copyRarityFilters()).open(player);
            }
            return;
        }

        for (int i = 0; i < PET_SLOTS.length; i++) {
            if (slot != PET_SLOTS[i]) continue;

            int petIndex = page * PETS_PER_PAGE + i;
            if (petIndex >= visiblePets.size()) return;

            PetInstance pet = visiblePets.get(petIndex);
            if (event.isLeftClick()) {
                PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
                String displayName = type != null ? pet.getLocalizedDisplayName(type, plugin.getLanguageManager()) : pet.getPetTypeId();
                if (pet.isSelected()) {
                    plugin.getPetManager().deselectPet(player.getUniqueId());
                    plugin.getPetManager().sendPetNotification(player,
                        "petcollectiongui.deselected_notification",
                        "petcollectiongui.deselected_notification",
                        Map.of("%pet_name%", displayName));
                } else {
                    plugin.getPetManager().selectPet(player.getUniqueId(), pet);
                    plugin.getPetManager().sendPetNotification(player,
                        "petcollectiongui.selected_notification",
                        "petcollectiongui.selected_notification",
                        Map.of("%pet_name%", displayName));
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                plugin.getPetManager().refreshCache(player.getUniqueId());
                new PetCollectionGUI(plugin, player, page, filterMode, copyRarityFilters()).open(player);
            } else if (event.isRightClick()) {
                new PetDetailGUI(plugin, player, pet, page, filterMode, copyRarityFilters()).open(player);
            }
            return;
        }
    }

    private void fillRow(int row, Material material) {
        ItemStack pane = createFillerPane(material);
        int start = row * 9;
        for (int slot = start; slot < start + 9; slot++) {
            inventory.setItem(slot, pane);
        }
    }

    private void fillContentSides() {
        for (int row = 1; row <= 3; row++) {
            inventory.setItem(row * 9, createFillerPane(Material.GRAY_STAINED_GLASS_PANE));
            inventory.setItem(row * 9 + 8, createFillerPane(Material.GRAY_STAINED_GLASS_PANE));
        }
    }

    private void renderRaritySummary() {
        Map<Rarity, Integer> totalByRarity = new EnumMap<>(Rarity.class);
        Map<Rarity, Integer> ownedByRarity = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) {
            totalByRarity.put(rarity, 0);
            ownedByRarity.put(rarity, 0);
        }

        for (PetType type : plugin.getPetTypes().values()) {
            totalByRarity.put(type.getRarity(), totalByRarity.get(type.getRarity()) + 1);
        }

        Set<String> ownedTypeIds = new HashSet<>();
        for (PetInstance pet : playerPets) {
            if (!ownedTypeIds.add(pet.getPetTypeId())) {
                continue;
            }
            PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
            if (type == null) continue;
            ownedByRarity.put(type.getRarity(), ownedByRarity.get(type.getRarity()) + 1);
        }

        boolean filteringByRarity = !activeRarityFilters.isEmpty();
        for (int i = 0; i < RARITY_FILTER_ORDER.length; i++) {
            Rarity rarity = RARITY_FILTER_ORDER[i];
            int owned = ownedByRarity.getOrDefault(rarity, 0);
            int total = totalByRarity.getOrDefault(rarity, 0);
            boolean selected = activeRarityFilters.contains(rarity);
            inventory.setItem(RARITY_FILTER_SLOTS[i],
                    createRarityItem(rarity, owned, total, filteringByRarity, selected));
        }
    }

    private ItemStack createRarityItem(Rarity rarity, int owned, int total,
                                       boolean filteringByRarity, boolean selected) {
        ItemStack item = new ItemStack(rarityDye(rarity));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(rarityTitle(rarity) + " " + plugin.getLanguageManager().getString("petcollectiongui.collection", "Collection"))
                .color(rarity.getColor())
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(owned + "/" + total + " " + plugin.getLanguageManager().getString("petcollectiongui.collected", "collected"))
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        if (!filteringByRarity) {
            lore.add(plugin.getLanguageManager().getMessage("petcollectiongui.no_rarity_filter_active", "No rarity filter active.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else if (selected) {
            lore.add(plugin.getLanguageManager().getMessage("petcollectiongui.included_in_current_filter", "Included in current filter.")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(plugin.getLanguageManager().getMessage("petcollectiongui.excluded_by_current_filter", "Excluded by current filter.")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(plugin.getLanguageManager().getMessage("petcollectiongui.leftclick_toggle_this_rarity", "Left-click: toggle this rarity").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(plugin.getLanguageManager().getMessage("petcollectiongui.rightclick_clear_rarity_filters", "Right-click: clear rarity filters").color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        if (selected) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    private Material rarityDye(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> Material.WHITE_DYE;
            case UNCOMMON -> Material.LIME_DYE;
            case RARE -> Material.BLUE_DYE;
            case EPIC -> Material.PURPLE_DYE;
            case LEGENDARY -> Material.YELLOW_DYE;
        };
    }

    private String rarityTitle(Rarity rarity) {
        return plugin.getPetManager().getLocalizedRarity(rarity);
    }

    private ItemStack createPageArrow(boolean enabled, boolean next) {
        if (!enabled) {
            return createFillerPane(Material.BLACK_STAINED_GLASS_PANE);
        }

        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage(
                        next ? "basegui.next_page" : "basegui.previous_page",
                        next ? "Next Page" : "Previous Page")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        arrow.setItemMeta(meta);
        return arrow;
    }

    private ItemStack createPageInfo(int page, int totalPages) {
        ItemStack pageInfo = new ItemStack(Material.BOOK);
        ItemMeta pageMeta = pageInfo.getItemMeta();
        pageMeta.displayName(Component.text(plugin.getLanguageManager().getString("pagination.page", "Page ") + (page + 1) + "/" + totalPages)
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        pageMeta.lore(List.of(
                Component.text(plugin.getLanguageManager().getString("pagination.showing", "Showing: ") + visiblePets.size() + "/" + playerPets.size())
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        pageInfo.setItemMeta(pageMeta);
        return pageInfo;
    }

    private ItemStack createFilterItem() {
        ItemStack filterItem = new ItemStack(Material.HOPPER);
        ItemMeta filterMeta = filterItem.getItemMeta();
        filterMeta.displayName(Component.text(plugin.getLanguageManager().getString("petcollectiongui.filter_prefix", "Filter: ")
                        + filterMode.getDisplayName(plugin))
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        filterMeta.lore(List.of(
                plugin.getLanguageManager().getMessage("petcollectiongui.click_to_cycle_filters", "Click to cycle filters").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
            plugin.getLanguageManager().getMessage("petcollectiongui.level_type_name_then_back", "Level, type, name, then back to all.").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            plugin.getLanguageManager().getMessage("petcollectiongui.use_top_dyes_to_toggle", "Use top dyes to toggle rarity filters.").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        filterItem.setItemMeta(filterMeta);
        return filterItem;
    }

    private ItemStack createSettingsButton() {
        ItemStack settingsBtn = new ItemStack(Material.COMPARATOR);
        ItemMeta settingsMeta = settingsBtn.getItemMeta();
        settingsMeta.displayName(plugin.getLanguageManager().getMessage("petcollectiongui.settings", "Settings").color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        settingsMeta.lore(List.of(
                Component.empty(),
                plugin.getLanguageManager().getMessage("petcollectiongui.manage_follow_mode_visibility", "Manage follow mode, visibility,").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                plugin.getLanguageManager().getMessage("petcollectiongui.and_pet_sound_preferences", "and pet sound preferences.").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                plugin.getLanguageManager().getMessage("petcollectiongui.click_to_open", "Click to open").color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        settingsBtn.setItemMeta(settingsMeta);
        return settingsBtn;
    }

    private void renderEmptyStateRecipe() {
        fillEmptyRecipeBackground();

        List<Material> ingredients = new ArrayList<>(EMPTY_RECIPE_PATTERN_SLOTS.length);
        String[] shape = plugin.getIncubatorManager().getIncubatorRecipeShape();
        Map<Character, Material> ingredientMap = plugin.getIncubatorManager().getIncubatorRecipeIngredients();
        for (String row : shape) {
            for (int i = 0; i < row.length(); i++) {
                ingredients.add(ingredientMap.getOrDefault(row.charAt(i), Material.AIR));
            }
        }

        for (int i = 0; i < EMPTY_RECIPE_PATTERN_SLOTS.length; i++) {
            int slot = EMPTY_RECIPE_PATTERN_SLOTS[i];
            Material ingredientMaterial = i < ingredients.size() ? ingredients.get(i) : Material.AIR;
            ItemStack ingredient = new ItemStack(ingredientMaterial);
            ItemMeta meta = ingredient.getItemMeta();
            meta.displayName(Component.text(plugin.getPetManager().formatMaterialName(ingredientMaterial))
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            ingredient.setItemMeta(meta);
            inventory.setItem(slot, ingredient);
        }

        inventory.setItem(23, createRecipeArrow());

        ItemStack result = plugin.getIncubatorManager().createIncubatorItem().clone();
        ItemMeta resultMeta = result.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getLanguageManager().getMessage("petcollectiongui.craft_this_to_start_hatching", "Craft this to start hatching eggs.").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(plugin.getLanguageManager().getMessage("petcollectiongui.the_recipe_works_in_a", "The recipe works in a normal crafting table").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(plugin.getLanguageManager().getMessage("petcollectiongui.and_shows_in_the_vanilla", "and shows in the vanilla recipe book.").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        resultMeta.lore(lore);
        result.setItemMeta(resultMeta);
        inventory.setItem(25, result);
    }

    private void fillEmptyRecipeBackground() {
        ItemStack filler = createFillerPane(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot : PET_SLOTS) {
            inventory.setItem(slot, filler);
        }
    }

    private void renderNoResultsState() {
        fillEmptyRecipeBackground();

        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage("petcollectiongui.no_pets_match", "No Pets Match")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            plugin.getLanguageManager().getMessage("petcollectiongui.adjust_rarity_dyes_or_cycle", "Adjust rarity dyes or cycle the hopper filter.").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        inventory.setItem(22, item);
    }

    private ItemStack createRecipeArrow() {
        ItemStack arrow = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = arrow.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage("petcollectiongui.crafting_output", "Crafting Output")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        arrow.setItemMeta(meta);
        return arrow;
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
            if (!activeRarityFilters.isEmpty() && !activeRarityFilters.contains(type.getRarity())) {
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

    private Set<Rarity> copyRarityFilters() {
        Set<Rarity> copy = EnumSet.noneOf(Rarity.class);
        copy.addAll(activeRarityFilters);
        return copy;
    }

    private Rarity rarityFromSlot(int slot) {
        for (int i = 0; i < RARITY_FILTER_SLOTS.length; i++) {
            if (RARITY_FILTER_SLOTS[i] == slot) {
                return RARITY_FILTER_ORDER[i];
            }
        }
        return null;
    }

    private String getSortableName(PetInstance pet) {
        PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
        if (type == null) {
            return "";
        }

        String name = pet.getLocalizedDisplayName(type, plugin.getLanguageManager());
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
