package io.luna.game.model.collision;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.def.GameObjectDefinition;
import io.luna.game.model.object.GameObject;

import java.util.HashMap;
import java.util.Map;

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
     * Builder for {@link CollisionUpdate} instances.
     * <p>
     * The builder accumulates {@link CollisionFlag} masks for a set of tiles and produces an immutable
     * {@link CollisionUpdate} snapshot via {@link #build()}.
     * </p>
     */
    public static final class Builder {

        /**
         * Accumulated flag masks by world position.
         */
        private final Map<Position, Integer> flags = new HashMap<>();

        /**
         * The update type (adding or removing flags).
         */
        private CollisionUpdateType type;

        /**
         * If the positions are in map coordinates.
         */
        private boolean mapCoordinates;

        /**
         * The tile of the object this update was built for, or {@code null} if it was not built from an object.
         */
        private Position origin;

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
         * Marks the positions of this update as map coordinates, as used by terrain and objects. The tiles of a
         * bridged column are stored one level lower in map coordinates than where mobs stand on them, so updates in
         * map coordinates have that adjustment applied.
         * <p>
         * Updates for mobs use the coordinates they actually stand on, and must not be marked.
         * </p>
         */
        public void mapCoordinates() {
            mapCoordinates = true;
        }

        /**
         * Adds {@link CollisionFlag}s to the tile at {@code position}. Flags added to the same tile more than once are
         * combined.
         *
         * @param position The world position of the tile being updated.
         * @param mask The collision flags to add to (or remove from) the tile.
         */
        public void flag(Position position, int mask) {
            flags.merge(position, mask, (current, added) -> current | added);
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
            wallFlag(position, impenetrable, orientation);
            wallFlag(position.translate(1, orientation), impenetrable, orientation.opposite());
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
            for (Direction direction : directions) {
                wallFlag(position, impenetrable, direction);
                wallFlag(position.translate(1, direction), impenetrable, direction.opposite());
            }
        }

        /**
         * Adds collision flags appropriate for the given {@link GameObject}.
         * <p>
         * This follows the 377 client's rules, which are the same ones rsmod uses:
         * </p>
         * <ul>
         *     <li>Ground decorations block their tile only when they are interactive.</li>
         *     <li>Everything else only collides when the definition is solid.</li>
         *     <li>Walls (types 0-3) block the edges or corners they cover.</li>
         *     <li>Diagonal walls, centrepieces and roofs (types 9-21) block every tile of their footprint. The
         *     footprint is rotated, so objects facing north or south swap their width and length.</li>
         *     <li>Wall decorations (types 4-8) never collide.</li>
         * </ul>
         *
         * @param object The object whose presence should contribute collision data.
         */
        public void object(GameObject object) {
            GameObjectDefinition definition = object.def();
            Position position = object.getPosition();
            origin = position;
            int type = object.getObjectType().getId();
            boolean impenetrable = definition.isImpenetrable();
            int orientation = object.getDirection().getId();

            if (type == GROUND_DECORATION.getId()) {
                if (definition.isInteractive()) {
                    flag(position, CollisionFlag.GROUND_DECOR);
                }
            } else if (!definition.isSolid()) {
                return;
            } else if (type == STRAIGHT_WALL.getId()) {
                wall(position, impenetrable, Direction.WNES.get(orientation));
            } else if (type == DIAGONAL_CORNER_WALL.getId() || type == RECTANGLE_CORNER_WALL.getId()) {
                wall(position, impenetrable, Direction.WNES_DIAGONAL.get(orientation));
            } else if (type == WALL_CORNER.getId()) {
                largeCornerWall(position, impenetrable, Direction.WNES_DIAGONAL.get(orientation));
            } else if (type >= DIAGONAL_WALL.getId() && type < GROUND_DECORATION.getId()) {
                int sizeX = definition.getSizeX();
                int sizeY = definition.getSizeY();
                if (orientation == 1 || orientation == 3) {
                    sizeX = definition.getSizeY();
                    sizeY = definition.getSizeX();
                }

                int mask = impenetrable ? CollisionFlag.LOC | CollisionFlag.LOC_PROJ_BLOCKER : CollisionFlag.LOC;
                for (int dx = 0; dx < sizeX; dx++) {
                    for (int dy = 0; dy < sizeY; dy++) {
                        flag(new Position(position.getX() + dx, position.getY() + dy, position.getZ()), mask);
                    }
                }
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
            return new CollisionUpdate(type, ImmutableMap.copyOf(flags), mapCoordinates, origin);
        }

        /**
         * Adds the wall flag for a single side of a tile.
         *
         * @param position The tile the wall is on.
         * @param impenetrable {@code true} if the wall should block projectiles, otherwise {@code false}.
         * @param direction The side of the tile the wall is on.
         */
        private void wallFlag(Position position, boolean impenetrable, Direction direction) {
            int id = direction.getId();
            int mask = CollisionFlag.WALLS[id];
            if (impenetrable) {
                mask |= CollisionFlag.WALL_PROJ_BLOCKERS[id];
            }
            flag(position, mask);
        }
    }

    /**
     * The type of this update (e.g. adding or removing collision flags).
     */
    private final CollisionUpdateType type;

    /**
     * A mapping of world {@link Position}s to the {@link CollisionFlag} masks that are added to or removed from them.
     * <p>
     * The manager later applies these masks to the appropriate {@link CollisionMatrix}.
     * </p>
     */
    private final ImmutableMap<Position, Integer> flags;

    /**
     * If the positions are in map coordinates, where bridged tiles are stored one level lower.
     */
    private final boolean mapCoordinates;

    /**
     * The tile of the object this update was built for, or {@code null} if it was not built from an object. Objects
     * are lowered by a bridge as a whole, according to this tile, so that a wall is never split across two levels.
     */
    private final Position origin;

    /**
     * Creates a new {@link CollisionUpdate}.
     *
     * @param type The {@link CollisionUpdateType} describing whether collision is being added or removed.
     * @param flags A map of positions to their flag masks.
     * @param mapCoordinates If the positions are in map coordinates.
     * @param origin The tile of the object the update was built for, or {@code null}.
     */
    public CollisionUpdate(CollisionUpdateType type, ImmutableMap<Position, Integer> flags, boolean mapCoordinates,
                           Position origin) {
        this.type = type;
        this.flags = flags;
        this.mapCoordinates = mapCoordinates;
        this.origin = origin;
    }

    /**
     * Returns whether the positions of this update are in map coordinates.
     *
     * @return {@code true} if bridged tiles must be moved down a level when applying this update.
     */
    public boolean isMapCoordinates() {
        return mapCoordinates;
    }

    /**
     * Returns the tile of the object this update was built for.
     *
     * @return The tile of the object, or {@code null} if the update was not built from an object.
     */
    public Position getOrigin() {
        return origin;
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
     * Returns the mapping of tiles to their flag masks.
     *
     * @return A map of positions to {@link CollisionFlag} masks.
     */
    public ImmutableMap<Position, Integer> getFlags() {
        return flags;
    }
}
