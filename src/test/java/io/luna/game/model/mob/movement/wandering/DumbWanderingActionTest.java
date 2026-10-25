package io.luna.game.model.mob.movement.wandering;

import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.area.Area;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.movement.WalkingNavigator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.*;

/**
 * Regression coverage for NPCs left outside their wandering bounds after combat.
 *
 * @author lare96
 */
class DumbWanderingActionTest {

    private Npc npc;
    private Area area;
    private WalkingNavigator navigator;
    private DumbWanderingAction action;

    @BeforeEach
    void setUp() {
        npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        area = mock(Area.class);
        navigator = npc.getNavigator();
        when(npc.getPosition()).thenReturn(new Position(110, 110));
        when(area.randomPosition()).thenReturn(new Position(100, 100));
        when(npc.isWandering()).thenReturn(true);
        when(npc.getWalking().isEmpty()).thenReturn(true);
        when(npc.getCombat().getTarget()).thenReturn(null);
        when(npc.getInteractingWith()).thenReturn(null);
        action = new DumbWanderingAction(npc, area, WanderingFrequency.RAPID);
    }

    @Test
    void displacedNpcCanCrossTilesOutsideItsWanderArea() {
        when(navigator.step(Direction.SOUTH_WEST)).thenReturn(true);
        action.run();
        verify(navigator).step(Direction.SOUTH_WEST);
        verifyNoMoreInteractions(navigator);
    }

    @Test
    void blockedDiagonalFallsBackToHorizontalThenVertical() {
        when(navigator.step(Direction.SOUTH)).thenReturn(true);
        action.run();
        InOrder order = inOrder(navigator);
        order.verify(navigator).step(Direction.SOUTH_WEST);
        order.verify(navigator).step(Direction.WEST);
        order.verify(navigator).step(Direction.SOUTH);
        order.verifyNoMoreInteractions();
    }

    @Test
    void successfulHorizontalFallbackQueuesOnlyOneStep() {
        when(navigator.step(Direction.WEST)).thenReturn(true);
        action.run();
        verify(navigator).step(Direction.SOUTH_WEST);
        verify(navigator).step(Direction.WEST);
        verify(navigator, never()).step(Direction.SOUTH);
    }

    @Test
    void differentPlaneDoesNotQueueMovement() {
        when(area.randomPosition()).thenReturn(new Position(100, 100, 1));
        action.run();
        verifyNoInteractions(navigator);
    }

    @Test
    void combatAndExistingMovementKeepOwnership() {
        when(npc.getCombat().inCombat()).thenReturn(true);
        action.run();
        when(npc.getCombat().inCombat()).thenReturn(false);
        when(npc.getWalking().isEmpty()).thenReturn(false);
        action.run();
        verifyNoInteractions(navigator);
    }

    @Test
    void npcInsideAreaKeepsLocalWandering() {
        when(area.contains(any(Position.class))).thenReturn(true);
        when(navigator.step(any())).thenReturn(true);
        action.run();
        verify(area, never()).randomPosition();
        verify(navigator).step(any());
    }

    @Test
    void playersDoNotAcquireNpcRecoveryBehavior() {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        when(player.getPosition()).thenReturn(new Position(110, 110));
        new DumbWanderingAction(player, area, WanderingFrequency.RAPID).wander();
        verify(area, never()).randomPosition();
        verify(player.getNavigator(), never()).step(any());
    }
}
