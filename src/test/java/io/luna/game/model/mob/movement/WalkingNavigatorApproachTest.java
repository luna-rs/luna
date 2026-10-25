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
import io.luna.game.model.collision.CollisionView;
import io.luna.game.model.path.route.RoutePathfinder;
import io.luna.game.model.path.route.RouteStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.util.Deque;
import java.util.Optional;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for legal and reachable interaction approaches.
 *
 * @author lare96
 */
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

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void npcNavigationRoutesAroundBlockedNearestSides(boolean continuous) {
        var collision = player.getWorld().getCollisionManager();
        CollisionView view = mock(CollisionView.class);
        when(collision.view(true)).thenReturn(view);
        // Synthetic collision map: the two closest approach tiles and the NPC tile are occupied.
        when(view.get(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int y = invocation.getArgument(1);
            return (x == 105 && (y == 104 || y == 105)) || (x == 104 && y == 105)
                    ? io.luna.game.model.collision.CollisionFlag.LOC : 0;
        });
        RoutePathfinder pathfinder = new RoutePathfinder(collision, 1, 0, RouteStrategy.NORMAL);
        Position start = player.getPosition();
        Position nearest = navigator.computeOffsetPosition(target, Optional.empty());
        assertNotEquals(PathResultType.COMPLETE, pathfinder.find(start, nearest).getType());

        navigator = spy(navigator);
        doReturn(pathfinder).when(navigator).getDefaultPathfinder();
        when(player.getNavigator()).thenReturn(navigator);
        when(player.getService().getGameExecutor()).thenReturn(Runnable::run);
        when(target.abs()).thenReturn(new Position(105, 105));
        when(target.isAlive()).thenReturn(true);
        when(collision.reached(eq(player), eq(target), any())).thenReturn(false);
        NavigationRequest request = NavigationRequest.builder(player).target(target)
                .policy(InteractionPolicy.STANDARD_SIZE).continuous(continuous).build();

        assertFalse(new NavigationAction(player, request).run());
        ArgumentCaptor<Deque<Position>> queued = ArgumentCaptor.forClass(Deque.class);
        verify(player.getWalking()).replacePath(queued.capture());
        Position endpoint = queued.getValue().peekLast();
        assertTrue(endpoint.equals(new Position(105, 106)) || endpoint.equals(new Position(106, 105)),
                "Must reach an open alternative side instead of stopping at the blocked nearest side: " + endpoint);
        // Every segment of the chosen route must still obey the normal collision flags.
        Position previous = start;
        for (Position waypoint : queued.getValue()) {
            while (!previous.equals(waypoint)) {
                int dx = Integer.compare(waypoint.getX(), previous.getX());
                int dy = Integer.compare(waypoint.getY(), previous.getY());
                if (dx != 0 && dy != 0) {
                    assertEquals(0, view.get(previous.getX() + dx, previous.getY(), 0));
                    assertEquals(0, view.get(previous.getX(), previous.getY() + dy, 0));
                }
                previous = new Position(previous.getX() + dx, previous.getY() + dy);
                assertEquals(0, view.get(previous.getX(), previous.getY(), 0));
            }
        }
    }

    @Test void npcExplicitFollowOffsetKeepsItsRequestedDestination() {
        WalkingNavigator mockedNavigator = mock(WalkingNavigator.class);
        when(player.getNavigator()).thenReturn(mockedNavigator);
        var pathfinder = navigator.getDefaultPathfinder();
        when(mockedNavigator.getDefaultPathfinder()).thenReturn(pathfinder);
        when(target.abs()).thenReturn(new Position(105, 105));
        when(target.isAlive()).thenReturn(true);
        when(player.getWorld().getCollisionManager().reached(eq(player), eq(target), any())).thenReturn(false);
        Position offset = new Position(106, 106);
        when(mockedNavigator.computeOffsetPosition(target, Optional.of(Direction.NORTH_EAST))).thenReturn(offset);
        NavigationRequest request = NavigationRequest.builder(player).target(target)
                .policy(InteractionPolicy.STANDARD_SIZE).offsetDir(Direction.NORTH_EAST).continuous(true).build();
        assertFalse(new NavigationAction(player, request).run());
        verify(mockedNavigator).walk(eq(offset), any(), eq(false));
        verify(mockedNavigator, never()).walkToInteraction(any(), any(), any(), anyBoolean());
    }

    @SuppressWarnings("unchecked")
    @Test void largeNpcUsesAnInteriorEdgeTileAndRefreshesItAfterMoving() {
        when(target.sizeX()).thenReturn(3);
        when(target.sizeY()).thenReturn(3);
        when(player.getService().getGameExecutor()).thenReturn(Runnable::run);
        when(player.getWorld().getCollisionManager().reached(any(), eq(target), any()))
                .thenAnswer(invocation -> {
                    Position candidate = invocation.getArgument(0);
                    Position origin = target.getPosition();
                    return candidate.equals(new Position(origin.getX() + 1, origin.getY() + 3));
                });
        GamePathfinder<Position> pathfinder = mock(GamePathfinder.class);
        when(pathfinder.find(any(), any())).thenAnswer(invocation ->
                new PathResult<>(PathResultType.COMPLETE,
                        new ArrayDeque<>(List.of(invocation.<Position>getArgument(1)))));

        navigator.walkToInteraction(target, Optional.empty(), pathfinder, false).join();
        when(target.getPosition()).thenReturn(new Position(108, 105));
        navigator.walkToInteraction(target, Optional.empty(), pathfinder, false).join();

        ArgumentCaptor<Deque<Position>> queued = ArgumentCaptor.forClass(Deque.class);
        verify(player.getWalking(), times(2)).replacePath(queued.capture());
        assertEquals(new Position(106, 108), queued.getAllValues().get(0).peekLast());
        assertEquals(new Position(109, 108), queued.getAllValues().get(1).peekLast());
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
        when(mockedNavigator.walkToInteraction(eq(object), any(), any(), eq(false)))
                .thenReturn(new CompletableFuture<>());
        NavigationRequest adjacent = NavigationRequest.builder(player).target(object)
                .policy(InteractionPolicy.STANDARD_SIZE).build();
        assertFalse(new NavigationAction(player, adjacent).run());
        verify(mockedNavigator).walkToInteraction(eq(object), eq(Optional.empty()), any(), eq(false));
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
        assertSame(complete, navigator.findInteractionPath(start, List.of(south, west), pathfinder));
    }

    @SuppressWarnings("unchecked")
    @Test void allBlockedRoutesReturnNoPathAndLongTravelKeepsPartialRoute() {
        GamePathfinder<Position> pathfinder = mock(GamePathfinder.class);
        Position start = player.getPosition();
        Position west = new Position(2975, 3369);
        when(pathfinder.find(start, west)).thenReturn(new PathResult<>(PathResultType.FAILED, null));
        assertNull(navigator.findInteractionPath(start, List.of(west), pathfinder));
        var partial = new ArrayDeque<>(List.of(new Position(2970, 3369)));
        when(pathfinder.find(start, west)).thenReturn(new PathResult<>(PathResultType.PARTIAL, partial));
        assertSame(partial, navigator.findInteractionPath(start, List.of(west), pathfinder));
    }
}
