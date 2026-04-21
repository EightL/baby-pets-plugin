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
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Confirmation GUI for pet deletion.
 * Shows the pet info and requires clicking CONFIRM to proceed.
 */
public class DeleteConfirmGUI extends BaseGUI {

    private final Player player;
    private final PetInstance pet;
    private final int returnPage;
    private final PetCollectionGUI.FilterMode returnFilterMode;
    private final Set<Rarity> returnRarityFilters;

    public DeleteConfirmGUI(PetsPlugin plugin, Player player, PetInstance pet, int returnPage,
                            PetCollectionGUI.FilterMode returnFilterMode,
                            Set<Rarity> returnRarityFilters) {
        super(plugin, 3, localizedTitle(plugin, "deleteconfirmgui.title", "Delete Pet?"));
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
        fillBackground(Material.RED_STAINED_GLASS_PANE);
        fillBottomBar();

        PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
        if (type == null) return;

        // Center: The pet being deleted
        ItemStack petItem = new ItemStack(type.getIcon());
        ItemMeta meta = petItem.getItemMeta();
        meta.displayName(Component.text("Delete: " + pet.getDisplayName(type))
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Level " + pet.getLevel() + " "
                        + plugin.getPetManager().getLocalizedRarity(type.getRarity()))
                        .color(type.getRarity().getColor())
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                plugin.getLanguageManager().getMessage("deleteconfirmgui.this_action_is_permanent", "This action is PERMANENT!").color(NamedTextColor.DARK_RED)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true)
        ));
        petItem.setItemMeta(meta);
        inventory.setItem(13, petItem);

        // Confirm button (slot 11)
        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.displayName(plugin.getLanguageManager().getMessage("deleteconfirmgui.confirm_delete", "CONFIRM DELETE").color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        confirmMeta.lore(List.of(
                plugin.getLanguageManager().getMessage("deleteconfirmgui.click_to_permanently_delete", "Click to permanently delete").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                plugin.getLanguageManager().getMessage("deleteconfirmgui.this_pet", "this pet.").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        confirm.setItemMeta(confirmMeta);
        inventory.setItem(11, confirm);

        // Cancel button (slot 15)
        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(plugin.getLanguageManager().getMessage("deleteconfirmgui.cancel", "CANCEL").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        cancelMeta.lore(List.of(
                plugin.getLanguageManager().getMessage("deleteconfirmgui.go_back_to_collection", "Go back to collection.").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        cancel.setItemMeta(cancelMeta);
        inventory.setItem(15, cancel);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 11) {
            // Confirm delete
            plugin.getPetManager().deletePet(player.getUniqueId(), pet);
            plugin.getPetManager().refreshCache(player.getUniqueId());

            PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
            String name = type != null ? pet.getDisplayName(type) : pet.getPetTypeId();
            player.sendMessage(plugin.getLanguageManager().getMessage("deleteconfirmgui.deleted", "Deleted ").color(NamedTextColor.RED)
                    .append(Component.text(name).color(NamedTextColor.YELLOW))
                    .append(plugin.getLanguageManager().getMessage("deleteconfirmgui.permanently", " permanently.").color(NamedTextColor.RED)));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);

            new PetCollectionGUI(plugin, player, returnPage, returnFilterMode, returnRarityFilters).open(player);
        } else if (slot == 15) {
            // Cancel — go back
            new PetCollectionGUI(plugin, player, returnPage, returnFilterMode, returnRarityFilters).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        }
    }
}
