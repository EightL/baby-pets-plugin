package com.petsplugin.gui;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetFollowMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class PetSettingsGUI extends BaseGUI {

    private final Player player;
    private final int returnPage;

    public PetSettingsGUI(PetsPlugin plugin, Player player, int returnPage) {
        super(plugin, 3, "Pet Settings");
        this.player = player;
        this.returnPage = returnPage;
        initializeItems();
    }

    private void initializeItems() {
        fillBackground(Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        inventory.setItem(11, createHideOtherPetsItem());
        inventory.setItem(15, createCollectionShortcut());
        setBackButton(18);

        ItemStack info = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(Component.text("Naming").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text("Use a renamed name tag on your pet").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("to save a nickname.").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Its name appears when you point at it.").color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        info.setItemMeta(meta);
        inventory.setItem(13, info);
    }

    private ItemStack createHideOtherPetsItem() {
        boolean enabled = plugin.getSettingsManager().isHideOtherPetsEnabled(player.getUniqueId());
        Material material = enabled ? Material.ENDER_EYE : Material.ENDER_PEARL;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Hide Other Players' Pets: " + (enabled ? "ON" : "OFF"))
                .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Keeps nearby companion mobs less cluttered.").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Your own pet always stays visible.").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Command: /pets hideothers").color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to toggle.").color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCollectionShortcut() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Pet Collection")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text("Return to the main pets menu.").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 18) {
            new PetCollectionGUI(plugin, player, returnPage).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 11) {
            boolean current = plugin.getSettingsManager().isHideOtherPetsEnabled(player.getUniqueId());
            plugin.getPetManager().setHideOtherPets(player, !current);
            initializeItems();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 15) {
            new PetCollectionGUI(plugin, player, returnPage).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        }
    }
}
