package io.luna.game.model.mob;

import com.google.common.collect.ImmutableSet;
import game.player.Sounds;
import io.luna.LunaContext;
import io.luna.game.action.Action;
import io.luna.game.action.ActionQueue;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.block.Graphic;
import io.luna.game.model.mob.block.Hit;
import io.luna.game.model.mob.block.Hit.HitType;
import io.luna.game.model.mob.block.UpdateBlockData;
import io.luna.game.model.mob.block.UpdateBlockData.Builder;
import io.luna.game.model.mob.block.UpdateFlagSet;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.task.Task;
import io.luna.game.task.TaskState;
import io.luna.net.codec.ByteMessage;
import io.netty.buffer.ByteBuf;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.luna.game.model.mob.Skill.HITPOINTS;

/**
 * Base type for interactable, movable entities in the world (players and NPCs).
 *
 * @author lare96
 */
public abstract class Mob extends Entity {

    /**
     * The set of update flags indicating which update blocks need to be encoded for this mob.
     * <p>
     * This is tick-local state and must only be mutated from the game thread.
     * </p>
     */
    protected final UpdateFlagSet flags = new UpdateFlagSet();

    /**
     * The skill set backing this mob's levels and experience.
     */
    protected final SkillSet skills = new SkillSet(this);

    /**
     * The queue of active and pending actions for this mob.
     */
    protected final ActionQueue actions = new ActionQueue(this);

    /**
     * The movement queue used for walking and running steps.
     */
    protected final WalkingQueue walking = new WalkingQueue(this);

    /**
     * The movement navigator used for routing and generating paths.
     */
    protected final WalkingNavigator navigator = new WalkingNavigator(this);

    /**
     * Index into the global {@link MobList}. {@code -1} indicates "not registered".
     */
    private int index = -1;

    /**
     * The current walking direction for this tick (or {@link Direction#NONE} if stationary).
     */
    private Direction walkingDirection = Direction.NONE;

    /**
     * The current running direction for this tick (or {@link Direction#NONE} if not running).
     */
    private Direction runningDirection = Direction.NONE;

    /**
     * The last non-{@link Direction#NONE} movement direction.
     * <p>
     * Used as a default facing direction (e.g., for idle NPC orientation).
     * </p>
     */
    private Direction lastDirection = Direction.SOUTH;

    /**
     * The entity this mob is currently interacting with (target), if any.
     */
    private Entity interactingWith;

    /**
     * Cached player view of this mob when {@link #type} is {@link EntityType#PLAYER}. May be {@code null}.
     */
    private Player playerInstance;

    /**
     * Cached NPC view of this mob when {@link #type} is {@link EntityType#NPC}. May be {@code null}.
     */
    private Npc npcInstance;

    /**
     * {@code true} if this mob is currently action-locked.
     * <p>
     * While locked, its {@link ActionQueue} and {@link WalkingQueue} should be treated as suspended by higher-level
     * logic.
     * </p>
     */
    private boolean locked;

    /**
     * {@code true} if this mob must be re-placed on the client this tick (i.e., encode a placement/teleport-style
     * movement update instead of normal walking/running).
     */
    protected boolean pendingPlacement;

    /**
     * Pending, mutable update data for this tick.
     * <p>
     * This must only be accessed and mutated from the game thread. Once all per-tick mutations are complete,
     * {@link #buildBlockData()} must be called to publish an immutable snapshot for the encoder threads.
     * </p>
     */
    protected UpdateBlockData.Builder pendingBlockData = new UpdateBlockData.Builder(this);

    /**
     * Immutable update data for this tick.
     * <p>
     * This is published from the game thread by {@link #buildBlockData()} and may then be read safely from any thread
     * (e.g., by the player/NPC update encoders).
     * </p>
     */
    private volatile UpdateBlockData blockData;

    /**
     * Immutable snapshot of update flags for this tick.
     * <p>
     * This is published from the game thread by {@link #buildBlockData()} and may then be read safely from any thread
     * (e.g., by the player/NPC update encoders).
     * </p>
     */
    private volatile ImmutableSet<UpdateFlag> flagData;

    /**
     * Cached encoded update block for this mob (per tick), used to avoid re-encoding the same block for multiple
     * observers.
     * <p>
     * The underlying {@link ByteBuf} is reference-counted; see {@link #acquireCachedBlock()},
     * {@link #cacheBlockIfAbsent(ByteMessage)}, and {@link #clearCachedBlock()} for semantics.
     * </p>
     */
    private final AtomicReference<ByteBuf> cachedBlock = new AtomicReference<>();

    /**
     * The current transform id to apply for this mob, or {@code -1} if not transformed.
     * <p>
     * The actual interpretation of this value is defined by subclasses in {@link #transform(int)} /
     * {@link #resetTransform()}.
     * </p>
     */
    protected int transformId = -1;

    /**
     * The scheduled task controlling a timed action-lock, if any.
     */
    private Task lockTask;

    /**
     * Creates a new {@link Mob} at a specific starting position.
     *
     * @param context The game context.
     * @param position The initial world position.
     * @param type The entity type of this mob.
     */
    public Mob(LunaContext context, Position position, EntityType type) {
        super(context, position, type);
    }

    /**
     * Creates a new {@link Mob} without an initial position.
     *
     * @param context The game context.
     * @param type The entity type of this mob.
     */
    public Mob(LunaContext context, EntityType type) {
        super(context, type);
    }

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    /**
     * Resets all mob-specific state for the next tick.
     * <p>
     * This is called from {@link #resetFlags()} after a new {@link UpdateBlockData.Builder} is created and
     * {@link #flags} have been cleared, but before the next game tick begins.
     * </p>
     * <p>
     * Implementations should clear or re-initialize any per-tick state that is not already handled by {@link Mob}.
     * </p>
     */
    public abstract void reset();

    /**
     * Transforms this mob into an NPC with the given id.
     *
     * @param requestedId The NPC definition id to transform into.
     */
    public abstract void transform(int requestedId);

    /**
     * Reverts any active transform, restoring this mob to its original form.
     */
    public abstract void resetTransform();

    /**
     * Returns this mob's combat level.
     *
     * @return The combat level.
     */
    public abstract int getCombatLevel();

    /**
     * Returns this mob's maximum hitpoints.
     *
     * @return The total health of this mob.
     */
    public abstract int getTotalHealth();

    /**
     * Builds and publishes the immutable {@link UpdateBlockData} snapshot for this tick.
     * <p>
     * Threading/ordering requirements:
     * </p>
     * <ul>
     *     <li>Must be called from the game thread only.</li>
     *     <li>Must be called once all game logic for the tick has finished mutating {@link #pendingBlockData} and
     *     {@link #flags}.</li>
     *     <li>All online mobs should have {@code buildBlockData()} invoked before the updating stage begins.</li>
     * </ul>
     * <p>
     * The snapshot is only rebuilt if no previous snapshot exists, or if any update flags are currently set. This
     * avoids unnecessary object creation when there are no updates to send.
     * </p>
     */
    public final void buildBlockData() {
        flagData = flags.snapshot();
        if (blockData == null || !flags.isEmpty()) {
            // Only build block data when actually needed.
            blockData = pendingBlockData.build();
        }
    }


    /**
     * Acquires a safe, read-only view of the cached encoded update block for this mob.
     * <p>
     * If a cached block is present, this method returns a {@link ByteBuf} view created via
     * {@link ByteBuf#retainedDuplicate()}. This has two important properties:
     * </p>
     * <ul>
     *     <li><b>Pins lifetime:</b> the returned buffer's reference count is incremented, preventing it from being
     *     freed while the caller is using it.</li>
     *     <li><b>Independent indices:</b> the returned buffer has its own reader/writer indices, so copying from it
     *     won't interfere with other readers.</li>
     * </ul>
     * <p>
     * The returned buffer must be treated as <b>read-only</b>. Callers must always release it when finished:
     * </p>
     *
     * <pre>{@code
     * ByteBuf buf = mob.acquireCachedBlock();
     * if (buf != null) {
     *     try {
     *         // copy from buf
     *     } finally {
     *         buf.release();
     *     }
     * }
     * }</pre>
     *
     * @return A retained duplicate of the cached update block, or {@code null} if none is cached.
     */
    public ByteBuf acquireCachedBlock() {
        ByteBuf buf = cachedBlock.get();
        return buf == null ? null : buf.retainedDuplicate();
    }

    /**
     * Attempts to publish an encoded update block into this mob's per-tick cache.
     * <p>
     * This method uses a compare-and-set (CAS) strategy so that the first thread to compute an update block
     * for this mob during the current tick "wins", and all other threads reuse the already-cached block.
     * </p>
     * <p>
     * The cached instance is stored as a {@link ByteBuf} created via {@link ByteBuf#retainedDuplicate()} from
     * {@code msg}'s underlying buffer. This avoids copying bytes while ensuring the cached buffer:
     * </p>
     * <ul>
     *     <li>has an incremented reference count for safe sharing, and</li>
     *     <li>has independent indices so readers don't interfere with one another.</li>
     * </ul>
     * <p>
     * If caching fails because another thread already published a block, the duplicate created by this method
     * is released before returning.
     * </p>
     *
     * @param msg The newly encoded update block to cache.
     * @return {@code true} if the block was cached by this call, or {@code false} if a block was already cached.
     */
    public boolean cacheBlockIfAbsent(ByteMessage msg) {
        ByteBuf cachedMsg = msg.getBuffer().retainedDuplicate();
        if (cachedBlock.compareAndSet(null, cachedMsg)) {
            return true;
        }
        cachedMsg.release();
        return false;
    }

    /**
     * Clears the cached update block for this mob.
     * <p>
     * This is intended to be called once per tick (typically during post-synchronization) after all update writers
     * have finished reading the cached block. If a cached block is present, this method releases the mob's cached
     * reference.
     * </p>
     * <p>
     * Callers must ensure they do not clear the cache while other threads may still be using a buffer acquired via
     * {@link #acquireCachedBlock()}.
     * </p>
     */
    public void clearCachedBlock() {
        ByteBuf old = cachedBlock.getAndSet(null);
        if (old != null) {
            old.release();
        }
    }

    /**
     * Returns the current hitpoints of this mob.
     *
     * @return The current hitpoints level.
     */
    public final int getHealth() {
        return skill(HITPOINTS).getLevel();
    }

    /**
     * Sets the current hitpoints of this mob, clamping the value to {@code >= 0}, and scheduling a
     * {@link MobDeathTask} if this call transitions the mob from alive to dead.
     *
     * @param amount The new hitpoint value.
     */
    public final void setHealth(int amount) {
        Skill hp = skill(HITPOINTS);
        int levelBefore = hp.getLevel();
        hp.setLevel(Math.max(amount, 0));
        if (levelBefore > 0 && hp.getLevel() <= 0) {
            // TODO Determine the true killer/source after combat system is implemented.
            Mob source = null;
            world.schedule(new MobDeathTask(this, source));
        }
    }

    /**
     * Adds or subtracts {@code amount} from the current hitpoints.
     *
     * @param amount The amount to add (positive) or subtract (negative).
     */
    public final void addHealth(int amount) {
        setHealth(getHealth() + amount);
    }

    /**
     * Convenience method that submits an {@link Action} to this mob's {@link ActionQueue}.
     *
     * @param pending The action to submit.
     */
    public final void submitAction(Action<? extends Mob> pending) {
        actions.submit(pending);
    }

    /**
     * Action-locks this mob for the specified number of ticks.
     * <p>
     * Equivalent to {@link #lock(int, Runnable)} with an empty {@code onUnlock} callback.
     * </p>
     *
     * @param ticks The number of ticks to remain locked.
     */
    public void lock(int ticks) {
        lock(ticks, () -> {
        });
    }

    /**
     * Action-locks this mob for the specified number of ticks and runs {@code onUnlock} when the lock expires.
     * <p>
     * Lock semantics:
     * </p>
     * <ul>
     *     <li>If the mob is already {@link #locked}, this call attempts to extend the existing timed lock if the new
     *     {@code ticks} duration exceeds the remaining time on {@link #lockTask}.</li>
     *     <li>If no lock task is running, a new {@link Task} is scheduled to unlock the mob after {@code ticks}.</li>
     *     <li>This timed lock is distinct from a "hard" lock created via {@link #lock()}.</li>
     * </ul>
     *
     * @param ticks The number of ticks to remain locked.
     * @param onUnlock A callback executed when the lock naturally expires.
     */
    public void lock(int ticks, Runnable onUnlock) {
        if (!locked) {
            if (lockTask != null && lockTask.getState() == TaskState.RUNNING) {
                int elapsed = lockTask.getExecutionCounter();
                int remaining = lockTask.getDelay() - elapsed;
                if (ticks > remaining) {
                    lockTask.setDelay(ticks - elapsed);
                }
            } else {
                locked = true;
                lockTask = new Task(ticks) {
                    @Override
                    protected void execute() {
                        locked = false;
                        onUnlock.run();
                        cancel();
                    }
                };
                world.schedule(lockTask);
            }
        }
    }

    /**
     * Action-locks this mob indefinitely.
     * <p>
     * This cancels any outstanding timed lock task and permanently sets {@link #locked} to {@code true}.
     * <strong>{@link #unlock()} must be called at some point or the player will not be able to perform any action,
     * including logout.</strong>
     * </p>
     */
    public void lock() {
        if (lockTask != null) {
            // Cancel timed lock so it does not interfere with hard lock semantics.
            lockTask.cancel();
        }
        locked = true;
    }

    /**
     * Removes any action-lock on this mob.
     * <p>
     * Cancels any outstanding timed lock task and sets {@link #locked} to {@code false}.
     * </p>
     */
    public void unlock() {
        if (lockTask != null) {
            lockTask.cancel();
        }
        locked = false;
    }

    /**
     * Applies a single hit to this mob with an explicit {@link HitType}.
     * <p>
     * This method:
     * </p>
     * <ul>
     *     <li>Normalizes negative damage to zero.</li>
     *     <li>Converts {@link HitType#BLOCKED} to {@link HitType#NORMAL} if damage is non-zero.</li>
     *     <li>Reduces this mob's hitpoints via {@link #addHealth(int)} / {@link #setHealth(int)}.</li>
     *     <li>Builds and enqueues a {@link Hit} into the first or second hitsplat slot for this tick.</li>
     *     <li>Triggers basic player damage/block sounds when this mob is a {@link Player}.</li>
     * </ul>
     *
     * @param amount The damage amount (negative values will be treated as zero).
     * @param type The hit type to use.
     */
    public final void damage(int amount, HitType type) {
        if (amount < 0) {
            amount = 0;
        }
        if (amount == 0) {
            type = HitType.BLOCKED;
        } else if (type == HitType.BLOCKED) {
            type = HitType.NORMAL;
        }

        // Apply damage to hitpoints (clamped to >= 0).
        if (getHealth() - amount < 0) {
            setHealth(0);
        } else {
            addHealth(-amount);
        }

        Hit hit = new Hit(amount, type, getHealth(), getTotalHealth());
        if (pendingBlockData.getHit1() != null) {
            hit2(hit);
        } else {
            hit1(hit);
        }

        if (this instanceof Player) {
            if (hit.getDamage() > 0) {
                // TODO Refine per-hit sound selection once a proper combat sound system is implemented.
                asPlr().playRandomSound(
                        Sounds.TAKE_DAMAGE,
                        Sounds.TAKE_DAMAGE_2,
                        Sounds.TAKE_DAMAGE_3,
                        Sounds.TAKE_DAMAGE_4
                );
            } else {
                asPlr().playSound(Sounds.UNARMED_BLOCK);
            }
        }
    }

    /**
     * Applies a single normal hit to this mob.
     * <p>
     * Convenience overload for {@link #damage(int, HitType)} using {@link HitType#NORMAL}.
     * </p>
     *
     * @param amount The damage amount.
     */
    public void damage(int amount) {
        damage(amount, HitType.NORMAL);
    }

    /**
     * Immediately moves this mob to {@code position}, clearing movement and interactions.
     *
     * @param position The destination position.
     */
    public final void move(Position position) {
        setPosition(position);
        walking.clear();
        resetInteractingWith();
        pendingPlacement = true;
        onMove(position);
    }

    /**
     * Attempts to play {@code animation} on this mob.
     * <p>
     * If the currently pending animation allows overrides (according to {@link Animation#overrides(Animation)}),
     * it will be replaced by the new one; otherwise the call has no effect.
     * </p>
     *
     * @param animation The animation to perform.
     */
    public final void animation(Animation animation) {
        Animation current = pendingBlockData.getAnimation();
        if (current == null || animation.overrides(current)) {
            pendingBlockData.animation(animation);
        }
    }

    /**
     * Faces this mob toward a specific {@link Position}.
     *
     * @param position The world position to face.
     */
    public final void face(Position position) {
        pendingBlockData.face(position);
    }

    /**
     * Faces this mob toward the given {@link Direction} from its current position.
     *
     * @param direction The direction to face.
     */
    public final void face(Direction direction) {
        int x = direction.getTranslateX();
        int y = direction.getTranslateY();
        face(position.translate(x, y));
    }

    /**
     * Forces this mob to say {@code message} as chat.
     *
     * @param message The message to display (converted via {@link Object#toString()}).
     */
    public final void speak(Object message) {
        pendingBlockData.speak(message.toString());
    }

    /**
     * Plays the specified {@link Graphic} on this mob.
     *
     * @param graphic The graphic to display.
     */
    public final void graphic(Graphic graphic) {
        pendingBlockData.graphic(graphic);
    }

    /**
     * Sets this mob's interaction target to {@code entity}.
     * <p>
     * Behavior:
     * </p>
     * <ul>
     *     <li>If {@code entity} is {@code null}, the interaction index is reset (65535).</li>
     *     <li>If {@code entity} is a {@link Mob}, the interaction index is encoded according to the protocol
     *     (players use {@code index + 32768}, NPCs use {@code index}).</li>
     *     <li>For non-mob entities, this mob simply turns to face the target position.</li>
     * </ul>
     *
     * @param entity The new interaction target, or {@code null} to clear.
     */
    public final void interact(Entity entity) {
        if (entity == null) {
            pendingBlockData.interact(65535);
            flags.flag(UpdateFlag.INTERACTION);
        } else if (entity instanceof Mob) {
            Mob mob = (Mob) entity;
            pendingBlockData.interact(mob.type == EntityType.PLAYER ? mob.index + 32768 : mob.index);
            flags.flag(UpdateFlag.INTERACTION);
        } else {
            face(entity.getPosition());
        }
        interactingWith = entity;
    }

    /**
     * Clears the current interaction target, if one is set.
     */
    public final void resetInteractingWith() {
        if (interactingWith != null) {
            interact(null);
        }
    }

    /**
     * Returns whether this mob is currently interacting with {@code entity}.
     *
     * @param entity The entity to test.
     * @return {@code true} if the current interaction target equals {@code entity}.
     */
    public boolean isInteractingWith(Entity entity) {
        return Objects.equals(interactingWith, entity);
    }

    /**
     * Displays a primary hitsplat for this tick.
     *
     * @param hit The hit data to display.
     */
    private void hit1(Hit hit) {
        pendingBlockData.hit1(hit);
    }

    /**
     * Displays a secondary hitsplat for this tick.
     *
     * @param hit The hit data to display.
     */
    private void hit2(Hit hit) {
        pendingBlockData.hit2(hit);
    }

    /**
     * Returns this mob's index in the global {@link MobList}.
     *
     * @return The list index, or {@code -1} if not registered.
     */
    public final int getIndex() {
        return index;
    }

    /**
     * Sets this mob's index in the global {@link MobList}.
     * <p>
     * This should only be invoked by the owning {@link MobList} implementation.
     * </p>
     *
     * @param index The new index, or {@code -1} to clear.
     */
    public final void setIndex(int index) {
        if (index != -1) {
            checkArgument(index >= 1, "index < 1");
        }
        this.index = index;
    }

    /**
     * Resets update-related state for the next tick.
     */
    public final void resetFlags() {
        Direction defaultDirection = this instanceof Npc ? asNpc().getDefaultDirection().orElse(null) : null;
        pendingPlacement = false;
        pendingBlockData = new UpdateBlockData.Builder(this);
        if (defaultDirection != null) {
            pendingBlockData.face(position.translate(1, defaultDirection));
        }
        flags.clear();
        if (defaultDirection != null) {
            flags.flag(UpdateFlag.FACE_POSITION);
        }
        reset();
    }

    /**
     * Retrieves the {@link Skill} with the given id.
     *
     * @param id The skill identifier (see {@link Skill} constants).
     * @return The skill instance.
     */
    public Skill skill(int id) {
        return skills.getSkill(id);
    }

    /**
     * Returns whether this mob is currently alive.
     *
     * @return {@code true} if hitpoints are greater than zero.
     */
    public boolean isAlive() {
        return skill(HITPOINTS).getLevel() > 0;
    }

    /**
     * Returns this mob as a {@link Player}.
     *
     * @return The underlying player instance.
     * @throws IllegalStateException If this mob is not a player.
     */
    public Player asPlr() {
        checkState(type == EntityType.PLAYER, "Mob instance is not a Player.");
        if (playerInstance == null) {
            playerInstance = (Player) this;
        }
        return playerInstance;
    }

    /**
     * Returns this mob as an {@link Npc}.
     *
     * @return The underlying NPC instance.
     * @throws IllegalStateException If this mob is not an NPC.
     */
    public Npc asNpc() {
        checkState(type == EntityType.NPC, "Mob instance is not a Npc.");
        if (npcInstance == null) {
            npcInstance = (Npc) this;
        }
        return npcInstance;
    }

    /**
     * Hook invoked after this mob is moved by {@link #move(Position)}.
     * <p>
     * Subclasses may override this to perform any post-move logic, such as region checks, interface updates, or
     * script callbacks. The default implementation is a no-op.
     * </p>
     *
     * @param newPosition The destination position.
     */
    public void onMove(Position newPosition) {
        // Default no-op.
    }

    /**
     * Returns this mob's {@link UpdateFlagSet}.
     *
     * @return The update flag set.
     */
    public final UpdateFlagSet getFlags() {
        return flags;
    }

    /**
     * Returns the current walking direction for this tick.
     *
     * @return The walking direction, or {@link Direction#NONE}.
     */
    public final Direction getWalkingDirection() {
        return walkingDirection;
    }

    /**
     * Updates the current walking direction for this tick.
     *
     * @param walkingDirection The new walking direction.
     */
    public final void setWalkingDirection(Direction walkingDirection) {
        this.walkingDirection = walkingDirection;
    }

    /**
     * Returns the current running direction for this tick.
     *
     * @return The running direction, or {@link Direction#NONE}.
     */
    public Direction getRunningDirection() {
        return runningDirection;
    }

    /**
     * Updates the current running direction for this tick.
     *
     * @param runningDirection The new running direction.
     */
    public void setRunningDirection(Direction runningDirection) {
        this.runningDirection = runningDirection;
    }

    /**
     * Returns the last non-{@link Direction#NONE} movement direction.
     *
     * @return The last movement direction.
     */
    public Direction getLastDirection() {
        return lastDirection;
    }

    /**
     * Updates the last movement direction.
     *
     * @param lastDirection The new last direction value.
     */
    public void setLastDirection(Direction lastDirection) {
        this.lastDirection = lastDirection;
    }

    /**
     * Returns this mob's {@link WalkingQueue}.
     *
     * @return The walking queue instance.
     */
    public final WalkingQueue getWalking() {
        return walking;
    }

    /**
     * @return The movement navigator used for routing and generating paths.
     */
    public WalkingNavigator getNavigator() {
        return navigator;
    }

    /**
     * Returns this mob's {@link SkillSet}.
     *
     * @return The skill set instance.
     */
    public final SkillSet getSkills() {
        return skills;
    }

    /**
     * Returns this mob's {@link ActionQueue}.
     *
     * @return The action queue instance.
     */
    public ActionQueue getActions() {
        return actions;
    }

    /**
     * Returns whether this mob is currently action-locked.
     *
     * @return {@code true} if locked, otherwise {@code false}.
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * Returns this mob's plane ({@code z}) coordinate.
     *
     * @return The z-level.
     */
    public int getZ() {
        return position.getZ();
    }

    /**
     * Returns whether a teleport is currently in progress for this tick.
     *
     * @return {@code true} if teleporting, otherwise {@code false}.
     */
    public boolean isPendingPlacement() {
        return pendingPlacement;
    }

    /**
     * Returns the entity this mob is currently interacting with, if any.
     *
     * @return The interaction target, or {@code null} if none.
     */
    public Entity getInteractingWith() {
        return interactingWith;
    }

    /**
     * Returns the immutable {@link UpdateBlockData} built for this tick.
     *
     * @return The current block data snapshot, or {@code null} if {@link #buildBlockData()} has not yet been called.
     */
    public UpdateBlockData getBlockData() {
        return blockData;
    }

    /**
     * Returns the immutable snapshot of {@link UpdateFlagSet} built for this tick.
     *
     * @return The current flag data snapshot.
     */
    public ImmutableSet<UpdateFlag> getFlagData() {
        return flagData;
    }

    /**
     * Returns the mutable builder used to accumulate this tick's update block data.
     * <p>
     * This is intended for use on the game thread only; encoder threads should read from {@link #getBlockData()} instead.
     * </p>
     *
     * @return The pending block data builder.
     */
    public Builder getPendingBlockData() {
        return pendingBlockData;
    }

    /**
     * Returns the current transform id to apply for this mob, or {@code -1} if no transform is active.
     *
     * @return The transform id, or {@code -1} if none.
     */
    public int getTransformId() {
        return transformId;
    }
}
