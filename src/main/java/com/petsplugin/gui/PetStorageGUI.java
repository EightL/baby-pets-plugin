package com.petsplugin.gui;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetType;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Portable storage GUI for horse/donkey/mule/llama/camel/trader_llama pets.
 *
 * Storage is shared per player+group (horse/donkey/mule share one bag;
 * llama, camel, and trader_llama each have their own).
 *
 * Slots unlocked per level: ceil(maxSlots * level / maxLevel), min 1.
 * Locked slots show filler glass; unlocked slots are air (usable).
 *
 * No bottom bar — press Escape to close.
 *
 * Max-slot layouts (S=storage, G=filler glass):
 *   max 5  (horse, 1 row ): [G][G][S][S][S][S][S][G][G]
 *   max 9  (llama, 1 row ): [S×9]
 *   max 14 (camel, 2 rows): [G][S×7][G] × 2
 *   max 18 (trader, 2 rows): [S×9] × 2
 */
public class PetStorageGUI extends BaseGUI {

    private final UUID playerUuid;
    private final String storageGroup;
    private final Material storageGlass;
    /** Full ordered list of storage slot positions (all max slots). */
    private final int[] storageSlots;
    /** Which top-inventory slots are currently active (unlocked). */
    private final boolean[] isStorageSlot;

    public PetStorageGUI(PetsPlugin plugin, Player player, PetType type,
                         int petLevel, Map<Integer, ItemStack> savedItems) {
        super(plugin, computeRows(type.getStorageSize()), type.getLocalizedDisplayName(plugin.getLanguageManager()) + " " + localizedTitle(plugin, "petstoragegui.title_suffix", "Storage"));
        this.playerUuid = player.getUniqueId();
        this.storageGroup = type.getStorageGroup();
        this.storageGlass = type.getStorageGlass();
        this.storageSlots = computeStorageSlots(type.getStorageSize());

        int maxLevel = plugin.getMaxLevel();
        int activeCount = type.computeActiveStorageSlots(petLevel, maxLevel);

        this.isStorageSlot = new boolean[inventory.getSize()];
        for (int i = 0; i < activeCount && i < storageSlots.length; i++) {
            isStorageSlot[storageSlots[i]] = true;
        }

        buildLayout(type, activeCount, savedItems);
    }

    private void buildLayout(PetType type, int activeCount, Map<Integer, ItemStack> savedItems) {
        // 1. Fill everything with filler glass (covers locked slots + gaps)
        fillBackground(type.getStorageGlass());

        // 2. Clear only the active storage slots so they are usable (air)
        for (int i = 0; i < activeCount && i < storageSlots.length; i++) {
            inventory.setItem(storageSlots[i], null);
        }

        // 3. Place saved items (only into active slots; higher-index items stay safe in DB)
        for (Map.Entry<Integer, ItemStack> entry : savedItems.entrySet()) {
            int idx = entry.getKey();
            ItemStack saved = entry.getValue();
            if (idx >= 0 && idx < activeCount && idx < storageSlots.length && !isStoragePlaceholder(saved)) {
                inventory.setItem(storageSlots[idx], saved.clone());
            }
        }
    }

    private boolean isStoragePlaceholder(ItemStack item) {
        if (item == null || item.getType() != storageGlass) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }

        String plain = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        return plain.isBlank();
    }

    // ── Layout helpers ────────────────────────────────────────

    private static int computeRows(int maxStorageSize) {
        // Enough rows to hold all max slots; no extra bottom-bar row
        if (maxStorageSize <= 9) return 1;
        return 2;
    }

    private static int[] computeStorageSlots(int maxStorageSize) {
        return switch (maxStorageSize) {
            case 5  -> new int[]{2, 3, 4, 5, 6};                       // centred in 1 row
            case 9  -> new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
            case 14 -> new int[]{1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16}; // 7×2 with glass borders
            case 18 -> new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8,
                                 9, 10, 11, 12, 13, 14, 15, 16, 17};
            default -> new int[0];
        };
    }

    // ── Event handling ────────────────────────────────────────

    @Override
    public boolean handlesPlayerInventoryClicks() {
        return true;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        boolean isTopInv = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());

        if (!isTopInv) {
            // Player's own inventory — allow all clicks (shift-click fills active slots naturally)
            event.setCancelled(false);
            return;
        }

        int slot = event.getSlot();

        // Active storage slot: let Minecraft handle item movement
        if (slot >= 0 && slot < isStorageSlot.length && isStorageSlot[slot]) {
            event.setCancelled(false);
            return;
        }

        // Filler glass or locked slot: keep cancelled
    }

    @Override
    public void onDrag(InventoryDragEvent event) {
        // Allow drag only if every touched top-inventory slot is an active storage slot
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= inventory.getSize()) continue; // player-inventory side — fine
            if (rawSlot < 0 || rawSlot >= isStorageSlot.length || !isStorageSlot[rawSlot]) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        saveContents();
    }

    // ── Persistence ───────────────────────────────────────────

    private void saveContents() {
        Map<Integer, ItemStack> toSave = new HashMap<>();
        for (int i = 0; i < storageSlots.length; i++) {
            int topSlot = storageSlots[i];
            if (topSlot < 0 || topSlot >= isStorageSlot.length || !isStorageSlot[topSlot]) {
                continue;
            }

            ItemStack item = inventory.getItem(topSlot);
            if (item != null && !item.getType().isAir() && !isStoragePlaceholder(item)) {
                toSave.put(i, item.clone());
            }
        }
        plugin.getDatabaseManager().savePlayerStorage(playerUuid, storageGroup, toSave);
    }
}
