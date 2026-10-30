package io.luna.game.model.path.route;

import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionUpdate;
import io.luna.game.model.collision.CollisionUpdateType;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.mob.interact.InteractionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Checks explicit approach-range interactions while preserving ordinary wall reach and walking collision.
 *
 * @author lare96
 */
final class DistanceInteractionReachTest {

    private static final InteractionPolicy APPROACH_RANGE = new InteractionPolicy(InteractionType.DISTANCE, 2);

    private final TestMap map = new TestMap();

    @Test
    void distancePoliciesRequireAPositiveRange() {
        for (int distance : new int[]{0, -1, -2}) {
            assertThrows(IllegalArgumentException.class, () -> new InteractionPolicy(InteractionType.DISTANCE, distance));
        }
        assertDoesNotThrow(() -> new InteractionPolicy(InteractionType.DISTANCE, 1));
    }

    @Test
    void distanceUsesInclusiveAxisBoundsOnTheSamePlane() {
        Npc target = target(new Position(110, 110));
        assertTrue(map.manager().reached(new Position(112, 112), target, APPROACH_RANGE));
        assertTrue(map.manager().reached(new Position(108, 108), target, APPROACH_RANGE));
        assertFalse(map.manager().reached(new Position(113, 112), target, APPROACH_RANGE));
        assertFalse(map.manager().reached(new Position(112, 113), target, APPROACH_RANGE));
        assertFalse(map.manager().reached(new Position(110, 110, 1), target, APPROACH_RANGE));
    }

    @Test
    void distanceDoesNotBypassTheViewingDistanceLimit() {
        Npc target = target(new Position(110, 110));
        InteractionPolicy excessive = new InteractionPolicy(InteractionType.DISTANCE, Position.VIEWING_DISTANCE + 1);
        assertThrows(IllegalArgumentException.class, () -> map.manager().reached(new Position(110, 112), target, excessive));
    }

    @Test
    void onlyAnExplicitDistancePolicyReachesAcrossAnImpenetrableCounter() {
        Position anchor = new Position(110, 110);
        Position stand = anchor.translate(0, 1);
        Npc target = target(anchor);
        wall(anchor, Direction.NORTH);

        assertTrue(map.manager().reached(stand, target, APPROACH_RANGE));
        assertFalse(map.manager().reached(stand, target, InteractionPolicy.STANDARD_SIZE));
        assertFalse(map.manager().reached(stand, target, new InteractionPolicy(InteractionType.LINE_OF_SIGHT, 2)));
        assertFalse(map.manager().raycast(stand, anchor));
        assertFalse(StepValidator.canTravel(map.view(), 0, stand.getX(), stand.getY(), 0, -1,
                1, 0, RouteStrategy.NORMAL));
    }

    @Test
    void aNormalRouteStopsOnTheAccessibleCustomerSideWhenTheRangeIsReached() {
        Position anchor = new Position(110, 110);
        Position current = anchor.translate(0, 6);
        Npc target = target(anchor);
        for (Direction direction : Direction.NESW) {
            wall(anchor, direction);
        }
        Route route = new RouteFinder().find(map.view(), 0, current.getX(), current.getY(),
                anchor.getX(), anchor.getY(), 1, 0, RouteStrategy.NORMAL, true, 255);
        assertTrue(route.isSuccess());
        assertTrue(route.isAlternative());

        boolean reached = false;
        approach:
        for (Position waypoint : route.getWaypoints()) {
            while (!current.equals(waypoint)) {
                int dx = Integer.compare(waypoint.getX(), current.getX());
                int dy = Integer.compare(waypoint.getY(), current.getY());
                assertTrue(StepValidator.canTravel(map.view(), 0, current.getX(), current.getY(), dx, dy,
                        1, 0, RouteStrategy.NORMAL));
                current = current.translate(dx, dy);
                if (map.manager().reached(current, target, APPROACH_RANGE)) {
                    reached = true;
                    break approach;
                }
            }
        }
        assertTrue(reached);
        assertEquals(anchor.translate(0, 2), current);
        assertFalse(map.manager().reached(current, target, InteractionPolicy.STANDARD_SIZE));
        assertFalse(StepValidator.canTravel(map.view(), 0, 110, 111, 0, -1, 1, 0, RouteStrategy.NORMAL));
    }

    private void wall(Position position, Direction direction) {
        CollisionUpdate.Builder update = new CollisionUpdate.Builder();
        update.type(CollisionUpdateType.ADDING);
        update.wall(position, true, direction);
        map.manager().apply(update.build(), true);
    }

    private static Npc target(Position position) {
        Npc target = mock(Npc.class);
        when(target.getPosition()).thenReturn(position);
        when(target.abs()).thenReturn(position);
        when(target.size()).thenReturn(1);
        when(target.sizeX()).thenReturn(1);
        when(target.sizeY()).thenReturn(1);
        return target;
    }
}
