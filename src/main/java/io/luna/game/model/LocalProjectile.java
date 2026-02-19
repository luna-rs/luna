package io.luna.game.model;

import com.google.common.base.MoreObjects;
import io.luna.LunaContext;
import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.AddProjectileMessageWriter;

import java.util.OptionalInt;

/**
 * A {@link LocalEntity} representing a client-side projectile effect.
 * <p>
 * {@code LocalProjectile} is a temporary, non-interactable entity that exists only to send an
 * {@link AddProjectileMessageWriter} to nearby clients through the chunk update system. After being displayed, it is
 * discarded (it is not a persistent world entity).</p>
 *
 * <h3>Targeting</h3>
 * <ul>
 *   <li><b>Entity-targeted</b>: encodes a client "target index" so the projectile tracks a moving
 *   {@link Player} or {@link Npc}.</li>
 *   <li><b>Tile-targeted</b>: omits the target index and travels to a fixed destination tile using
 *   {@code deltaX}/{@code deltaY}.</li>
 * </ul>
 *
 * @author lare96
 */
public class LocalProjectile extends LocalEntity {

    /**
     * Builder base class for {@link LocalProjectile}.
     * <p>
     * Subclasses provide the source/target positions and (optionally) a client target index for tracking.
     */
    public static abstract class TargetBuilder {

        /**
         * The context used to construct the projectile entity.
         */
        private final LunaContext context;

        /**
         * Projectile graphic id.
         */
        private int id = -1;

        /**
         * Starting height (client units).
         */
        private int startHeight;

        /**
         * Ending height (client units).
         */
        private int endHeight;

        /**
         * Client ticks (20ms) before the projectile begins.
         */
        private int ticksToStart;

        /**
         * Client ticks (20ms) until the projectile ends (duration / travel time).
         */
        private int ticksToEnd;

        /**
         * Initial slope/angle parameter controlling the arc on the client.
         */
        private int initialSlope = 16; // historically 16–18 is common

        /**
         * Who the projectile is visible to.
         */
        private ChunkUpdatableView view = ChunkUpdatableView.globalView();

        /**
         * Sets the projectile graphic id.
         *
         * @param id The projectile id.
         */
        public TargetBuilder setId(int id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the starting height (client units).
         *
         * @param startHeight The start height.
         */
        public TargetBuilder setStartHeight(int startHeight) {
            this.startHeight = startHeight;
            return this;
        }

        /**
         * Sets the ending height (client units).
         *
         * @param endHeight The end height.
         */
        public TargetBuilder setEndHeight(int endHeight) {
            this.endHeight = endHeight;
            return this;
        }

        /**
         * Sets the delay before the projectile begins, in <em>client ticks</em> (20ms).
         *
         * @param ticksToStart Delay in client ticks.
         */
        public TargetBuilder setTicksToStart(int ticksToStart) {
            this.ticksToStart = ticksToStart;
            return this;
        }

        /**
         * Sets the projectile duration/travel time, in <em>client ticks</em> (20ms).
         *
         * @param ticksToEnd Duration in client ticks.
         */
        public TargetBuilder setTicksToEnd(int ticksToEnd) {
            this.ticksToEnd = ticksToEnd;
            return this;
        }

        /**
         * Convenience method to set a duration expressed in <em>server ticks</em> (600ms) and convert it to client ticks.
         * <p>
         * This sets {@code ticksToStart = 0} and computes {@code ticksToEnd}.
         *
         * @param durationTicks Duration in server ticks.
         */
        public TargetBuilder setDurationTicks(int durationTicks) {
            int ticksToMs = durationTicks * 600;
            int msToClientTicks = ticksToMs / 20;
            ticksToStart = 0;
            ticksToEnd = msToClientTicks;
            return this;
        }

        /**
         * Sets the initial slope/angle parameter controlling projectile arc shape.
         *
         * @param initialSlope The slope value.
         */
        public TargetBuilder setInitialSlope(int initialSlope) {
            this.initialSlope = initialSlope;
            return this;
        }

        /**
         * Sets the visibility view for this projectile.
         *
         * @param view Who can see this projectile.
         */
        public void setView(ChunkUpdatableView view) {
            this.view = view;
        }

        /**
         * @return The projectile source position.
         */
        public abstract Position sourcePosition();

        /**
         * @return The projectile target position (tile), even if entity-tracked.
         */
        public abstract Position targetPosition();

        /**
         * @return Optional client target index if this projectile tracks a mob.
         */
        public abstract OptionalInt targetIndex();

        /**
         * @return The "distance from source" parameter expected by the client projectile packet.
         */
        public abstract int distanceFromSource();

        /**
         * Creates a new {@link TargetBuilder}.
         *
         * @param context The context.
         */
        public TargetBuilder(LunaContext context) {
            this.context = context;
        }

        /**
         * Builds a new {@link LocalProjectile}.
         *
         * @return The constructed projectile.
         */
        public LocalProjectile build() {
            return new LocalProjectile(context, id, sourcePosition(), targetPosition(), targetIndex(),
                    startHeight, endHeight, ticksToStart, ticksToEnd, initialSlope, distanceFromSource(),
                    view);
        }
    }

    /**
     * A {@link TargetBuilder} that targets another {@link Entity}.
     * <p>
     * If the target is a {@link Mob}, a client "target index" is encoded so the projectile will track the entity as
     * it moves.</p>
     */
    public static class EntityTargetBuilder extends TargetBuilder {

        /**
         * The entity the projectile starts from.
         */
        private Entity sourceEntity;

        /**
         * The entity being targeted.
         */
        private Entity targetEntity;

        public EntityTargetBuilder(LunaContext context) {
            super(context);
        }

        @Override
        public Position sourcePosition() {
            return sourceEntity.getPosition();
        }

        @Override
        public Position targetPosition() {
            return targetEntity.getPosition();
        }

        @Override
        public OptionalInt targetIndex() {
            if (targetEntity instanceof Mob) {
                int targetIndex = 0;
                Mob targetMob = (Mob) targetEntity;

                // Client convention:
                // - NPC target: + (npcIndex + 1)
                // - Player target: - (playerIndex + 1)
                if (targetMob.getType() == EntityType.PLAYER) {
                    targetIndex = -((Player) targetEntity).getIndex() - 1;
                } else if (targetMob.getType() == EntityType.NPC) {
                    targetIndex = ((Npc) targetEntity).getIndex() + 1;
                }
                return targetIndex == 0 ? OptionalInt.empty() : OptionalInt.of(targetIndex);
            }
            return OptionalInt.empty();
        }

        @Override
        public int distanceFromSource() {
            return (sourceEntity.size() * 64);
        }

        /**
         * Sets the source entity.
         *
         * @param sourceEntity The source entity.
         */
        public EntityTargetBuilder setSourceEntity(Entity sourceEntity) {
            this.sourceEntity = sourceEntity;
            return this;
        }

        /**
         * Sets the target entity.
         *
         * @param targetEntity The target entity.
         */
        public EntityTargetBuilder setTargetEntity(Entity targetEntity) {
            this.targetEntity = targetEntity;
            return this;
        }
    }

    /**
     * A {@link TargetBuilder} that targets a fixed destination tile.
     * <p>
     * Tile-targeted projectiles do not encode a client target index, so the projectile travels purely using
     * {@code deltaX}/{@code deltaY} relative to the source tile.
     */
    public static class PositionTargetBuilder extends TargetBuilder {

        /**
         * The projectile source position.
         */
        private Position sourcePosition;

        /**
         * The projectile destination position.
         */
        private Position targetPosition;

        public PositionTargetBuilder(LunaContext context) {
            super(context);
        }

        public PositionTargetBuilder setSourcePosition(Position sourcePosition) {
            this.sourcePosition = sourcePosition;
            return this;
        }

        public PositionTargetBuilder setTargetPosition(Position targetPosition) {
            this.targetPosition = targetPosition;
            return this;
        }

        @Override
        public Position sourcePosition() {
            return sourcePosition;
        }

        @Override
        public Position targetPosition() {
            return targetPosition;
        }

        @Override
        public OptionalInt targetIndex() {
            return OptionalInt.empty();
        }

        @Override
        public int distanceFromSource() {
            return 64;
        }
    }

    /**
     * Creates a builder for an entity-tracking projectile.
     *
     * @param context The context.
     * @return A new builder instance.
     */
    public static EntityTargetBuilder followEntity(LunaContext context) {
        return new EntityTargetBuilder(context);
    }

    /**
     * Creates a builder for a tile-targeted projectile.
     *
     * @param context The context.
     * @return A new builder instance.
     */
    public static PositionTargetBuilder followPath(LunaContext context) {
        return new PositionTargetBuilder(context);
    }

    /**
     * The destination position (tile).
     */
    private final Position destination;

    /**
     * The client target index used for entity tracking, if any.
     * <p>
     * A positive index tracks the specified {@link Npc} ({@code npcIndex + 1}). A negative index tracks the specified
     * {@link Player} ({@code -(playerIndex + 1)}). A value of {@code 0} means no tracking.
     */
    private final OptionalInt targetIndex;

    /**
     * The start height (client units).
     */
    private final int startHeight;

    /**
     * The end height (client units).
     */
    private final int endHeight;

    /**
     * The delay before sending/starting the projectile, in client ticks (20ms).
     */
    private final int ticksToStart;

    /**
     * The projectile duration/travel time, in client ticks (20ms).
     */
    private final int ticksToEnd;

    /**
     * The initial slope/angle controlling arc shape.
     */
    private final int initialSlope;

    /**
     * The distance-from-source parameter expected by the client projectile packet.
     */
    private final int distanceFromSource;

    /**
     * Creates a new {@link LocalProjectile}.
     *
     * @param context The context.
     * @param id The projectile graphic id.
     * @param start The source tile.
     * @param destination The destination tile.
     * @param targetIndex The optional client target index for entity tracking.
     * @param startHeight The starting height.
     * @param endHeight The ending height.
     * @param ticksToStart Delay before start (client ticks).
     * @param ticksToEnd Duration/travel time (client ticks).
     * @param initialSlope Arc slope/angle.
     * @param distanceFromSource Distance-from-source parameter.
     * @param view Who can see this projectile.
     */
    private LocalProjectile(LunaContext context, int id, Position start, Position destination,
                            OptionalInt targetIndex, int startHeight, int endHeight, int ticksToStart, int ticksToEnd,
                            int initialSlope, int distanceFromSource, ChunkUpdatableView view) {
        super(context, id, EntityType.PROJECTILE, start, view);
        this.destination = destination;
        this.targetIndex = targetIndex;
        this.startHeight = startHeight;
        this.endHeight = endHeight;
        this.ticksToStart = ticksToStart;
        this.ticksToEnd = ticksToEnd;
        this.initialSlope = initialSlope;
        this.distanceFromSource = distanceFromSource;
    }

    @Override
    public ChunkUpdatableMessage displayMessage(int offset) {
        int deltaX = destination.getX() - position.getX();
        int deltaY = destination.getY() - position.getY();
        return new AddProjectileMessageWriter(
                id,
                offset,
                deltaX,
                deltaY,
                targetIndex.orElse(0),
                startHeight,
                endHeight,
                ticksToEnd,
                ticksToStart,
                initialSlope,
                distanceFromSource
        );
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("sourcePosition", position)
                .add("destinationPosition", destination)
                .add("targetIndex", targetIndex)
                .add("startHeight", startHeight)
                .add("endHeight", endHeight)
                .add("ticksToStart", ticksToStart)
                .add("ticksToEnd", ticksToEnd)
                .add("initialSlope", initialSlope)
                .add("distanceFromSource", distanceFromSource)
                .toString();
    }
}
