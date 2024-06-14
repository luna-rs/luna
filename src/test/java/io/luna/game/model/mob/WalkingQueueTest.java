package io.luna.game.model.mob;

import io.luna.LunaContext;
import io.luna.game.model.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WalkingQueueTest {
    private final PlayerCredentials credentials = new PlayerCredentials("test", "test");
    private final LunaContext context = mock(LunaContext.class);
    private final Player player = new Player(context, credentials);

    private final WalkingQueue queue = new NullWalkingQueue(player);
    private final Position startPosition = new Position(0, 0);


    private WalkingQueueTest() {
        player.setSettings(new NullPlayerSettings());
    }

    @BeforeEach
    void init() {
        player.setPosition(startPosition);
        player.setRunning(false);
        queue.clear();
    }

    @Test
    void whenPlayerIsNotMoving_thenDoNotTakeAnySteps() {
        player.setPosition(startPosition);

        //No movement applied
        queue.process();

        assertEquals(startPosition, player.getPosition());
    }

    @Test
    void whenPlayerIsWalking_thenTakeOneStep() {
        player.setRunEnergy(100);

        queue.walk(2, 2);
        queue.process();

        var stepsTaken = player.getPosition().computeLongestDistance(startPosition);
        assertEquals(stepsTaken, 1);
    }

    @Test
    void whenPlayerIsNotMoving_thenIncreaseRunEnergy() {
        var startingEnergy = 0.0;
        player.setRunEnergy(startingEnergy);
        player.setPosition(startPosition);

        // No movement applied
        queue.process();

        assertTrue(player.getRunEnergy() > startingEnergy);
    }

    @Test
    void whenPlayerIsWalking_thenIncreaseRunEnergy() {
        var startingEnergy = 0.0;
        player.setRunEnergy(startingEnergy);
        player.setPosition(startPosition);

        queue.walk(1, 1);
        queue.process();

        assertTrue(player.getRunEnergy() > startingEnergy);
    }
    @Test
    void whenPlayerIsRunning_AndHasEnergy_thenDecreaseRunEnergy(){
        var startingEnergy = 100;
        player.setRunEnergy(startingEnergy);
        player.setRunning(true);

        queue.walk(2, 2);
        queue.process();

        assertTrue(player.getRunEnergy() < startingEnergy);
    }

    @Test
    void whenPlayerIsRunning_AndHasEnergy_thenTakeTwoSteps() {
        player.setRunEnergy(100);
        player.setRunning(true);

        queue.walk(2, 2);
        queue.process();

        var stepsTaken = player.getPosition().computeLongestDistance(startPosition);
        assertEquals(stepsTaken, 2);
    }

    @Test
    void whenPlayerIsRunning_AndOutOfEnergy_thenTakeOneStep() {
        player.setRunEnergy(0);
        player.setRunning(true);

        queue.walk(2, 2);
        queue.process();

        var stepsTaken = player.getPosition().computeLongestDistance(startPosition);
        assertEquals(stepsTaken, 1);
    }
}