package io.luna.game.model.item.economy;

import engine.bot.coordinator.skill.ScriptFixtures;
import io.luna.game.model.World;
import java.sql.Connection;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies service-thread edits, sample preservation and persistence acknowledgement.
 *
 * @author lare96
 */
class DeveloperGuidePriceTest {
    private World world;
    private WorldEconomy economy;

    @BeforeEach void setup() {
        ScriptFixtures.INSTANCE.initialize();
        world = mock(World.class, RETURNS_DEEP_STUBS);
        economy = spy(new WorldEconomy(world));
        doReturn(true).when(economy).isRunning();
    }

    @Test void editWaitsForServiceAndPersistsWithoutAddingSamples() throws Exception {
        var before = economy.resolvePriceData(436);
        var result = economy.setGuidePrice(436, 2500);
        assertFalse(result.isDone());
        assertSame(before, economy.getPrices().get(436));
        try (var history = mockConstruction(BatchPriceHistorySqlTask.class);
             var prices = mockConstruction(BatchPriceUpdateSqlTask.class)) {
            economy.runOneIteration();
            assertEquals(2500, result.join());
            assertEquals(before.samples, economy.getPrices().get(436).samples);
            verify(history.constructed().getFirst()).run(any(Connection.class));
            verify(prices.constructed().getFirst()).run(any(Connection.class));
            verify(world.getConnectionPool().take()).commit();
        }
    }

    @Test void failedPersistenceIsNotReportedAsSaved() throws Exception {
        var result = economy.setGuidePrice(436, 2500);
        try (var history = mockConstruction(BatchPriceHistorySqlTask.class);
             var prices = mockConstruction(BatchPriceUpdateSqlTask.class, (task, context) ->
                     doThrow(new IllegalStateException("database unavailable")).when(task).run(any(Connection.class)))) {
            assertThrows(IllegalStateException.class, economy::runOneIteration);
            assertTrue(result.isCompletedExceptionally());
            verify(world.getConnectionPool().take()).rollback();
        }
    }
}
