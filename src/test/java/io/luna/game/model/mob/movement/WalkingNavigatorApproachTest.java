package io.luna.game.model.mob.movement;

import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.path.GamePathfinder;
import io.luna.game.model.path.PathResult;
import io.luna.game.model.path.PathResultType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

    private GameObject furnace() {
        GameObject object = mock(GameObject.class);
        when(object.getPosition()).thenReturn(new Position(2976, 3368));
        when(object.abs()).thenReturn(new Position(2976, 3368));
        when(object.sizeX()).thenReturn(2);
        when(object.sizeY()).thenReturn(3);
        return object;
    }

    @Test void objectApproachIncludesMiddleOfLegalSideAndRejectsBlockedSouth() {
        GameObject object = furnace();
        Position middleWest = new Position(2975, 3369);
        when(player.getWorld().getCollisionManager().reached(any(), eq(object), eq(InteractionPolicy.STANDARD_SIZE)))
                .thenAnswer(invocation -> middleWest.equals(invocation.getArgument(0)));
        assertEquals(List.of(middleWest), navigator.objectApproachPositions(object, Optional.empty()));
        assertTrue(navigator.objectApproachPositions(object, Optional.of(Direction.SOUTH)).isEmpty());
    }

    @Test void navigationActionUsesObjectApproachButExactPositionRequestsStayExact() {
        GameObject object = furnace();
        WalkingNavigator mockedNavigator = mock(WalkingNavigator.class);
        when(player.getNavigator()).thenReturn(mockedNavigator);
        var pathfinder = navigator.getDefaultPathfinder();
        when(mockedNavigator.getDefaultPathfinder()).thenReturn(pathfinder);
        when(mockedNavigator.walkToObject(eq(object), any(), any(), eq(false)))
                .thenReturn(new CompletableFuture<>());
        NavigationRequest adjacent = NavigationRequest.builder(player).target(object)
                .policy(InteractionPolicy.STANDARD_SIZE).build();
        assertFalse(new NavigationAction(player, adjacent).run());
        verify(mockedNavigator).walkToObject(eq(object), eq(Optional.empty()), any(), eq(false));
        verify(mockedNavigator, never()).walk(eq(object), any(), anyBoolean());

        Position exact = new Position(2976, 3368);
        NavigationRequest exactRequest = NavigationRequest.builder(player).target(exact)
                .policy(new InteractionPolicy(io.luna.game.model.mob.interact.InteractionType.SIZE, 0)).build();
        assertFalse(new NavigationAction(player, exactRequest).run());
        verify(mockedNavigator).walk(eq(exact), any(), eq(false));
    }

    @SuppressWarnings("unchecked")
    @Test void unreachableNearestSideDoesNotHideCompleteRouteToAnotherSide() {
        GamePathfinder<Position> pathfinder = mock(GamePathfinder.class);
        Position start = player.getPosition();
        Position south = new Position(2976, 3367);
        Position west = new Position(2975, 3369);
        var partial = new ArrayDeque<>(List.of(new Position(2976, 3366)));
        var complete = new ArrayDeque<>(List.of(west));
        when(pathfinder.find(start, south)).thenReturn(new PathResult<>(PathResultType.PARTIAL, partial));
        when(pathfinder.find(start, west)).thenReturn(new PathResult<>(PathResultType.COMPLETE, complete));
        assertSame(complete, navigator.findObjectPath(start, List.of(south, west), pathfinder));
    }

    @SuppressWarnings("unchecked")
    @Test void allBlockedRoutesReturnNoPathAndLongTravelKeepsPartialRoute() {
        GamePathfinder<Position> pathfinder = mock(GamePathfinder.class);
        Position start = player.getPosition();
        Position west = new Position(2975, 3369);
        when(pathfinder.find(start, west)).thenReturn(new PathResult<>(PathResultType.FAILED, null));
        assertNull(navigator.findObjectPath(start, List.of(west), pathfinder));
        var partial = new ArrayDeque<>(List.of(new Position(2970, 3369)));
        when(pathfinder.find(start, west)).thenReturn(new PathResult<>(PathResultType.PARTIAL, partial));
        assertSame(partial, navigator.findObjectPath(start, List.of(west), pathfinder));
    }
}
