package com.petsplugin.gui;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetInstance;
import com.petsplugin.model.PetType;
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
import java.util.List;

/**
 * Detailed view of a single pet — stats, ability info, obtained date.
 */
public class PetDetailGUI extends BaseGUI {

    private final Player player;
    private final PetInstance pet;
    private final int returnPage;

    public PetDetailGUI(PetsPlugin plugin, Player player, PetInstance pet, int returnPage) {
        super(plugin, 3, "Pet Details");
        this.player = player;
        this.pet = pet;
        this.returnPage = returnPage;
        initializeItems();
    }

    private void initializeItems() {
        fillBackground(Material.BLACK_STAINED_GLASS_PANE);

        PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
        if (type == null) return;

        // Center: Pet icon with full details
        ItemStack petItem = new ItemStack(type.getIcon());
        ItemMeta meta = petItem.getItemMeta();
        meta.displayName(Component.text(pet.getDisplayName(type))
                .color(type.getRarity().getColor())
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(type.getDescription()).color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.empty());

        // Rarity
        lore.add(Component.text("Rarity: ").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(type.getRarity().name()).color(type.getRarity().getColor())));

        // Level
        int maxLevel = plugin.getConfig().getInt("leveling.max_level", 10);
        lore.add(Component.text("Level: ").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(pet.getLevel() + "/" + maxLevel).color(NamedTextColor.YELLOW)));

        // XP
        if (pet.getLevel() < maxLevel) {
            double nextXp = plugin.getPetManager().getXpForLevel(pet.getLevel() + 1);
            lore.add(Component.text("XP: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(String.format("%.0f/%.0f", pet.getXp(), nextXp))
                            .color(NamedTextColor.AQUA)));
        }

        lore.add(Component.empty());

        // Player attribute bonus
        lore.add(Component.text("─── Bonus ───").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" " + type.getAttributeDisplay() + ": ").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("+" + String.format("%.2f", type.getAttributeAtLevel(pet.getLevel())))
                        .color(NamedTextColor.GREEN)));

        lore.add(Component.empty());

        // Status
        lore.add(Component.text("─── Status ───").color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" " + pet.getStatus().getDisplay())
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));

        lore.add(Component.empty());

        // Obtained date
        String date = new SimpleDateFormat("MMM dd, yyyy").format(new Date(pet.getObtainedAt()));
        lore.add(Component.text("Obtained: " + date).color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));

        // Selection
        lore.add(Component.text("Selection: ").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(pet.isSelected()
                        ? Component.text("ACTIVE").color(NamedTextColor.GREEN)
                                .decoration(TextDecoration.BOLD, true)
                        : Component.text("Resting").color(NamedTextColor.DARK_GRAY)));

        meta.lore(lore);
        petItem.setItemMeta(meta);
        inventory.setItem(13, petItem);

        // Back button
        setBackButton(18);

        // Select/Deselect button
        ItemStack selectBtn = new ItemStack(pet.isSelected() ? Material.RED_DYE : Material.LIME_DYE);
        ItemMeta selectMeta = selectBtn.getItemMeta();
        selectMeta.displayName(Component.text(pet.isSelected() ? "Deselect" : "Select")
                .color(pet.isSelected() ? NamedTextColor.RED : NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        selectBtn.setItemMeta(selectMeta);
        inventory.setItem(22, selectBtn);

        // Delete button
        ItemStack deleteBtn = new ItemStack(Material.TNT);
        ItemMeta deleteMeta = deleteBtn.getItemMeta();
        deleteMeta.displayName(Component.text("Delete Pet").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        deleteMeta.lore(List.of(
                Component.text("Permanently delete this pet!").color(NamedTextColor.DARK_RED)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        deleteBtn.setItemMeta(deleteMeta);
        inventory.setItem(26, deleteBtn);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 18) {
            // Back to collection
            new PetCollectionGUI(plugin, player, returnPage).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        } else if (slot == 22) {
            // Toggle select
            if (pet.isSelected()) {
                plugin.getPetManager().deselectPet(player.getUniqueId());
            } else {
                plugin.getPetManager().selectPet(player.getUniqueId(), pet);
            }
            plugin.getPetManager().refreshCache(player.getUniqueId());
            new PetCollectionGUI(plugin, player, returnPage).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        } else if (slot == 26) {
            // Delete confirmation
            new DeleteConfirmGUI(plugin, player, pet, returnPage).open(player);
        }
    }
}
