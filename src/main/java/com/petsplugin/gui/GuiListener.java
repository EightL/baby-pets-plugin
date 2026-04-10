package com.petsplugin.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof BaseGUI gui) {
            if (event.getClickedInventory() == null) {
                event.setCancelled(true);
                return;
            }

            boolean clickedTopInventory = event.getClickedInventory().equals(event.getView().getTopInventory());
            if (!clickedTopInventory && !gui.handlesPlayerInventoryClicks()) {
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);
            gui.onClick(event);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BaseGUI gui) {
            gui.onClose(event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BaseGUI gui) {
            if (!gui.handlesPlayerInventoryClicks()) {
                event.setCancelled(true);
                return;
            }
            gui.onDrag(event);
        }
    }
}
