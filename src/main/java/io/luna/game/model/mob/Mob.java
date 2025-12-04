package io.luna.game.model.mob;

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
import io.luna.game.model.mob.block.UpdateBlockData;
import io.luna.game.model.mob.block.UpdateFlagSet;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.task.Task;
import io.luna.game.task.TaskState;

import java.util.Objects;

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
     * The update flag set representing which update blocks must be sent to nearby players.
     */
    protected final UpdateFlagSet flags = new UpdateFlagSet();

    /**
     * The skill set backing this mob's levels and experience.
     */
    protected final SkillSet skills = new SkillSet(this);

    /**
     * The actions currently running for this mob.
     */
    protected final ActionQueue actions = new ActionQueue(this);

    /**
     * The movement queue used for walking and running.
     */
    protected final WalkingQueue walking = new WalkingQueue(this);

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
     * The last non-{@link Direction#NONE} movement direction. Used for facing/orientation defaults.
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
     */
    private boolean locked;

    /**
     * {@code true} while a teleport is in progress for this tick.
     */
    protected boolean teleporting;

    /**
     * Pending, mutable update data for this tick.
     * <p>
     * This must only be accessed and mutated from the game thread. After all mutations, {@link #buildBlockData()} must
     * be called to publish an immutable snapshot.
     * </p>
     */
    protected UpdateBlockData.Builder pendingBlockData = new UpdateBlockData.Builder();

    /**
     * Immutable, read-only update data for this tick.
     * <p>
     * This field is published by {@link #buildBlockData()} and may be safely read from any thread (e.g., update
     * encoder threads).
     * </p>
     */
    private volatile UpdateBlockData blockData;

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
     * This is called from {@link #resetFlags()}, after a new {@link UpdateBlockData.Builder} is created and the
     * flags have been cleared, but before the next tick begins.
     * </p>
     *
     * @param oldBlockData The previous tick's block data (before reset), for reference if needed.
     */
    public abstract void reset(UpdateBlockData.Builder oldBlockData);

    /**
     * Transforms this mob into an NPC with the given id.
     *
     * @param id The NPC definition id to transform into.
     */
    public abstract void transform(int id);

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
     * <strong>Threading/ordering requirements:</strong>
     * </p>
     * <ul>
     *     <li>Must be called from the game thread only.</li>
     *     <li>Must be called once all game logic for the tick has finished mutating {@link #pendingBlockData} and
     *     {@link #flags}.</li>
     *     <li>All online mobs should have {@code buildBlockData()} invoked before the updating begins.</li>
     * </ul>
     */
    public final void buildBlockData() {
        blockData = pendingBlockData.build();
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
     * Applies a single instance of damage to this mob, shows the appropriate hitsplat, and plays basic hit/block
     * sounds for players.
     * <p>
     * TODO (future work):
     * </p>
     * <ul>
     *     <li>Derive hit sounds from hit size, attack style, and weapon.</li>
     *     <li>Send attacker sounds only to the attacker, and victim sounds only to the victim.</li>
     *     <li>Add NPC-specific damage and attack sounds based on OSRS behavior.</li>
     * </ul>
     *
     * @param hit The hit information to apply.
     */
    public final void damage(Hit hit) {
        if (pendingBlockData.getHit1() != null) {
            hit2(hit);
        } else {
            hit1(hit);
        }
        addHealth(-hit.getDamage());
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
     * Immediately moves this mob to {@code position}, clearing movement and interaction.
     * <p>
     * This method:
     * </p>
     * <ul>
     *     <li>Updates {@link #position} directly.</li>
     *     <li>Clears the {@link WalkingQueue}.</li>
     *     <li>Resets current interaction target.</li>
     *     <li>Invokes {@link #onMove(Position)} hook.</li>
     * </ul>
     *
     * @param position The destination position.
     */
    public final void move(Position position) {
        setPosition(position);
        walking.clear();
        resetInteractingWith();
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
            flags.flag(UpdateFlag.ANIMATION);
        }
    }

    /**
     * Faces this mob toward a specific {@link Position}.
     *
     * @param position The world position to face.
     */
    public final void face(Position position) {
        pendingBlockData.face(position);
        flags.flag(UpdateFlag.FACE_POSITION);
    }

    /**
     * Faces this mob toward the given {@link Direction} from its current position.
     *
     * @param direction The direction to face.
     */
    public final void face(Direction direction) {
        int x = direction.getTranslation().getX();
        int y = direction.getTranslation().getY();
        face(position.translate(x, y));
    }

    /**
     * Forces this mob to say {@code message} as chat.
     *
     * @param message The message to display (converted via {@link Object#toString()}).
     */
    public final void speak(Object message) {
        pendingBlockData.speak(message.toString());
        flags.flag(UpdateFlag.FORCED_CHAT);
    }

    /**
     * Plays the specified {@link Graphic} on this mob.
     *
     * @param graphic The graphic to display.
     */
    public final void graphic(Graphic graphic) {
        pendingBlockData.graphic(graphic);
        flags.flag(UpdateFlag.GRAPHIC);
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
        flags.flag(UpdateFlag.PRIMARY_HIT);
    }

    /**
     * Displays a secondary hitsplat for this tick.
     *
     * @param hit The hit data to display.
     */
    private void hit2(Hit hit) {
        pendingBlockData.hit2(hit);
        flags.flag(UpdateFlag.SECONDARY_HIT);
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
     * <p>
     * This method:
     * </p>
     * <ul>
     *     <li>Clears the teleporting flag.</li>
     *     <li>Swaps {@link #pendingBlockData} with a fresh builder.</li>
     *     <li>Optionally restores a default facing direction for stationary NPCs.</li>
     *     <li>Clears all {@link #flags} and re-applies {@link UpdateFlag#FACE_POSITION} if needed.</li>
     *     <li>Invokes {@link #reset(UpdateBlockData.Builder)} for subclass-specific cleanup.</li>
     * </ul>
     */
    public final void resetFlags() {
        Direction defaultDirection = this instanceof Npc ? asNpc().getDefaultDirection().orElse(null) : null;
        teleporting = false;
        UpdateBlockData.Builder oldBlockData = pendingBlockData;
        pendingBlockData = new UpdateBlockData.Builder();
        if (defaultDirection != null) {
            pendingBlockData.face(position.translate(1, defaultDirection));
        }
        flags.clear();
        if (defaultDirection != null) {
            flags.flag(UpdateFlag.FACE_POSITION);
        }
        reset(oldBlockData);
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
     * Hook invoked after this mob is teleported by {@link #move(Position)}.
     * <p>
     * Subclasses may override this to perform any post-move logic, such as region checks, interface updates, or
     * script callbacks.
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
    public boolean isTeleporting() {
        return teleporting;
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
}
