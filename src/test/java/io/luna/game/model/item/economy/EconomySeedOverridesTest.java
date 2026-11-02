package io.luna.game.model.item.economy;

import engine.bot.coordinator.skill.ScriptFixtures;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks durable seed edits and separation from already observed market prices.
 *
 * @author lare96
 */
class EconomySeedOverridesTest {
    @TempDir Path directory;

    @BeforeEach void initialize() throws Exception {
        ScriptFixtures.INSTANCE.initialize();
        EconomySeedOverrides.load(directory.resolve("absent.json"));
    }

    @AfterEach void reset() throws Exception {
        EconomySeedOverrides.load(directory.resolve("absent.json"));
    }

    @Test void seedPersistsWithoutMutatingExistingMarketSnapshot() throws Exception {
        ItemPriceData observed = new ItemPriceData(436).setPrice(5000, 4);
        Path file = directory.resolve("seeds.json");
        EconomySeedOverrides.setAndSave(file, 436, 1500);
        EconomySeedOverrides.setAndSave(file, 438, 1600);
        EconomySeedOverrides.load(directory.resolve("absent.json"));
        EconomySeedOverrides.load(file);
        assertEquals(1500, ItemPriceData.computeInitialPrice(436));
        assertEquals(1600, ItemPriceData.computeInitialPrice(438));
        assertEquals(5000, observed.price);
        assertEquals(4, observed.samples);
        assertFalse(Files.exists(directory.resolve("seeds.json.tmp")));
    }

    @Test void invalidEditDoesNotReplaceSavedSeeds() throws Exception {
        Path file = directory.resolve("seeds.json");
        EconomySeedOverrides.setAndSave(file, 436, 1500);
        String before = Files.readString(file);
        assertThrows(IllegalArgumentException.class, () -> EconomySeedOverrides.setAndSave(file, 995, 10));
        assertThrows(IllegalStateException.class, () -> EconomySeedOverrides.setAndSave(file, 436, -1));
        assertEquals(before, Files.readString(file));
        assertEquals(1500, EconomySeedOverrides.get(436));
    }
}
