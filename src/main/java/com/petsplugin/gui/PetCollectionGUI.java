package com.petsplugin.gui;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetInstance;
import com.petsplugin.model.PetType;
import com.petsplugin.model.Rarity;
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
import java.util.List;

/**
 * Main pet collection GUI opened with /pets.
 * Shows all owned pets as spawn egg icons with stats.
 * Click to select/deselect. Selected pet has enchant glint.
 */
public class PetCollectionGUI extends BaseGUI {

    private final Player player;
    private int page;

    /** Content slots: 3 rows × 7 columns. */
    private static final int[] PET_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int PETS_PER_PAGE = PET_SLOTS.length;

    private List<PetInstance> playerPets;

    public PetCollectionGUI(PetsPlugin plugin, Player player) {
        this(plugin, player, 0);
    }

    public PetCollectionGUI(PetsPlugin plugin, Player player, int page) {
        super(plugin, 6, "Pet Collection");
        this.player = player;
        this.page = page;
        initializeItems();
    }

    private void initializeItems() {
        fillBackground(Material.GRAY_STAINED_GLASS_PANE);

        // Load player's pets
        playerPets = plugin.getPetManager().getPlayerPets(player.getUniqueId());

        int totalPages = Math.max(1, (int) Math.ceil((double) playerPets.size() / PETS_PER_PAGE));
        if (page >= totalPages) page = totalPages - 1;

        int startIndex = page * PETS_PER_PAGE;
        int endIndex = Math.min(startIndex + PETS_PER_PAGE, playerPets.size());

        for (int i = startIndex; i < endIndex; i++) {
            int slotIdx = i - startIndex;
            if (slotIdx >= PET_SLOTS.length) break;

            PetInstance pet = playerPets.get(i);
            PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
            if (type == null) continue;

            ItemStack item = new ItemStack(type.getIcon());
            ItemMeta meta = item.getItemMeta();

            // Display name
            String displayName = pet.getDisplayName(type);
            Component name = Component.text(displayName)
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

            // Lore
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(type.getDescription()).color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, true));
            lore.add(Component.empty());

            // Rarity
            lore.add(Component.text("Rarity: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(type.getRarity().name()).color(type.getRarity().getColor())));

            // Level & XP
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

                // Simple progress bar
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

            // Player attribute bonus
            lore.add(Component.text("Bonus: ").color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" " + type.getAttributeDisplay() + ": ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + String.format("%.2f", type.getAttributeAtLevel(pet.getLevel())))
                            .color(NamedTextColor.GREEN)));

            // Status
            lore.add(Component.text(" Status: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(pet.getStatus().getDisplay())
                            .color(NamedTextColor.YELLOW)));

            lore.add(Component.empty());

            // Action hint
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

            // Enchant glint for selected pet
            if (pet.isSelected()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);
            inventory.setItem(PET_SLOTS[slotIdx], item);
        }

        // Page info
        ItemStack pageInfo = new ItemStack(Material.BOOK);
        ItemMeta pageMeta = pageInfo.getItemMeta();
        pageMeta.displayName(Component.text("Page " + (page + 1) + "/" + totalPages)
                .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        pageMeta.lore(List.of(
                Component.text("Pets: " + playerPets.size()).color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        pageInfo.setItemMeta(pageMeta);
        inventory.setItem(49, pageInfo);

        // Pagination
        setPaginationControls(45, 53, page, totalPages);

        // Close button
        setCloseButton(48);

        // Delete button (slot 50)
        ItemStack deleteBtn = new ItemStack(Material.TNT);
        ItemMeta deleteMeta = deleteBtn.getItemMeta();
        deleteMeta.displayName(Component.text("Delete Selected Pet").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        deleteMeta.lore(List.of(
                Component.text("Select a pet first, then").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("click here to delete it.").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        deleteBtn.setItemMeta(deleteMeta);
        inventory.setItem(50, deleteBtn);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 48) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 45) {
            // Previous page
            if (page > 0) {
                new PetCollectionGUI(plugin, player, page - 1).open(player);
            }
            return;
        }

        if (slot == 53) {
            // Next page
            int totalPages = Math.max(1, (int) Math.ceil((double) playerPets.size() / PETS_PER_PAGE));
            if (page < totalPages - 1) {
                new PetCollectionGUI(plugin, player, page + 1).open(player);
            }
            return;
        }

        if (slot == 50) {
            // Delete selected pet
            PetInstance selected = plugin.getPetManager().getActivePet(player.getUniqueId());
            if (selected == null) {
                selected = plugin.getPetManager().getSelectedPet(player.getUniqueId());
            }
            if (selected == null) {
                player.sendMessage(Component.text("Select a pet first!").color(NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            new DeleteConfirmGUI(plugin, player, selected, page).open(player);
            return;
        }

        // Pet slot click
        for (int i = 0; i < PET_SLOTS.length; i++) {
            if (slot == PET_SLOTS[i]) {
                int petIndex = page * PETS_PER_PAGE + i;
                if (petIndex >= playerPets.size()) return;

                PetInstance pet = playerPets.get(petIndex);

                if (event.isLeftClick()) {
                    // Toggle selection
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
                    // Refresh
                    plugin.getPetManager().refreshCache(player.getUniqueId());
                    new PetCollectionGUI(plugin, player, page).open(player);
                } else if (event.isRightClick()) {
                    new PetDetailGUI(plugin, player, pet, page).open(player);
                }
                return;
            }
        }
    }
}
