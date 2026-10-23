package io.luna.game.model.item.economy;

import engine.bot.coordinator.skill.ScriptFixtures;
import io.luna.Luna;
import io.luna.LunaSettings;
import io.luna.game.model.World;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.item.Item;
import io.luna.util.GsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ItemPriceFloorTest {
    // Shared ScriptFixtures skips noted definitions. Reserve unused slots for local test definitions.
    private static final int STACK = 7953;
    private static final int ORDINARY = 7955;
    private static final int NOTE = 7949;
    private MockedStatic<Luna> luna;
    private WorldEconomySettings economy;

    @BeforeAll static void definitions() {
        ScriptFixtures.INSTANCE.initialize();
        definition(STACK, true, OptionalInt.empty());
        definition(ORDINARY, false, OptionalInt.empty());
        definition(NOTE, true, OptionalInt.of(ORDINARY));
    }

    private static void definition(int id, boolean stackable, OptionalInt unnoted) {
        if (ItemDefinition.ALL.get(id).isPresent()) return;
        ItemDefinition def = mock(ItemDefinition.class);
        when(def.id()).thenReturn(id);
        when(def.isStackable()).thenReturn(stackable);
        when(def.isTradeable()).thenReturn(true);
        when(def.isNoted()).thenReturn(unnoted.isPresent());
        when(def.getUnnotedId()).thenReturn(unnoted);
        ItemDefinition.ALL.storeDefinition(def);
    }

    @BeforeEach void settings() {
        LunaSettings settings = mock(LunaSettings.class);
        economy = mock(WorldEconomySettings.class);
        when(settings.economy()).thenReturn(economy);
        when(economy.minimumStackablePrice()).thenReturn(5);
        when(economy.minimumPrice()).thenReturn(25);
        when(economy.priceMovementResistance()).thenReturn(0.60);
        when(economy.maximumPriceMovement()).thenReturn(0.25);
        when(economy.maximumOverpayRatio()).thenReturn(1.0);
        when(economy.maximumUnderpayRatio()).thenReturn(0.55);
        luna = mockStatic(Luna.class, CALLS_REAL_METHODS);
        luna.when(Luna::settings).thenReturn(settings);
    }

    @AfterEach void restoreSettings() { luna.close(); }

    @ParameterizedTest @EnumSource(SampleType.class)
    void floorPreservesPreviousPriceAndEffectiveSamples(SampleType type) {
        ItemPriceData before = new ItemPriceData(STACK, 5, 6, 100, 90);
        ItemPriceData after = before.setCalculatedPrice(4.99, type, new Item(STACK, 10000));
        long additional = (long) Math.floor(100 * type.getConfidence());
        assertEquals(5, after.price);
        assertEquals(5, after.lastPrice);
        assertEquals(100 + additional, after.samples);
        assertEquals(100, after.lastSamples);
    }

    @Test void notedSamplesUseTheNormalizedItemsNonStackableFloor() {
        World world = mock(World.class, RETURNS_DEEP_STUBS);
        WorldEconomy service = new WorldEconomy(world);
        ItemPriceData before = service.resolvePriceData(NOTE);
        assertEquals(ORDINARY, before.id);
        assertEquals(25, before.price);
        assertEquals(25, before.setCalculatedPrice(24, SampleType.ITEM_FOR_COINS, new Item(NOTE, 100)).price);
    }

    @Test void configuredFloorsAreUsedRatherThanConstants() {
        when(economy.minimumStackablePrice()).thenReturn(7);
        when(economy.minimumPrice()).thenReturn(40);
        assertEquals(7, new ItemPriceData(STACK).setCalculatedPrice(6, SampleType.ITEM_FOR_COINS, new Item(STACK, 100)).price);
        assertEquals(40, new ItemPriceData(ORDINARY).setCalculatedPrice(39, SampleType.ITEM_FOR_COINS, new Item(ORDINARY, 2)).price);
    }

    @Test void ordinaryMovementAndSubsequentUpwardMovementArePreserved() {
        assertEquals(101.25, new ItemPriceData(STACK, 100, 100, 0, 0)
                .setCalculatedPrice(105, SampleType.ITEM_FOR_COINS, new Item(STACK, 16)).price);
        ItemPriceData floor = new ItemPriceData(STACK).setCalculatedPrice(4, SampleType.ITEM_FOR_COINS, new Item(STACK, 100));
        assertTrue(floor.setCalculatedPrice(10, SampleType.ITEM_FOR_COINS, new Item(STACK, 10000)).price > 5);
    }

    @Test void InsufficientEffectiveSamplesDoNotReplaceTheSnapshot() {
        ItemPriceData before = new ItemPriceData(STACK);
        assertSame(before, before.setCalculatedPrice(4, SampleType.BARTERING, new Item(STACK)));
    }

    @Test void invalidCalculatedValuesAndDirectAssignmentsStillFail() {
        ItemPriceData before = new ItemPriceData(STACK);
        for (double value : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalStateException.class,
                    () -> before.setCalculatedPrice(value, SampleType.ITEM_FOR_COINS, new Item(STACK, 100)));
        }
        assertThrows(IllegalStateException.class, () -> before.setPrice(4, 1));
        assertThrows(IllegalStateException.class, () -> before.setPrice(Integer.MAX_VALUE + 1.0, 1));
    }

    @Test void belowFloorBasketDoesNotPreventLaterIterationPersistence() throws Exception {
        World world = mock(World.class, RETURNS_DEEP_STUBS);
        when(world.getSerializerManager().isJson()).thenReturn(true);
        WorldEconomy service = new WorldEconomy(world);
        List<Map<Integer, ItemPriceData>> persisted = new ArrayList<>();
        // Exercise real queue draining and savePrices, but intercept disk I/O so no live price file is touched.
        try (MockedStatic<GsonUtils> gson = mockStatic(GsonUtils.class, CALLS_REAL_METHODS);
             MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            gson.when(() -> GsonUtils.writeJson(any(), any(Path.class))).thenAnswer(call -> {
                Map<Integer, ItemPriceData> snapshot = call.getArgument(0);
                persisted.add(new HashMap<>(snapshot));
                return null;
            });
            files.when(() -> Files.move(any(Path.class), any(Path.class), any(CopyOption[].class)))
                    .thenAnswer(call -> call.getArgument(1));
            service.addTrade(trade(List.of(new Item(STACK, 100), new Item(ORDINARY, 2)), 549));
            service.runOneIteration();
            assertEquals(5, persisted.getFirst().get(STACK).price);
            service.addTrade(trade(List.of(new Item(STACK, 100)), 600));
            service.runOneIteration();
            assertEquals(2, persisted.size());
            assertTrue(persisted.getLast().get(STACK).price > 5);
            assertTrue(persisted.getLast().get(STACK).samples > persisted.getFirst().get(STACK).samples);
        }
    }

    private CompletedTradeData trade(List<Item> items, int coins) {
        CompletedTradeData trade = mock(CompletedTradeData.class);
        when(trade.getSideA()).thenReturn(items);
        when(trade.getSideB()).thenReturn(List.of(new Item(995, coins)));
        return trade;
    }
}
