package com.petsplugin.gui;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetFollowMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Base GUI class — same pattern as fish-rework's BaseGUI.
 */
public abstract class BaseGUI implements InventoryHolder {

    protected final PetsPlugin plugin;
    protected final Inventory inventory;

    /** Helper: resolve a GUI title via LanguageManager before passing to super(). */
    protected static String localizedTitle(PetsPlugin plugin, String key, String fallback) {
        return plugin.getLanguageManager().getString(key, fallback);
    }

    public BaseGUI(PetsPlugin plugin, int rows, String title) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, rows * 9, Component.text(title));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public abstract void onClick(InventoryClickEvent event);

    public void onClose(InventoryCloseEvent event) { }

    public void onDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    public boolean handlesPlayerInventoryClicks() {
        return false;
    }

    // Utility methods

    protected void fillBackground(Material material) {
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" "));
        glass.setItemMeta(meta);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, glass);
            }
        }
    }

    protected ItemStack createFillerPane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    protected void fillBottomBar() {
        fillBottomBar(Material.BLACK_STAINED_GLASS_PANE);
    }

    protected void fillBottomBar(Material material) {
        ItemStack pane = createFillerPane(material);
        int startSlot = Math.max(0, inventory.getSize() - 9);
        for (int slot = startSlot; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane);
        }
    }

    protected void setBackButton(int slot) {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage("basegui.back", "Back").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(meta);
        inventory.setItem(slot, back);
    }

        protected ItemStack createFollowModeItem(UUID playerUuid, boolean includeCommandHint) {
        PetFollowMode mode = plugin.getSettingsManager().getFollowMode(playerUuid);
        boolean follow = mode == PetFollowMode.FOLLOW;

        ItemStack item = new ItemStack(follow ? Material.LEAD : Material.BELL);
        ItemMeta meta = item.getItemMeta();
        String modeOnLabel = plugin.getLanguageManager().getString("ui.labels.on", "ON");
        String modeOffLabel = plugin.getLanguageManager().getString("ui.labels.off", "OFF");
        String modeFollowLabel = plugin.getLanguageManager().getString("pet.mode.FOLLOW", "Follow");
        String modeStayLabel = plugin.getLanguageManager().getString("pet.mode.STAY", "Stay");
        String title = includeCommandHint
            ? plugin.getLanguageManager().getString(
                    "basegui.follow_mode",
                    "Follow Mode: %state%",
                    Map.of("state", follow ? modeOnLabel : modeOffLabel))
            : plugin.getLanguageManager().getString(
                    follow ? "basegui.mode_follow" : "basegui.mode_stay",
                    "Mode: %mode%",
                    Map.of("mode", follow ? modeFollowLabel : modeStayLabel));
        meta.displayName(Component.text(title)
            .color(follow ? NamedTextColor.GREEN : NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(plugin.getLanguageManager().getMessage("basegui.follow_keeps_your_active_pet", "Follow keeps your active pet near you.").color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(plugin.getLanguageManager().getMessage("basegui.stay_keeps_your_pet_in", "Stay keeps your pet in place.").color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        if (includeCommandHint) {
            lore.add(plugin.getLanguageManager().getMessage("basegui.commands_pets_follow_pets_stay", "Commands: /pets follow, /pets stay").color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(plugin.getLanguageManager().getMessage("basegui.click_to_toggle", "Click to toggle.").color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
        }
}
