package com.petsplugin.manager;

import com.petsplugin.PetsPlugin;
import com.petsplugin.model.PetInstance;
import com.petsplugin.model.PetType;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PetAdvancementManager {

    private final PetsPlugin plugin;

    public final NamespacedKey ROOT_KEY;
    public final NamespacedKey HOME_INCUBATION_KEY;
    public final NamespacedKey A_LITTLE_FRIEND_KEY;
    public final NamespacedKey OFFICIALLY_ADOPTED_KEY;
    public final NamespacedKey SNACK_TIME_KEY;
    public final NamespacedKey HEADPATS_KEY;
    public final NamespacedKey GROWING_TOGETHER_KEY;
    public final NamespacedKey BEST_FRIENDS_FOREVER_KEY;
    public final NamespacedKey SOMETHING_SPECIAL_KEY;
    public final NamespacedKey TINY_LEGEND_KEY;
    public final NamespacedKey MINI_MENAGERIE_KEY;
    public final NamespacedKey PET_PARENT_KEY;

    public PetAdvancementManager(PetsPlugin plugin) {
        this.plugin = plugin;
        this.ROOT_KEY = new NamespacedKey(plugin, "pets/root");
        this.HOME_INCUBATION_KEY = new NamespacedKey(plugin, "pets/home_incubation");
        this.A_LITTLE_FRIEND_KEY = new NamespacedKey(plugin, "pets/a_little_friend");
        this.OFFICIALLY_ADOPTED_KEY = new NamespacedKey(plugin, "pets/officially_adopted");
        this.SNACK_TIME_KEY = new NamespacedKey(plugin, "pets/snack_time");
        this.HEADPATS_KEY = new NamespacedKey(plugin, "pets/headpats");
        this.GROWING_TOGETHER_KEY = new NamespacedKey(plugin, "pets/growing_together");
        this.BEST_FRIENDS_FOREVER_KEY = new NamespacedKey(plugin, "pets/best_friends_forever");
        this.SOMETHING_SPECIAL_KEY = new NamespacedKey(plugin, "pets/something_special");
        this.TINY_LEGEND_KEY = new NamespacedKey(plugin, "pets/tiny_legend");
        this.MINI_MENAGERIE_KEY = new NamespacedKey(plugin, "pets/mini_menagerie");
        this.PET_PARENT_KEY = new NamespacedKey(plugin, "pets/pet_parent");
    }

    public void loadAdvancements() {
        if (!areAdvancementsEnabled()) return;

        unloadAdvancements();

        safeLoad(ROOT_KEY, buildRootJson());
        safeLoad(HOME_INCUBATION_KEY, buildAdvancementJson(ROOT_KEY, "minecraft:smoker", "Home Incubation",
                "Place a pet egg into an incubator.", "task", true, true));
        safeLoad(A_LITTLE_FRIEND_KEY, buildAdvancementJson(HOME_INCUBATION_KEY, "minecraft:egg", "A Little Friend",
                "Hatch your first baby pet.", "task", true, true));
        safeLoad(OFFICIALLY_ADOPTED_KEY, buildAdvancementJson(A_LITTLE_FRIEND_KEY, "minecraft:name_tag", "Officially Adopted",
                "Name one of your pets with a name tag.", "task", true, true));
        safeLoad(SNACK_TIME_KEY, buildAdvancementJson(A_LITTLE_FRIEND_KEY, "minecraft:bread", "Snack Time",
                "Feed one of your pets a treat.", "task", true, true));
        safeLoad(HEADPATS_KEY, buildAdvancementJson(A_LITTLE_FRIEND_KEY, "minecraft:feather", "Headpats",
                "Give your pet some affection.", "task", true, true));
        safeLoad(GROWING_TOGETHER_KEY, buildAdvancementJson(A_LITTLE_FRIEND_KEY, "minecraft:experience_bottle", "Growing Together",
                "Raise a pet to level 5.", "task", true, true));
        safeLoad(BEST_FRIENDS_FOREVER_KEY, buildAdvancementJson(GROWING_TOGETHER_KEY, "minecraft:totem_of_undying", "Best Friends Forever",
                "Raise a pet to max level.", "challenge", true, true));
        safeLoad(SOMETHING_SPECIAL_KEY, buildAdvancementJson(A_LITTLE_FRIEND_KEY, "minecraft:lapis_lazuli", "Something Special",
                "Hatch a rare pet or better.", "task", true, true));
        safeLoad(TINY_LEGEND_KEY, buildAdvancementJson(SOMETHING_SPECIAL_KEY, "minecraft:nether_star", "Tiny Legend",
                "Hatch a legendary pet.", "challenge", true, true));
        safeLoad(MINI_MENAGERIE_KEY, buildAdvancementJson(A_LITTLE_FRIEND_KEY, "minecraft:lead", "Mini Menagerie",
                "Collect every type of pet.", "challenge", true, true));
        safeLoad(PET_PARENT_KEY, buildAdvancementJson(MINI_MENAGERIE_KEY, "minecraft:golden_apple", "Pet Parent",
                "Complete the entire baby pets advancement tree.", "challenge", true, true));
    }

    public void unloadAdvancements() {
        safeRemove(PET_PARENT_KEY);
        safeRemove(MINI_MENAGERIE_KEY);
        safeRemove(TINY_LEGEND_KEY);
        safeRemove(SOMETHING_SPECIAL_KEY);
        safeRemove(BEST_FRIENDS_FOREVER_KEY);
        safeRemove(GROWING_TOGETHER_KEY);
        safeRemove(HEADPATS_KEY);
        safeRemove(SNACK_TIME_KEY);
        safeRemove(OFFICIALLY_ADOPTED_KEY);
        safeRemove(A_LITTLE_FRIEND_KEY);
        safeRemove(HOME_INCUBATION_KEY);
        safeRemove(ROOT_KEY);
    }

    public void syncPlayerAdvancements(Player player) {
        if (!areAdvancementsEnabled()) return;

        grantAdvancement(player, ROOT_KEY);

        List<PetInstance> pets = plugin.getPetManager().getPlayerPets(player.getUniqueId());
        if (!pets.isEmpty()) {
            grantAdvancement(player, HOME_INCUBATION_KEY);
            grantAdvancement(player, A_LITTLE_FRIEND_KEY);
        }

        boolean hasNamedPet = false;
        boolean hasLevel5 = false;
        boolean hasLevel10 = false;
        boolean hasRare = false;
        boolean hasLegendary = false;

        for (PetInstance pet : pets) {
            if (pet.getNickname() != null && !pet.getNickname().isBlank()) {
                hasNamedPet = true;
            }
            if (pet.getLevel() >= 5) {
                hasLevel5 = true;
            }
            if (pet.getLevel() >= plugin.getConfig().getInt("leveling.max_level", 10)) {
                hasLevel10 = true;
            }

            PetType type = plugin.getPetTypes().get(pet.getPetTypeId());
            if (type == null) continue;

            if (type.getRarity().ordinal() >= com.petsplugin.model.Rarity.RARE.ordinal()) {
                hasRare = true;
            }
            if (type.getRarity() == com.petsplugin.model.Rarity.LEGENDARY) {
                hasLegendary = true;
            }
        }

        if (hasNamedPet) grantAdvancement(player, OFFICIALLY_ADOPTED_KEY);
        if (hasLevel5) grantAdvancement(player, GROWING_TOGETHER_KEY);
        if (hasLevel10) grantAdvancement(player, BEST_FRIENDS_FOREVER_KEY);
        if (hasRare) grantAdvancement(player, SOMETHING_SPECIAL_KEY);
        if (hasLegendary) grantAdvancement(player, TINY_LEGEND_KEY);
        if (ownsAllPetTypes(pets)) grantAdvancement(player, MINI_MENAGERIE_KEY);

        checkCompletionist(player);
    }

    public void handleEggPlaced(Player player) {
        if (!areAdvancementsEnabled()) return;
        grantAdvancement(player, ROOT_KEY);
        grantAdvancement(player, HOME_INCUBATION_KEY);
        checkCompletionist(player);
    }

    public void handlePetHatched(Player player, PetInstance pet, PetType type) {
        if (!areAdvancementsEnabled()) return;

        grantAdvancement(player, ROOT_KEY);
        grantAdvancement(player, HOME_INCUBATION_KEY);
        grantAdvancement(player, A_LITTLE_FRIEND_KEY);

        if (type.getRarity().ordinal() >= com.petsplugin.model.Rarity.RARE.ordinal()) {
            grantAdvancement(player, SOMETHING_SPECIAL_KEY);
        }
        if (type.getRarity() == com.petsplugin.model.Rarity.LEGENDARY) {
            grantAdvancement(player, TINY_LEGEND_KEY);
        }

        syncPlayerAdvancements(player);
    }

    public void handlePetRenamed(Player player) {
        if (!areAdvancementsEnabled()) return;
        grantAdvancement(player, OFFICIALLY_ADOPTED_KEY);
        checkCompletionist(player);
    }

    public void handlePetFed(Player player) {
        if (!areAdvancementsEnabled()) return;
        grantAdvancement(player, SNACK_TIME_KEY);
        checkCompletionist(player);
    }

    public void handlePetPetted(Player player) {
        if (!areAdvancementsEnabled()) return;
        grantAdvancement(player, HEADPATS_KEY);
        checkCompletionist(player);
    }

    public void handlePetLevel(Player player, PetInstance pet) {
        if (!areAdvancementsEnabled()) return;

        if (pet.getLevel() >= 5) {
            grantAdvancement(player, GROWING_TOGETHER_KEY);
        }
        if (pet.getLevel() >= plugin.getConfig().getInt("leveling.max_level", 10)) {
            grantAdvancement(player, BEST_FRIENDS_FOREVER_KEY);
        }
        checkCompletionist(player);
    }

    public boolean hasAdvancement(Player player, NamespacedKey key) {
        if (!areAdvancementsEnabled()) return false;

        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) return false;
        return player.getAdvancementProgress(advancement).isDone();
    }

    public void grantAdvancement(Player player, NamespacedKey key) {
        if (!areAdvancementsEnabled()) return;

        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) {
            plugin.getLogger().warning("Advancement not found: " + key);
            return;
        }

        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        if (progress.isDone()) return;

        for (String criteria : progress.getRemainingCriteria()) {
            progress.awardCriteria(criteria);
        }
    }

    private void checkCompletionist(Player player) {
        List<NamespacedKey> required = List.of(
                ROOT_KEY,
                HOME_INCUBATION_KEY,
                A_LITTLE_FRIEND_KEY,
                OFFICIALLY_ADOPTED_KEY,
                SNACK_TIME_KEY,
                HEADPATS_KEY,
                GROWING_TOGETHER_KEY,
                BEST_FRIENDS_FOREVER_KEY,
                SOMETHING_SPECIAL_KEY,
                TINY_LEGEND_KEY,
                MINI_MENAGERIE_KEY
        );

        for (NamespacedKey key : required) {
            if (!hasAdvancement(player, key)) {
                return;
            }
        }

        grantAdvancement(player, PET_PARENT_KEY);
    }

    private boolean ownsAllPetTypes(List<PetInstance> pets) {
        Set<String> allTypes = plugin.getPetTypes().keySet();
        if (allTypes.isEmpty()) return false;

        Set<String> ownedTypes = new java.util.HashSet<>();
        for (PetInstance pet : pets) {
            ownedTypes.add(pet.getPetTypeId());
        }
        return ownedTypes.containsAll(allTypes);
    }

    private String buildRootJson() {
        return "{\n" +
                "  \"display\": {\n" +
                "    \"icon\": { \"id\": \"minecraft:name_tag\" },\n" +
                "    \"title\": \"Baby Pets\",\n" +
                "    \"description\": \"Raise adorable companions from egg to best friend.\",\n" +
                "    \"background\": \"minecraft:block/pink_wool\",\n" +
                "    \"frame\": \"task\",\n" +
                "    \"show_toast\": false,\n" +
                "    \"announce_to_chat\": false\n" +
                "  },\n" +
                "  \"criteria\": { \"impossible\": { \"trigger\": \"minecraft:impossible\" } }\n" +
                "}";
    }

    private String buildAdvancementJson(NamespacedKey parent, String iconId, String title,
                                        String description, String frame, boolean toast, boolean chat) {
        return "{\n" +
                "  \"parent\": \"" + parent + "\",\n" +
                "  \"display\": {\n" +
                "    \"icon\": { \"id\": \"" + iconId + "\" },\n" +
                "    \"title\": \"" + title + "\",\n" +
                "    \"description\": \"" + description + "\",\n" +
                "    \"frame\": \"" + frame + "\",\n" +
                "    \"show_toast\": " + toast + ",\n" +
                "    \"announce_to_chat\": " + chat + "\n" +
                "  },\n" +
                "  \"criteria\": { \"impossible\": { \"trigger\": \"minecraft:impossible\" } }\n" +
                "}";
    }

    private void safeLoad(NamespacedKey key, String json) {
        try {
            Bukkit.getUnsafe().loadAdvancement(key, json);
        } catch (Exception e) {
            plugin.getLogger().warning("Error loading advancement " + key + ": " + e.getMessage());
        }
    }

    private void safeRemove(NamespacedKey key) {
        try {
            Bukkit.getUnsafe().removeAdvancement(key);
        } catch (Exception ignored) {
        }
    }

    private boolean areAdvancementsEnabled() {
        return plugin.getConfig().getBoolean("advancements.enabled", true);
    }
}
