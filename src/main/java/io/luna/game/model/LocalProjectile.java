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
 * A {@link LocalEntity} type representing a displayable projectile within the game world.
 *
 * @author lare96
 */
public class LocalProjectile extends LocalEntity {
// TODO finish documenation once tested and all values figured out.

    /**
     * A builder for {@link LocalProjectile} types.
     */
    public static abstract class TargetBuilder {
        private final LunaContext context;
        private int id = -1;
        private int startHeight;
        private int endHeight;
        private int ticksToStart;
        private int ticksToEnd;
        private int initialSlope = 25 * 128 / 180; // TODO number between 0-64
        private ChunkUpdatableView view = ChunkUpdatableView.globalView();

        public TargetBuilder setId(int id) {
            this.id = id;
            return this;
        }

        public TargetBuilder setStartHeight(int startHeight) {
            this.startHeight = startHeight;
            return this;
        }

        public TargetBuilder setEndHeight(int endHeight) {
            this.endHeight = endHeight;
            return this;
        }

        public TargetBuilder setDurationTicks(int durationTicks) {
            int ticksToMs = durationTicks * 600;
            int msToClientTicks = ticksToMs / 20;
            ticksToStart = msToClientTicks;
            ticksToEnd = ticksToStart + msToClientTicks;
            return this;
        }

        public void setInitialSlope(int initialSlope) {
            this.initialSlope = initialSlope * 128 / 180;
        }

        public void setView(ChunkUpdatableView view) {
            this.view = view;
        }

        public abstract Position sourcePosition();

        public abstract Position targetPosition();

        public abstract OptionalInt targetIndex();

        public abstract int distanceFromSource();

        public TargetBuilder(LunaContext context) {
            this.context = context;
        }

        public LocalProjectile build() {
            return new LocalProjectile(context, id, sourcePosition(), targetPosition(), targetIndex(),
                    startHeight, endHeight, ticksToStart, ticksToEnd, initialSlope, distanceFromSource(),
                    view);
        }
    }

    /**
     * An entity based {@link TargetBuilder} type.
     */
    public static class EntityTargetBuilder extends TargetBuilder {
        private Entity sourceEntity;
        private Entity targetEntity;
        private int sourceDistanceOffset;

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
                if (targetMob.getType() == EntityType.PLAYER) {
                    targetIndex = ((Player) targetEntity).getIndex() + 1;
                    targetIndex = -targetIndex;

                } else if (targetMob.getType() == EntityType.NPC) {
                    targetIndex = ((Npc) targetEntity).getIndex() + 1;
                }
                return targetIndex == 0 ? OptionalInt.empty() : OptionalInt.of(targetIndex);
            }
            return OptionalInt.empty();
        }

        @Override
        public int distanceFromSource() {
            return (sourceEntity.size() * 64) + (sourceDistanceOffset * 64); // startdistanceoffset?
        }

        public EntityTargetBuilder setSourceEntity(Entity sourceEntity) {
            this.sourceEntity = sourceEntity;
            return this;
        }

        public EntityTargetBuilder setTargetEntity(Entity targetEntity) {
            this.targetEntity = targetEntity;
            return this;

        }

        public EntityTargetBuilder setSourceDistanceOffset(int sourceDistanceOffset) {
            this.sourceDistanceOffset = sourceDistanceOffset;
            return this;

        }
    }

    /**
     * A position based {@link TargetBuilder} type.
     */
    public static class PositionTargetBuilder extends TargetBuilder {
        private Position sourcePosition;
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
     * Returns a builder to create a new {@link LocalProjectile} following an entity.
     */
    public static EntityTargetBuilder followEntity(LunaContext context) {
        return new EntityTargetBuilder(context);
    }

    /**
     * Returns a builder to create a new {@link LocalProjectile} following a path.
     */
    public static PositionTargetBuilder followPath(LunaContext context) {
        return new PositionTargetBuilder(context);
    }

    /**
     * The destination position.
     */
    private final Position destinationPosition;

    /**
     * The index of the target this projectile will track.
     * <p>
     * A positive index value will track the specified {@link Npc} on the index. A negated index value will track
     * the specified {@link Player} on the unsigned index.
     */
    private final OptionalInt targetIndex;

    /**
     * The start height.
     */
    private final int startHeight;

    /**
     * The end height.
     */
    private final int endHeight;

    /**
     * The amount of ticks before sending the projectile.
     */
    private final int ticksToStart;

    /**
     * The amount of ticks before the projectile will end.
     */
    private final int ticksToEnd;

    /**
     * The angle of the projectile.
     */
    private final int initialSlope;

    /**
     * The distance from the source that the projectile should start.
     */
    private final int distanceFromSource;

    private LocalProjectile(LunaContext context, int id, Position sourcePosition, Position destinationPosition,
                            OptionalInt targetIndex, int startHeight, int endHeight, int ticksToStart, int delay,
                            int initialSlope, int distanceFromSource, ChunkUpdatableView view) {
        super(context, id, EntityType.PROJECTILE, sourcePosition, view);
        this.destinationPosition = destinationPosition;
        this.targetIndex = targetIndex;
        this.startHeight = startHeight;
        this.endHeight = endHeight;
        this.ticksToStart = ticksToStart;
        this.ticksToEnd = delay;
        this.initialSlope = initialSlope;
        this.distanceFromSource = distanceFromSource;
    }

    @Override
    public ChunkUpdatableMessage displayMessage(int offset) {
        int deltaX = (position.getX() - destinationPosition.getX()) * -1;
        int deltaY = (position.getY() - destinationPosition.getY()) * -1;
        return new AddProjectileMessageWriter(id, offset, deltaX, deltaY, targetIndex.orElse(0), startHeight,
                endHeight, ticksToEnd, ticksToStart, initialSlope, distanceFromSource);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("sourcePosition", position)
                .add("destinationPosition", destinationPosition)
                .add("targetIndex", targetIndex)
                .add("startHeight", startHeight)
                .add("endHeight", endHeight)
                .add("speed", ticksToStart)
                .add("delay", ticksToEnd)
                .add("initialSlope", initialSlope)
                .add("distanceFromSource", distanceFromSource)
                .toString();
    }
}
