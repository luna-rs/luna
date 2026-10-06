package io.luna.game.model.collision;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.Position;
import io.luna.game.model.def.GameObjectDefinition;
import io.luna.game.model.object.GameObject;
import io.luna.game.model.object.ObjectDirection;
import io.luna.game.model.object.ObjectType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.OptionalInt;

import static io.luna.game.model.collision.CollisionFlag.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CollisionUpdate.Builder#object(GameObject)}.
 *
 * @author hydrozoa
 */
final class CollisionUpdateTest {

    private static final Position ORIGIN = new Position(10, 10, 0);

    private static GameObjectDefinition definition(int sizeX, int sizeY, boolean solid, boolean impenetrable,
                                                   boolean interactive) {
        return new GameObjectDefinition(1, "test", "test", sizeX, sizeY, 0, solid, impenetrable, interactive,
                OptionalInt.empty(), ImmutableList.of(), false, null);
    }

    private static CollisionUpdate build(GameObjectDefinition definition, ObjectType type, ObjectDirection direction) {
        GameObject object = Mockito.mock(GameObject.class);
        Mockito.when(object.def()).thenReturn(definition);
        Mockito.when(object.getObjectType()).thenReturn(type);
        Mockito.when(object.getDirection()).thenReturn(direction);
        Mockito.when(object.getPosition()).thenReturn(ORIGIN);

        CollisionUpdate.Builder builder = new CollisionUpdate.Builder();
        builder.type(CollisionUpdateType.ADDING);
        builder.object(object);
        return builder.build();
    }

    private static CollisionUpdate solid(ObjectType type, ObjectDirection direction) {
        return build(definition(1, 1, true, false, false), type, direction);
    }

    private static int at(CollisionUpdate update, int x, int y) {
        return update.getFlags().getOrDefault(new Position(x, y, 0), 0);
    }

    @Test
    void testStraightWall() {
        CollisionUpdate update = solid(ObjectType.STRAIGHT_WALL, ObjectDirection.WEST);
        assertEquals(WALL_WEST, at(update, 10, 10));
        assertEquals(WALL_EAST, at(update, 9, 10));
        assertEquals(2, update.getFlags().size());
    }

    @Test
    void testImpenetrableWallAlsoBlocksProjectiles() {
        CollisionUpdate update = build(definition(1, 1, true, true, false), ObjectType.STRAIGHT_WALL,
                ObjectDirection.NORTH);
        assertEquals(WALL_NORTH | WALL_NORTH_PROJ_BLOCKER, at(update, 10, 10));
        assertEquals(WALL_SOUTH | WALL_SOUTH_PROJ_BLOCKER, at(update, 10, 11));
    }

    @Test
    void testLCornerWall() {
        CollisionUpdate update = solid(ObjectType.WALL_CORNER, ObjectDirection.WEST);
        assertEquals(WALL_WEST | WALL_NORTH, at(update, 10, 10));
        assertEquals(WALL_EAST, at(update, 9, 10));
        assertEquals(WALL_SOUTH, at(update, 10, 11));
    }

    @Test
    void testDiagonalCornerWalls() {
        CollisionUpdate diagonal = solid(ObjectType.DIAGONAL_CORNER_WALL, ObjectDirection.NORTH);
        assertEquals(WALL_NORTH_EAST, at(diagonal, 10, 10));
        assertEquals(WALL_SOUTH_WEST, at(diagonal, 11, 11));

        CollisionUpdate square = solid(ObjectType.RECTANGLE_CORNER_WALL, ObjectDirection.SOUTH);
        assertEquals(WALL_SOUTH_WEST, at(square, 10, 10));
        assertEquals(WALL_NORTH_EAST, at(square, 9, 9));
    }

    @Test
    void testCentrepieceCoversItsFootprint() {
        CollisionUpdate update = build(definition(2, 3, true, false, false), ObjectType.DEFAULT,
                ObjectDirection.WEST);
        assertEquals(6, update.getFlags().size());
        assertEquals(LOC, at(update, 11, 12));
        assertEquals(0, at(update, 12, 10));
    }

    @Test
    void testRotatedCentrepieceSwapsItsFootprint() {
        for (ObjectDirection direction : new ObjectDirection[]{ObjectDirection.NORTH, ObjectDirection.SOUTH}) {
            CollisionUpdate update = build(definition(2, 3, true, true, false), ObjectType.DEFAULT, direction);
            assertEquals(6, update.getFlags().size());
            assertEquals(LOC | LOC_PROJ_BLOCKER, at(update, 12, 11));
            assertEquals(0, at(update, 10, 12));
        }
    }

    @Test
    void testRoofsAndDiagonalWallsBlockTheirFootprint() {
        assertEquals(LOC, at(solid(ObjectType.STRAIGHT_FLAT_TOP_ROOF, ObjectDirection.WEST), 10, 10));
        assertEquals(LOC, at(solid(ObjectType.DIAGONAL_WALL, ObjectDirection.WEST), 10, 10));
    }

    @Test
    void testNonSolidObjectsDoNotCollide() {
        GameObjectDefinition notSolid = definition(1, 1, false, true, true);
        assertTrue(build(notSolid, ObjectType.STRAIGHT_WALL, ObjectDirection.WEST).getFlags().isEmpty());
        assertTrue(build(notSolid, ObjectType.DEFAULT, ObjectDirection.WEST).getFlags().isEmpty());
        assertTrue(build(notSolid, ObjectType.STRAIGHT_FLAT_TOP_ROOF, ObjectDirection.WEST).getFlags().isEmpty());
    }

    @Test
    void testWallDecorationsNeverCollide() {
        GameObjectDefinition everything = definition(1, 1, true, true, true);
        assertTrue(build(everything, ObjectType.STRAIGHT_INSIDE_WALL_DECORATION, ObjectDirection.WEST)
                .getFlags().isEmpty());
    }

    @Test
    void testGroundDecorationOnlyBlocksWhenInteractive() {
        CollisionUpdate interactive = build(definition(1, 1, true, true, true), ObjectType.GROUND_DECORATION,
                ObjectDirection.WEST);
        assertEquals(GROUND_DECOR, at(interactive, 10, 10));

        CollisionUpdate plain = build(definition(1, 1, true, true, false), ObjectType.GROUND_DECORATION,
                ObjectDirection.WEST);
        assertTrue(plain.getFlags().isEmpty());
    }

    @Test
    void testFlagsOnTheSameTileAreCombined() {
        CollisionUpdate.Builder builder = new CollisionUpdate.Builder();
        builder.type(CollisionUpdateType.ADDING);
        builder.flag(ORIGIN, WALL_NORTH);
        builder.flag(ORIGIN, LOC);
        assertEquals(WALL_NORTH | LOC, builder.build().getFlags().get(ORIGIN));
    }

    @Test
    void testBuildNeedsAType() {
        assertThrows(NullPointerException.class, () -> new CollisionUpdate.Builder().build());
    }
}
