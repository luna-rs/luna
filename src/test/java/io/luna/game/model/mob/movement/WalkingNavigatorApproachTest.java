package io.luna.game.model.mob.movement;

import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalkingNavigatorApproachTest {
    private Player player;
    private Npc target;
    private WalkingNavigator navigator;

    @BeforeEach void setUp() {
        player = mock(Player.class, RETURNS_DEEP_STUBS);
        target = mock(Npc.class);
        when(player.getPosition()).thenReturn(new Position(100, 100));
        when(target.getPosition()).thenReturn(new Position(105, 105));
        when(player.sizeX()).thenReturn(1);
        when(player.sizeY()).thenReturn(1);
        when(target.sizeX()).thenReturn(1);
        when(target.sizeY()).thenReturn(1);
        when(player.getWorld().getCollisionManager().reached(any(), eq(target), any())).thenReturn(true);
        navigator = new WalkingNavigator(player);
    }

    @Test void diagonalApproachEndsAtACardinalSide() {
        Position end = navigator.computeOffsetPosition(target, Optional.empty());
        assertEquals(1, Math.abs(end.getX() - 105) + Math.abs(end.getY() - 105));
        assertTrue(end.getX() < 105 || end.getY() < 105);
    }

    @Test void blockedNearestSideUsesAnotherReachableSide() {
        when(player.getWorld().getCollisionManager().reached(any(), eq(target), any())).thenAnswer(invocation -> {
            Position candidate = invocation.getArgument(0);
            return candidate.equals(new Position(105, 106));
        });
        assertEquals(new Position(105, 106), navigator.computeOffsetPosition(target, Optional.empty()));
    }

    @Test void explicitFollowOffsetIsPreserved() {
        assertEquals(new Position(106, 106), navigator.computeOffsetPosition(target, Optional.of(Direction.NORTH_EAST)));
    }
}
