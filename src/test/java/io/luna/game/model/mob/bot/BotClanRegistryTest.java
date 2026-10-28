package io.luna.game.model.mob.bot;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.luna.game.model.mob.bot.brain.BotPersonality;
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType;
import io.luna.game.model.mob.bot.brain.BotPkPreference;
import io.luna.game.persistence.PlayerData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies balanced offline enrollment, durable identities, capacity enforcement and failed-write isolation.
 *
 * @author lare96
 */
class BotClanRegistryTest {

    @TempDir
    Path directory;

    private Path file() {
        return directory.resolve("clans.json");
    }

    private PlayerData data(BotPkPreference.Purpose purpose) {
        PlayerData data = new PlayerData("fixture");
        data.personality = BotPersonality.DEFAULT;
        data.preferences = new JsonObject();
        data.preferences.add("pk", new BotPkPreference(purpose, BotPkPreference.Styles.MELEE,
                BotPkPreference.Build.MAIN).save());
        return data;
    }

    @Test
    void allPurposesEnrollAndOfflineMembersCountAfterRestart() {
        Map<String, PlayerData> saves = new HashMap<>();
        for (int i = 0; i < 137; i++) saves.put("bot " + i, data(BotPkPreference.Purpose.values()[i % 4]));
        BotClanRegistry registry = new BotClanRegistry(file());
        registry.load(saves.keySet(), saves::get);
        Map<Integer, Integer> counts = new HashMap<>();
        saves.keySet().forEach(name -> counts.merge(registry.capeFor(name), 1, Integer::sum));
        assertEquals(50, counts.size());
        assertTrue(counts.values().stream().allMatch(count -> count == 2 || count == 3));
        BotClanRegistry restored = new BotClanRegistry(file());
        restored.load(saves.keySet(), saves::get);
        saves.keySet().forEach(name -> assertEquals(registry.capeFor(name), restored.capeFor(name)));
        restored.update("new bot", true);
        assertEquals(2, counts.get(restored.capeFor("new bot")));
        int assigned = restored.capeFor("new bot");
        restored.update("NEW BOT", true);
        assertEquals(assigned, restored.capeFor("new bot"));
        restored.update("new_bot", true);
        assertNotNull(restored.capeFor("new_bot"));
        assertNotEquals(assigned, restored.capeFor("new_bot"));
    }

    @Test
    void fullRosterRejectsEnrollmentWithoutChangingFileOrExistingAssignments() throws Exception {
        List<String> names = IntStream.range(0, 5000).mapToObj(i -> "bot " + i).toList();
        PlayerData data = data(BotPkPreference.Purpose.GENERIC);
        BotClanRegistry registry = new BotClanRegistry(file());
        registry.load(names, ignored -> data);
        Map<Integer, Integer> counts = new HashMap<>();
        names.forEach(name -> counts.merge(registry.capeFor(name), 1, Integer::sum));
        assertEquals(50, counts.size());
        assertTrue(counts.values().stream().allMatch(count -> count == 100));
        String before = Files.readString(file());
        assertThrows(IllegalStateException.class, () -> registry.update("overflow", true));
        assertNull(registry.capeFor("overflow"));
        assertEquals(before, Files.readString(file()));
        int freed = registry.capeFor("bot 0");
        registry.update("bot 0", false);
        registry.update("replacement", true);
        assertEquals(freed, registry.capeFor("replacement"));
    }

    @Test
    void startupPrunesDeletedAndIneligibleMembersWithoutMovingRemainingMembers() {
        PlayerData data = data(BotPkPreference.Purpose.SKILL);
        BotClanRegistry registry = new BotClanRegistry(file());
        registry.load(List.of("retained", "deleted", "ineligible"), ignored -> data);
        int retainedCape = registry.capeFor("retained");
        PlayerData ineligible = data(BotPkPreference.Purpose.CLAN);
        ineligible.preferences.add("pk", JsonNull.INSTANCE);
        BotClanRegistry restored = new BotClanRegistry(file());
        restored.load(List.of("retained", "ineligible"), name -> name.equals("retained") ? data : ineligible);
        assertEquals(retainedCape, restored.capeFor("retained"));
        assertNull(restored.capeFor("deleted"));
        assertNull(restored.capeFor("ineligible"));
    }

    @Test
    void legacyEligibilityMatchesPreferenceMigrationAndSkillerAlwaysWins() {
        PlayerData legacy = data(BotPkPreference.Purpose.VALUE);
        legacy.preferences.remove("pk");
        JsonArray activities = new JsonArray();
        JsonObject activity = new JsonObject();
        activity.addProperty("type", "PKING");
        activity.addProperty("weight", 0.61);
        activities.add(activity);
        legacy.preferences.add("activities", activities);
        assertTrue(BotClanRegistry.isEligible(legacy));
        activity.addProperty("weight", 0.60);
        assertFalse(BotClanRegistry.isEligible(legacy));
        legacy.personality = mock(BotPersonality.class);
        when(legacy.personality.getType()).thenReturn(PersonalityTemplateType.PKER);
        assertTrue(BotClanRegistry.isEligible(legacy));
        legacy.preferences.add("pk", JsonNull.INSTANCE);
        assertFalse(BotClanRegistry.isEligible(legacy));
        legacy.preferences.add("pk", data(BotPkPreference.Purpose.CLAN).preferences.get("pk"));
        when(legacy.personality.getType()).thenReturn(PersonalityTemplateType.SKILLER);
        assertFalse(BotClanRegistry.isEligible(legacy));
    }

    @Test
    void legacyCapeFieldsDoNotOverrideExternalAssignmentsOrChangePurpose() {
        JsonObject pk = data(BotPkPreference.Purpose.VALUE).preferences.getAsJsonObject("pk");
        pk.addProperty("teamCape", 4413);
        BotPkPreference loaded = BotPkPreference.load(pk);
        assertEquals(BotPkPreference.Purpose.VALUE, loaded.purpose());
        assertFalse(loaded.save().has("teamCape"));
    }

    @Test
    void invalidExternalAssignmentsCannotBypassCapeOrCapacityValidation() throws Exception {
        PlayerData data = data(BotPkPreference.Purpose.CLAN);
        for (String value : List.of("4316", "4315.5", "9999")) {
            String json = "{\"version\":1,\"members\":{\"member\":" + value + "}}";
            Files.writeString(file(), json);
            BotClanRegistry registry = new BotClanRegistry(file());
            assertThrows(RuntimeException.class, () -> registry.load(List.of("member"), ignored -> data));
            assertEquals(json, Files.readString(file()));
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject members = new JsonObject();
        List<String> names = IntStream.range(0, 101).mapToObj(i -> "bot " + i).toList();
        names.forEach(name -> members.addProperty(name, 4315));
        root.add("members", members);
        Files.writeString(file(), root.toString());
        BotClanRegistry registry = new BotClanRegistry(file());
        assertThrows(IllegalStateException.class, () -> registry.load(names, ignored -> data));
        assertEquals(root.toString(), Files.readString(file()));
    }

    @Test
    void corruptRosterIsNotOverwrittenAndFailedWritesDoNotPublish() throws Exception {
        Files.writeString(file(), "broken json");
        BotClanRegistry registry = new BotClanRegistry(file());
        assertThrows(RuntimeException.class, () -> registry.load(List.of(), ignored -> null));
        assertEquals("broken json", Files.readString(file()));
        Files.delete(file());
        registry.load(List.of(), ignored -> null);
        Files.delete(file());
        Files.createDirectory(file());
        Files.writeString(file().resolve("blocker"), "prevents replacement");
        assertThrows(RuntimeException.class, () -> registry.update("uncommitted", true));
        assertNull(registry.capeFor("uncommitted"));
    }
}
