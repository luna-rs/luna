package io.luna.game.model.collision;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.def.GameObjectDefinition;
import io.luna.game.model.object.GameObject;
import io.luna.game.model.object.ObjectType;

import java.util.Objects;

import static io.luna.game.model.object.ObjectType.*;

/**
 * Represents a batch of collision changes to be applied to one or more tiles.
 * <p>
 * Instances of this class are typically created via the nested {@link Builder} and then consumed by
 * {@link CollisionManager#apply(CollisionUpdate, boolean)}.
 * </p>
 *
 * @author Major
 * @author lare96
 */
public final class CollisionUpdate {

    /**
     * Returns whether an object with the given {@link GameObjectDefinition} and object {@code type} should cause its
     * tile(s) to be blocked. This is used by {@link Builder#object(GameObject)} to decide if collision flags should
     * be generated at all for the object.
     *
     * @param definition The game's definition of the object.
     * @param type The raw object type id (see {@link ObjectType}).
     * @return {@code true} if the object should block movement on its tiles, otherwise {@code false}.
     */
    private static boolean unwalkable(GameObjectDefinition definition, int type) {
        boolean isSolidFloorDecoration = type == GROUND_DECORATION.getId() && definition.isInteractive();
        boolean isRoof = type > DIAGONAL_DEFAULT.getId() && type < GROUND_DECORATION.getId();

        boolean isWall = type >= STRAIGHT_WALL.getId() && type <= RECTANGLE_CORNER_WALL.getId() ||
                type == DIAGONAL_WALL.getId();

        boolean isSolidInteractable = (type == DIAGONAL_DEFAULT.getId() ||
                type == DEFAULT.getId()) && definition.isSolid();

        return isWall || isRoof || isSolidInteractable || isSolidFloorDecoration;
    }

    /**
     * A directional flag in a {@link CollisionUpdate}.
     */
    public static final class DirectionFlag {

        /**
         * Whether the direction is impenetrable (blocks projectiles in addition to mobs).
         */
        private final boolean impenetrable;

        /**
         * The blocked direction itself.
         */
        private final Direction direction;

        /**
         * Creates a new {@link DirectionFlag}.
         *
         * @param impenetrable {@code true} if the direction should also block projectiles,
         * {@code false} if only mobs are blocked.
         * @param direction The direction from the tile that is blocked.
         */
        public DirectionFlag(boolean impenetrable, Direction direction) {
            this.impenetrable = impenetrable;
            this.direction = direction;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof DirectionFlag) {
                DirectionFlag other = (DirectionFlag) obj;
                return impenetrable == other.impenetrable && direction == other.direction;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(impenetrable, direction);
        }

        /**
         * Returns whether this flag represents an impenetrable direction.
         *
         * @return {@code true} if projectiles are blocked in this direction, otherwise {@code false}.
         */
        public boolean isImpenetrable() {
            return impenetrable;
        }

        /**
         * Returns the direction this flag represents.
         *
         * @return The blocked direction.
         */
        public Direction getDirection() {
            return direction;
        }
    }

    /**
     * Builder for {@link CollisionUpdate} instances.
     * <p>
     * The builder accumulates directional collision information for a set of tiles and produces an immutable
     * {@link CollisionUpdate} snapshot via {@link #build()}.
     * </p>
     */
    public static final class Builder {

        /**
         * Accumulated direction flags grouped by world position.
         */
        private final ImmutableSetMultimap.Builder<Position, DirectionFlag> flags = ImmutableSetMultimap.builder();

        /**
         * The update type (adding or removing flags).
         */
        private CollisionUpdateType type;

        /**
         * Sets the type of the {@link CollisionUpdate}.
         *
         * <p>
         * This must be called exactly once per builder instance before calling {@link #build()}.
         * </p>
         *
         * @param type The type of collision update to use (adding or removing).
         */
        public void type(CollisionUpdateType type) {
            Preconditions.checkState(this.type == null, "update type has already been set");
            this.type = type;
        }

        /**
         * Marks the tile at {@code position} as untraversable in the given directions.
         * <p>
         * Each direction in {@code directions} becomes a {@link DirectionFlag} entry at this position.
         * Whether the flags are impenetrable (block projectiles) is controlled by {@code impenetrable}.
         * </p>
         *
         * @param position The world position of the tile being updated.
         * @param impenetrable {@code true} if the tile should also block projectiles in those directions.
         * @param directions The directions that are untraversable from this tile.
         */
        public void tile(Position position, boolean impenetrable, ImmutableList<Direction> directions) {
            if (directions.isEmpty()) {
                return;
            }
            directions.forEach(direction -> flags.put(position, new DirectionFlag(impenetrable, direction)));
        }

        /**
         * Adds collision flags for a straight wall.
         * <p>
         * Walls are represented by:
         * </p>
         * <ul>
         *     <li>The tile where the wall is placed (blocking movement in the wall's facing direction).</li>
         *     <li>The adjacent tile one step in the facing direction (blocking movement from the opposite direction).</li>
         * </ul>
         * <p>
         * For example, a wall facing south will:
         * </p>
         * <ul>
         *     <li>Block movement south from its own tile.</li>
         *     <li>Block movement north from the tile immediately south of it.</li>
         * </ul>
         *
         * @param position The world position where the wall is placed.
         * @param impenetrable {@code true} if the wall should block projectiles, otherwise {@code false}.
         * @param orientation The cardinal direction the wall faces.
         */
        public void wall(Position position, boolean impenetrable, Direction orientation) {
            tile(position, impenetrable, ImmutableList.of(orientation));
            tile(position.translate(1, orientation), impenetrable, ImmutableList.of(orientation.opposite()));
        }

        /**
         * Adds collision flags for a larger corner wall.
         * <p>
         * A corner wall is represented by the two directions it faces and the two tiles in each of those
         * directions. For example, a corner oriented {@code NORTH_EAST} will:
         * </p>
         * <ul>
         *     <li>Block movement to the north and east from the corner tile itself.</li>
         *     <li>Block movement from the south on the tile directly north of the corner.</li>
         *     <li>Block movement from the west on the tile directly east of the corner.</li>
         * </ul>
         *
         * @param position The world position of the corner wall.
         * @param impenetrable {@code true} if the wall should block projectiles, otherwise {@code false}.
         * @param orientation The diagonal direction of the corner (e.g. {@link Direction#NORTH_EAST}).
         */
        public void largeCornerWall(Position position, boolean impenetrable, Direction orientation) {
            ImmutableList<Direction> directions = Direction.diagonalComponents(orientation);
            tile(position, impenetrable, directions);

            for (Direction direction : directions) {
                tile(position.translate(1, direction), impenetrable, ImmutableList.of(direction.opposite()));
            }
        }

        /**
         * Adds collision flags appropriate for the given {@link GameObject}.
         * <p>
         * This method inspects the {@link GameObjectDefinition} and {@link ObjectType} of {@code object} to
         * determine:
         * </p>
         * <ul>
         *     <li>Whether the object should block movement at all (see {@link #unwalkable(GameObjectDefinition, int)}).</li>
         *     <li>Whether it should be treated as a solid multi-tile object, a wall, a corner, or a floor decoration.</li>
         *     <li>Which directions and tiles to flag as blocked.</li>
         * </ul>
         *
         * @param object The object whose presence should contribute collision data.
         */
        public void object(GameObject object) {
            GameObjectDefinition definition = object.getDefinition();
            Position position = object.getPosition();
            int type = object.getObjectType().getId();

            if (!unwalkable(definition, type)) {
                return;
            }

            int x = position.getX(), y = position.getY(), height = position.getZ();
            boolean impenetrable = definition.isImpenetrable();
            int orientation = object.getDirection().getId();

            if (type == ObjectType.GROUND_DECORATION.getId()) {
                // Solid, interactive floor decorations block all directions on a single tile.
                if (definition.isInteractive() && definition.isSolid()) {
                    tile(new Position(x, y, height), impenetrable, Direction.NESW);
                }
            } else if (type >= DIAGONAL_WALL.getId() && type < GROUND_DECORATION.getId()) {
                // Large, solid multi-tile objects: block all directions on each covered tile.
                for (int dx = 0; dx < definition.getSizeX(); dx++) {
                    for (int dy = 0; dy < definition.getSizeY(); dy++) {
                        tile(new Position(x + dx, y + dy, height), impenetrable, Direction.NESW);
                    }
                }
            } else if (type == STRAIGHT_WALL.getId()) {
                wall(position, impenetrable, Direction.WNES.get(orientation));
            } else if (type == DIAGONAL_CORNER_WALL.getId() || type == RECTANGLE_CORNER_WALL.getId()) {
                largeCornerWall(position, impenetrable, Direction.WNES_DIAGONAL.get(orientation));
            } else if (type == WALL_CORNER.getId()) {
                largeCornerWall(position, impenetrable, Direction.WNES_DIAGONAL.get(orientation));
            }
        }

        /**
         * Builds a new immutable {@link CollisionUpdate} from the accumulated state.
         *
         * @return A new {@link CollisionUpdate} instance.
         * @throws NullPointerException if the update type has not been set.
         */
        public CollisionUpdate build() {
            Preconditions.checkNotNull(type, "update type must not be null");
            return new CollisionUpdate(type, flags.build());
        }
    }

    /**
     * The type of this update (e.g. adding or removing collision flags).
     */
    private final CollisionUpdateType type;

    /**
     * A mapping of world {@link Position}s to their associated {@link DirectionFlag}s.
     * <p>
     * Each position can have multiple flags, one per blocked direction. The manager later interprets these flags
     * into {@link CollisionFlag} values on the appropriate {@link CollisionMatrix}.
     * </p>
     */
    private final ImmutableSetMultimap<Position, DirectionFlag> flags;

    /**
     * Creates a new {@link CollisionUpdate}.
     *
     * @param type The {@link CollisionUpdateType} describing whether collision is being added or removed.
     * @param flags A multimap of positions to their direction flags.
     */
    public CollisionUpdate(CollisionUpdateType type, ImmutableSetMultimap<Position, DirectionFlag> flags) {
        this.type = type;
        this.flags = flags;
    }

    /**
     * Returns the type of this update.
     *
     * @return The update type (adding or removing flags).
     */
    public CollisionUpdateType getType() {
        return type;
    }

    /**
     * Returns the mapping of tiles to their direction flags.
     *
     * @return A multimap of positions to {@link DirectionFlag}s.
     */
    public ImmutableSetMultimap<Position, DirectionFlag> getFlags() {
        return flags;
    }
}
