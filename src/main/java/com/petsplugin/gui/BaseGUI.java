package com.petsplugin.gui;

import com.petsplugin.PetsPlugin;
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

/**
 * Base GUI class — same pattern as fish-rework's BaseGUI.
 */
public abstract class BaseGUI implements InventoryHolder {

    protected final PetsPlugin plugin;
    protected final Inventory inventory;

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

    protected void setCloseButton(int slot) {
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta meta = close.getItemMeta();
        meta.displayName(Component.text("Close").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(meta);
        inventory.setItem(slot, close);
    }

    protected void setBackButton(int slot) {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.displayName(Component.text("Back").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(meta);
        inventory.setItem(slot, back);
    }

    protected void setPaginationControls(int prevSlot, int nextSlot, int page, int totalPages) {
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta meta = prev.getItemMeta();
            meta.displayName(Component.text("Previous Page").color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            prev.setItemMeta(meta);
            inventory.setItem(prevSlot, prev);
        }
        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            meta.displayName(Component.text("Next Page").color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            next.setItemMeta(meta);
            inventory.setItem(nextSlot, next);
        }
    }
}
