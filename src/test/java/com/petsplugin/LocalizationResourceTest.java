package com.petsplugin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationResourceTest {

    @Test
    void configDoesNotContainTranslatableMessageSections() throws IOException {
        String config = Files.readString(Path.of("src/main/resources/config.yml"));

        assertFalse(config.contains("\nmessages:\n"), "config.yml should not keep translatable messages");
        assertFalse(config.contains("\nui:\n"), "config.yml should not keep translatable UI labels");
    }

    @Test
    void langFileContainsKeysForMigratedGuiStrings() throws IOException {
        String lang = Files.readString(Path.of("src/main/resources/lang_en.yml"));

        assertTrue(lang.contains("petcollectiongui.filter_prefix:"), "lang_en.yml should define filter label text");
        assertTrue(lang.contains("petcollectiongui.filter.all:"), "lang_en.yml should define localized filter modes");
        assertTrue(lang.contains("deleteconfirmgui.delete_prefix:"), "lang_en.yml should define delete summary prefix");
        assertTrue(lang.contains("petinfo.storage_bonus:"), "lang_en.yml should define pet info storage bonus text");
        assertTrue(lang.contains("incubatorlistener.remaining_time:"), "lang_en.yml should define incubation remaining time text");
        assertTrue(lang.contains("petmovement.ground:"), "lang_en.yml should define localized pet movement names");
        assertTrue(lang.contains("petdetailgui.abilities:"), "lang_en.yml should define ability panel titles");
        assertTrue(lang.contains("petdetailgui.vanity_mode:"), "lang_en.yml should define vanity panel titles");
        assertTrue(lang.contains("petdetailgui.storage_slots:"), "lang_en.yml should define storage slot text");
        assertTrue(lang.contains("petdetailgui.attribute_line:"), "lang_en.yml should define formatted attribute lines");
        assertTrue(lang.contains("petdetailgui.growth_line:"), "lang_en.yml should define growth formatting");
        assertTrue(lang.contains("petdetailgui.at_level_line:"), "lang_en.yml should define max-level stat formatting");
        assertTrue(lang.contains("petdetailgui.select:"), "lang_en.yml should define select button text");
        assertTrue(lang.contains("petdetailgui.deselect:"), "lang_en.yml should define deselect button text");
        assertTrue(lang.contains("petinfo.attribute_line:"), "lang_en.yml should define pet info attribute lines");
    }

    @Test
    void langFileContainsKeysForRemainingPetNotificationsAndLabels() throws IOException {
        String lang = Files.readString(Path.of("src/main/resources/lang_en.yml"));

        assertTrue(lang.contains("petmanager.pet_follow_enabled:"), "lang_en.yml should define follow mode notification text");
        assertTrue(lang.contains("petmanager.pet_follow_staying:"), "lang_en.yml should define stay mode notification text");
        assertTrue(lang.contains("petmanager.pet_returned_to_rest:"), "lang_en.yml should define despawn notification text");
        assertTrue(lang.contains("petmanager.pet_treat_status:"), "lang_en.yml should define feed status text");
        assertTrue(lang.contains("petmanager.pet_attention_status:"), "lang_en.yml should define petting status text");
        assertTrue(lang.contains("petcollectiongui.selected_notification:"), "lang_en.yml should define selection notification text");
        assertTrue(lang.contains("petcollectiongui.deselected_notification:"), "lang_en.yml should define deselection notification text");
        assertTrue(lang.contains("petdetailgui.growth_tracker:"), "lang_en.yml should define growth tracker title text");
        assertTrue(lang.contains("petdetailgui.obtained:"), "lang_en.yml should define obtained label text");
        assertTrue(lang.contains("petdetailgui.obtained_date_format:"), "lang_en.yml should define obtained date formatting");
        assertTrue(lang.contains("petdetailgui.species:"), "lang_en.yml should define species label text");
        assertTrue(lang.contains("basegui.follow_mode:"), "lang_en.yml should define follow mode item titles");
        assertTrue(lang.contains("basegui.mode_follow:"), "lang_en.yml should define follow mode state labels");
        assertTrue(lang.contains("basegui.mode_stay:"), "lang_en.yml should define stay mode state labels");
    }

    @Test
    void zhLangFileContainsKeysForPetDetailAndFollowModeLabels() throws IOException {
        String lang = Files.readString(Path.of("src/main/resources/lang_zh_CN.yml"));

        assertTrue(lang.contains("petdetailgui.growth_tracker:"), "lang_zh_CN.yml should define growth tracker title text");
        assertTrue(lang.contains("petdetailgui.obtained:"), "lang_zh_CN.yml should define obtained label text");
        assertTrue(lang.contains("petdetailgui.obtained_date_format:"), "lang_zh_CN.yml should define obtained date formatting");
        assertTrue(lang.contains("petdetailgui.species:"), "lang_zh_CN.yml should define species label text");
        assertTrue(lang.contains("basegui.follow_mode:"), "lang_zh_CN.yml should define follow mode item titles");
        assertTrue(lang.contains("basegui.mode_follow:"), "lang_zh_CN.yml should define follow mode state labels");
        assertTrue(lang.contains("basegui.mode_stay:"), "lang_zh_CN.yml should define stay mode state labels");
    }

    @Test
    void langFilesContainRuntimeNotificationKeys() throws IOException {
        String[] files = {
                "src/main/resources/lang_en.yml",
                "src/main/resources/lang_es.yml",
                "src/main/resources/lang_zh_CN.yml"
        };
        String[] keys = {
                "messages.egg_hatched:",
                "messages.egg_placed:",
                "messages.feed_cooldown:",
                "messages.hide_other_pets_disabled:",
                "messages.hide_other_pets_enabled:",
                "messages.incubator_busy:",
                "messages.pet_level_up:",
                "messages.pet_notifications_disabled:",
                "messages.pet_notifications_enabled:",
                "messages.pet_renamed:",
                "messages.pet_sounds_disabled:",
                "messages.pet_sounds_enabled:",
                "messages.pet_spawned:"
        };

        for (String file : files) {
            String lang = Files.readString(Path.of(file));
            for (String key : keys) {
                assertTrue(lang.contains(key), file + " should define " + key);
            }
        }
    }

    @Test
    void petSourcesDoNotContainHardcodedPlayerFacingNotificationLeaks() throws IOException {
        String petManager = Files.readString(Path.of("src/main/java/com/petsplugin/manager/PetManager.java"));
        String petCollectionGui = Files.readString(Path.of("src/main/java/com/petsplugin/gui/PetCollectionGUI.java"));
        String petDetailGui = Files.readString(Path.of("src/main/java/com/petsplugin/gui/PetDetailGUI.java"));

        assertFalse(petManager.contains("Your pet will follow you again."),
                "PetManager should load the follow notification from lang files");
        assertFalse(petManager.contains("Your pet is staying put."),
                "PetManager should load the stay notification from lang files");
        assertFalse(petManager.contains("has returned to rest."),
                "PetManager should load the despawn notification from lang files");
        assertFalse(petManager.contains("enjoyed the treat! Status:"),
                "PetManager should load the feed status notification from lang files");
        assertFalse(petManager.contains("loves the attention! Status:"),
                "PetManager should load the petting status notification from lang files");
        assertFalse(petCollectionGui.contains("You selected &e%pet_name%&a!"),
                "PetCollectionGUI should load the selected notification from lang files");
        assertFalse(petCollectionGui.contains("You deselected &e%pet_name%&7."),
                "PetCollectionGUI should load the deselected notification from lang files");
        assertFalse(petDetailGui.contains("new SimpleDateFormat(\"MMM dd, yyyy\")"),
                "Pet detail metadata should load its date format from lang files");
    }
}
