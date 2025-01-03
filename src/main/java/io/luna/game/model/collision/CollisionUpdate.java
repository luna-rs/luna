package io.luna.game.model.collision;

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.def.GameObjectDefinition;
import io.luna.game.model.object.GameObject;
import io.luna.game.model.object.ObjectType;

import java.util.Objects;
import java.util.stream.Stream;

import static io.luna.game.model.object.ObjectType.*;

/**
 * A global update to the collision matrices.
 */
public final class CollisionUpdate {

    /**
     * The type of this update.
     */
    private final CollisionUpdateType type;

    /**
     * A mapping of {@link Position}s to their {@link DirectionFlag}s.
     */
    private final Multimap<Position, DirectionFlag> flags;

    /**
     * Creates the CollisionUpdate.
     *
     * @param type The {@link CollisionUpdateType} of this update.
     * @param flags A {@link Multimap} of {@link Position}s to their {@link DirectionFlag}s.
     */
    public CollisionUpdate(CollisionUpdateType type, Multimap<Position, DirectionFlag> flags) {
        this.type = type;
        this.flags = flags;
    }

    /**
     * Get the type of this update (ADDING, or REMOVING).
     *
     * @return The type of this update.
     */
    public CollisionUpdateType getType() {
        return type;
    }

    /**
     * Get the mapping of tiles -> flags contained in this update.
     *
     * @return The flags contained in this update.
     */
    public Multimap<Position, DirectionFlag> getFlags() {
        return flags;
    }

    /**
     * A directional flag in a {@code CollisionUpdate}. Consists of a {@code direction} and a flag indicating whether
     * that tile is impenetrable as well as untraversable.
     */
    public static final class DirectionFlag {

        /**
         * If the direction is impenetrable.
         */
        private final boolean impenetrable;

        /**
         * The direction.
         */
        private final Direction direction;

        /**
         * Creates a new {@link DirectionFlag}.
         *
         * @param impenetrable If the direction is impenetrable.
         * @param direction The direction.
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
         * Check if this flag represents an impenetrable direction.
         *
         * @return {@code true} if this flag represents an impenetrable direction, {@code false} otherwise.
         */
        public boolean isImpenetrable() {
            return impenetrable;
        }

        /**
         * Get the direction this flag represents.
         *
         * @return The direction this flag represents.
         */
        public Direction getDirection() {
            return direction;
        }

    }

    /**
     * A builder for CollisionUpdates.
     */
    public static final class Builder {

        /**
         * The flags.
         */
        private final Multimap<Position, DirectionFlag> flags;

        /**
         * The type.
         */
        private CollisionUpdateType type;

        /**
         * Creates a new builder.
         */
        public Builder() {
            this.flags = MultimapBuilder.hashKeys().hashSetValues().build();
        }

        /**
         * Set the type of the {@link CollisionUpdate}. Can only be called once.
         *
         * @param type The type of collision update to use.
         */
        public void type(CollisionUpdateType type) {
            Preconditions.checkState(type != null, "update type has already been set");
            this.type = type;
        }

        /**
         * Sets the tile at the given {@code position} as untraversable in the given directions.
         *
         * @param position The world position of the tile.
         * @param directions The directions that are untraversable from this tile.
         */
        public void tile(Position position, boolean impenetrable, Direction... directions) {
            if (directions.length == 0) {
                return;
            }

            Stream.of(directions).forEach(direction -> flags.put(position, new DirectionFlag(impenetrable, direction)));
        }

        /**
         * Flag a wall in the CollisionUpdate.  When constructing a CollisionMatrix, the flags for a wall are represented
         * as the tile the wall exists on and the tile one step in the facing direction. So for a wall facing south,
         * the tile one step to the south be flagged as untraversable from the north.
         *
         * @param position The position of the wall.
         * @param impenetrable If projectiles can pass through this wall.
         * @param orientation The facing direction of this wall.
         */
        public void wall(Position position, boolean impenetrable, Direction orientation) {
            tile(position, impenetrable, orientation);
            tile(position.translate(1, orientation), impenetrable, orientation.opposite());
        }

        /**
         * Flag a larger corner wall in the CollisionUpdate.  A corner is represented by the 2 directions that it faces,
         * and the 2 tiles in both directions.  For example, when a tile is facing north its facing directions
         * are north and east, so the position of the object will be untraversable from those directions.  Additionally,
         * the tile 1 step to the north, and 1 step to the east will be untraversable from the opposite directions of
         * north and east respectively.
         * <p>
         * todo: "large corner wall", is that really what this is?
         *
         * @param position The position of the corner wall.
         * @param impenetrable If projectiles can pass through this corner wall.
         * @param orientation The direction of this corner wall
         */
        public void largeCornerWall(Position position, boolean impenetrable, Direction orientation) {
            Direction[] directions = Direction.diagonalComponents(orientation);
            tile(position, impenetrable, directions);

            for (Direction direction : directions) {
                tile(position.translate(1, direction), impenetrable, direction.opposite());
            }
        }

        /**
         * Flag a collision update for the given {@link GameObject}.
         *
         * @param object The object to update collision flags for.
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
                if (definition.isInteractive() && definition.isSolid()) {
                    tile(new Position(x, y, height), impenetrable, Direction.NESW);
                }
            } else if (type >= DIAGONAL_WALL.getId() && type < GROUND_DECORATION.getId()) {
                for (int dx = 0; dx < definition.getSizeX(); dx++) {
                    for (int dy = 0; dy < definition.getSizeY(); dy++) {
                        tile(new Position(x + dx, y + dy, height), impenetrable, Direction.NESW);
                    }
                }
            } else if (type == STRAIGHT_WALL.getId()) {
                wall(position, impenetrable, Direction.WNES[orientation]);
            } else if (type == DIAGONAL_CORNER_WALL.getId() || type == RECTANGLE_CORNER.getId()) {
                wall(position, impenetrable, Direction.WNES_DIAGONAL[orientation]);
            } else if (type == WALL_CORNER.getId()) {
                largeCornerWall(position, impenetrable, Direction.WNES_DIAGONAL[orientation]);
            }
        }

        /**
         * Create a new {@link CollisionUpdate}.
         *
         * @return A new CollisionUpdate with the flags in this builder.
         */
        public CollisionUpdate build() {
            Preconditions.checkNotNull(type, "update type must not be null");
            return new CollisionUpdate(type, Multimaps.unmodifiableMultimap(flags));
        }

    }

    /**
     * Returns whether an object with the specified {@link GameObjectDefinition} and {@code type} should result in
     * the tile(s) it is located on being blocked.
     *
     * @param definition The {@link GameObjectDefinition} of the object.
     * @param type The type of the object.
     * @return {@code true} iff the tile(s) the object is on should be blocked.
     */
    private static boolean unwalkable(GameObjectDefinition definition, int type) {
            boolean isSolidFloorDecoration = type == GROUND_DECORATION.getId() && definition.isInteractive();
        boolean isRoof = type > DIAGONAL_DEFAULT.getId() && type < GROUND_DECORATION.getId();

        boolean isWall = type >= STRAIGHT_WALL.getId() && type <= STRAIGHT_WALL.getId() ||
                type == DIAGONAL_WALL.getId();

        boolean isSolidInteractable = (type == DIAGONAL_DEFAULT.getId() ||
                type == DEFAULT.getId()) && definition.isSolid();

        return isWall || isRoof || isSolidInteractable || isSolidFloorDecoration;
    }

}