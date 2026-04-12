package com.petsplugin.gui;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetFollowMode;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class PetSettingsGUI extends BaseGUI {

    private final Player player;
    private final int returnPage;
    private final PetCollectionGUI.FilterMode returnFilterMode;
    private final Set<Rarity> returnRarityFilters;

    public PetSettingsGUI(PetsPlugin plugin, Player player, int returnPage) {
        this(plugin, player, returnPage, PetCollectionGUI.FilterMode.ALL, EnumSet.noneOf(Rarity.class));
    }

    public PetSettingsGUI(PetsPlugin plugin, Player player, int returnPage,
                          PetCollectionGUI.FilterMode returnFilterMode,
                          Set<Rarity> returnRarityFilters) {
        super(plugin, 3, "Pet Settings");
        this.player = player;
        this.returnPage = returnPage;
        this.returnFilterMode = returnFilterMode == null ? PetCollectionGUI.FilterMode.ALL : returnFilterMode;
        this.returnRarityFilters = EnumSet.noneOf(Rarity.class);
        if (returnRarityFilters != null) {
            this.returnRarityFilters.addAll(returnRarityFilters);
        }
        initializeItems();
    }

    private void initializeItems() {
        fillBackground(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        fillBottomBar();

        inventory.setItem(10, createFollowModeItem(player.getUniqueId(), true));
        inventory.setItem(12, createHideOtherPetsItem());
        inventory.setItem(14, createPetSoundsItem());
        inventory.setItem(16, createPetNotificationsItem());
        inventory.setItem(22, createBackBarrierItem());
    }

    private ItemStack createBackBarrierItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Back")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text("Return to your pet collection.")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
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

    private ItemStack createPetSoundsItem() {
        boolean enabled = plugin.getSettingsManager().isPetSoundsEnabled(player.getUniqueId());
        Material material = enabled ? Material.NOTE_BLOCK : Material.JUKEBOX;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Pet Sounds: " + (enabled ? "ON" : "OFF"))
                .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Controls ambient sounds from your own pet.").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Command: /pets sounds [on|off|toggle]").color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to toggle.").color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPetNotificationsItem() {
        boolean enabled = plugin.getSettingsManager().isPetNotificationsEnabled(player.getUniqueId());
        Material material = enabled ? Material.PAPER : Material.BOOK;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Pet Notifications: " + (enabled ? "ON" : "OFF"))
                .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Controls pet event chat messages.").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Spawn, level-up, feed, hatch, and similar notices.").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Command: /pets notifications [on|off|toggle]").color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to toggle.").color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 22) {
            new PetCollectionGUI(plugin, player, returnPage, returnFilterMode, returnRarityFilters).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 10) {
            PetFollowMode current = plugin.getSettingsManager().getFollowMode(player.getUniqueId());
            PetFollowMode next = current == PetFollowMode.FOLLOW ? PetFollowMode.STAY : PetFollowMode.FOLLOW;
            plugin.getPetManager().setFollowMode(player, next);
            initializeItems();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 12) {
            boolean current = plugin.getSettingsManager().isHideOtherPetsEnabled(player.getUniqueId());
            plugin.getPetManager().setHideOtherPets(player, !current);
            initializeItems();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 14) {
            boolean current = plugin.getSettingsManager().isPetSoundsEnabled(player.getUniqueId());
            plugin.getPetManager().setPetSoundsEnabled(player, !current);
            initializeItems();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 16) {
            boolean current = plugin.getSettingsManager().isPetNotificationsEnabled(player.getUniqueId());
            plugin.getPetManager().setPetNotificationsEnabled(player, !current);
            initializeItems();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        }
    }
}
