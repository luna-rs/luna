package io.luna.game.model.mob;

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
import io.luna.game.model.mob.block.UpdateFlagSet;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.task.Task;
import game.player.Sounds;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.luna.game.model.mob.Skill.HITPOINTS;

/**
 * A model representing an interactable entity able to move around.
 *
 * @author lare96
 */
public abstract class Mob extends Entity {

    /**
     * The update flag set.
     */
    protected final UpdateFlagSet flags = new UpdateFlagSet();

    /**
     * The skill set.
     */
    protected final SkillSet skills = new SkillSet(this);

    /**
     * The action set.
     */
    protected final ActionQueue actions = new ActionQueue(this);

    /**
     * The walking queue.
     */
    protected final WalkingQueue walking = new WalkingQueue(this);

    /**
     * The mob list index.
     */
    private int index = -1;

    /**
     * The current walking direction.
     */
    private Direction walkingDirection = Direction.NONE;

    /**
     * The current running direction.
     */
    private Direction runningDirection = Direction.NONE;

    /**
     * The last movement direction.
     */
    private Direction lastDirection = Direction.SOUTH;

    /**
     * The current animation.
     */
    private Optional<Animation> animation = Optional.empty();

    /**
     * The current position being faced.
     */
    private Optional<Position> facePosition = Optional.empty();

    /**
     * The current message being forced.
     */
    private Optional<String> forcedChat = Optional.empty();

    /**
     * The current graphic.
     */
    private Optional<Graphic> graphic = Optional.empty();

    /**
     * The current interaction index.
     */
    private OptionalInt interactionIndex = OptionalInt.empty();

    /**
     * The current primary hitsplat.
     */
    private Optional<Hit> primaryHit = Optional.empty();

    /**
     * The current secondary hitsplat.
     */
    private Optional<Hit> secondaryHit = Optional.empty();

    /**
     * The entity being interacted with.
     */
    private Optional<Entity> interactingWith = Optional.empty();

    /**
     * The transformation identifier.
     */
    OptionalInt transformId = OptionalInt.empty();

    /**
     * The player instance. May be null.
     */
    private Player playerInstance;

    /**
     * The npc instance. May be null.
     */
    private Npc npcInstance;

    /**
     * If this mob is locked.
     */
    private boolean locked;

    /**
     * If a teleportation is in progress.
     */
    protected boolean teleporting;

    /**
     * Creates a new {@link Mob}.
     *
     * @param context The context instance.
     */
    public Mob(LunaContext context, Position position, EntityType type) {
        super(context, position, type);
    }

    /**
     * Creates a new {@link Mob}.
     *
     * @param context The context instance.
     */
    public Mob(LunaContext context, EntityType type) {
        super(context, type);
    }

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    /**
     * Resets additional data for the next tick.
     */
    public abstract void reset();

    /**
     * Transforms this mob into an npc with {@code id}.
     *
     * @param id The transformation id.
     */
    public abstract void transform(int id);

    /**
     * Turns a transformed mob back into its original form.
     */
    public abstract void resetTransform();

    /**
     * @return The combat level of this mob.
     */
    public abstract int getCombatLevel();

    /**
     * @return The total health of this mob.
     */
    public abstract int getTotalHealth();

    /**
     * @return The current health of this mob.
     */
    public final int getHealth() {
        return skill(HITPOINTS).getLevel();
    }

    /**
     * Sets the current health of this mob.
     *
     * @param amount The new health.
     */
    public final void setHealth(int amount) {
        var hp = skill(HITPOINTS);
        if (hp.getLevel() > 0) {
            hp.setLevel(amount);
            if (hp.getLevel() <= 0) {
                Mob source = null; // TODO compute source after combat is done
                world.schedule(new MobDeathTask(this, source));
            }
        }
    }

    /**
     * Adds or subtracts {@code amount} from the current health level.
     *
     * @param amount The amount to add or subtract.
     */
    public final void addHealth(int amount) {
        setHealth(getHealth() + amount);
    }

    /**
     * Shortcut to function {@link ActionQueue#submit(Action)}.
     *
     * @param pending The action to submit.
     */
    public final void submitAction(Action<? extends Mob> pending) {
        actions.submit(pending);
    }

    /**
     * Action-locks this mob for the specified amount of ticks.
     */
    public void lock(int ticks) {
        lock(ticks, () -> {
        });
    }

    /**
     * Action-locks this mob for the specified amount of ticks and runs {@code onUnlock} on unlock.
     */
    public void lock(int ticks, Runnable onUnlock) {
        if (!locked) {
            locked = true;
            world.schedule(new Task(ticks) {
                @Override
                protected void execute() {
                    locked = false;
                    onUnlock.run();
                    cancel();
                }
            });
        }
    }

    /**
     * Action-locks this mob completely. <strong{@link #unlock()} must be called at some point or the player will
     * not be able to perform any action, even logout!</strong>
     */
    public void lock() {
        locked = true;
    }

    /**
     * Undoes the action-lock on this mob.
     */
    public void unlock() {
        locked = false;
    }

    /**
     * Damages the mob once.
     */
    public final void damage(Hit hit) {
        if (primaryHit.isPresent()) {
            secondaryHit(hit);
        } else {
            primaryHit(hit);
        }
        addHealth(-hit.getDamage());
        if (this instanceof Player) {
            if (hit.getDamage() > 0) {
                int healthPercent = getHealth() <= 0 ? 100 : (int) Math.floor((double) hit.getDamage() / getHealth());
                if (healthPercent > 20) {
                    asPlr().playSound(Sounds.TAKE_DAMAGE_4);
                } else if (healthPercent > 10) {
                    asPlr().playRandomSound(Sounds.TAKE_DAMAGE_3, Sounds.TAKE_DAMAGE_4);
                } else if (healthPercent > 5) {
                    asPlr().playRandomSound(Sounds.TAKE_DAMAGE, Sounds.TAKE_DAMAGE_2);
                } else {
                    asPlr().playSound(Sounds.TAKE_DAMAGE);
                }
            } else {
                // TODO combat sounds
                asPlr().playSound(Sounds.UNARMED_BLOCK);
            }
        }
    }

    /**
     * Damages the mob twice.
     */
    public final void damage(Hit hit1, Hit hit2) {
        damage(hit1);
        damage(hit2);
    }

    /**
     * Damages the mob three times.
     */
    public final void damage(Hit hit1, Hit hit2, Hit hit3) {
        damage(hit1);
        damage(hit2);
        world.schedule(new Task(1) {
            @Override
            protected void execute() {
                damage(hit3);
                cancel();
            }
        });
    }

    /**
     * Damages the mob four times.
     */
    public final void damage(Hit hit1, Hit hit2, Hit hit3, Hit hit4) {
        damage(hit1);
        damage(hit2);
        world.schedule(new Task(1) {
            @Override
            protected void execute() {
                damage(hit3);
                damage(hit4);
                cancel();
            }
        });
    }

    /**
     * Teleports to {@code position}. Will also stop movement and interrupt the current action.
     *
     * @param position The position to teleport to.
     */
    public final void move(Position position) {
        setPosition(position);
        walking.clear();
        resetInteractingWith();
        onTeleport(position);
    }

    /**
     * Attempts to perform {@code newAnimation}.
     *
     * @param newAnimation The animation to perform.
     */
    public final void animation(Animation newAnimation) {
        if (animation.isEmpty() ||
                animation.filter(newAnimation::overrides).isPresent()) {
            animation = Optional.of(newAnimation);
            flags.flag(UpdateFlag.ANIMATION);
        }
    }

    /**
     * Faces this mob to {@code position}.
     *
     * @param position The position.
     */
    public final void face(Position position) {
        facePosition = Optional.of(position);
        flags.flag(UpdateFlag.FACE_POSITION);
    }

    /**
     * Faces this mob to {@code direction}.
     *
     * @param direction The direction to face.
     */
    public final void face(Direction direction) {
        face(position.translate(direction.getTranslation().getX(), direction.getTranslation().getY()));
    }

    /**
     * Forces {@code message} as chat.
     *
     * @param message The message to force.
     */
    public final void forceChat(Object message) {
        forcedChat = Optional.of(Objects.toString(message));
        flags.flag(UpdateFlag.FORCED_CHAT);
    }

    /**
     * Performs {@code graphic}.
     *
     * @param newGraphic The graphic to perform.
     */
    public final void graphic(Graphic newGraphic) {
        graphic = Optional.of(newGraphic);
        flags.flag(UpdateFlag.GRAPHIC);
    }

    /**
     * Interacts with {@code entity}.
     */
    public final void interact(Entity entity) {
        if (entity == null) {
            // Reset the current interaction.
            interactionIndex = OptionalInt.of(65535);
            flags.flag(UpdateFlag.INTERACTION);
        } else if (entity instanceof Mob) {
            // Interact with player or npc.
            Mob mob = (Mob) entity;
            interactionIndex = mob.type == EntityType.PLAYER ?
                    OptionalInt.of(mob.index + 32768) : OptionalInt.of(mob.index);
            flags.flag(UpdateFlag.INTERACTION);
        } else {
            // Interact with a non-movable entity.
            face(entity.getPosition());
        }
        interactingWith = Optional.ofNullable(entity);
    }

    /**
     * Resets the current {@link Entity} we are interacting with.
     */
    public final void resetInteractingWith() {
        // Only reset if we're currently interacting.
        if (interactingWith.isPresent()) {
            interact(null);
        }
    }

    /**
     * Returns {@code true} if this mob is interacting with {@code entity}.
     */
    public boolean isInteractingWith(Entity entity) {
        return interactingWith.filter(entity::equals).isPresent();
    }

    /**
     * Displays a primary hitsplat.
     *
     * @param hit The hit to display.
     */
    private void primaryHit(Hit hit) {
        primaryHit = Optional.of(hit);
        flags.flag(UpdateFlag.PRIMARY_HIT);
    }

    /**
     * Displays a secondary hitsplat.
     *
     * @param hit The hit to display.
     */
    private void secondaryHit(Hit hit) {
        secondaryHit = Optional.of(hit);
        flags.flag(UpdateFlag.SECONDARY_HIT);
    }

    /**
     * @return The mob list index.
     */
    public final int getIndex() {
        return index;
    }

    /**
     * Sets the value for {@link #index}. Should only be set by the {@link MobList}.
     *
     * @param index The index to set.
     */
    public final void setIndex(int index) {
        if (index != -1) {
            checkArgument(index >= 1, "index < 1");
        }
        this.index = index;
    }

    /**
     * Resets update flag data for the next tick.
     */
    public final void resetFlags() {
        Direction defaultDirection = this instanceof Npc ? asNpc().getDefaultDirection().orElse(null) : null;
        reset();
        teleporting = false;
        animation = Optional.empty();
        forcedChat = Optional.empty();
        facePosition = defaultDirection == null ? Optional.empty() :
                Optional.of(position.translate(1, defaultDirection));
        interactionIndex = OptionalInt.empty();
        primaryHit = Optional.empty();
        secondaryHit = Optional.empty();
        flags.clear();
        if (defaultDirection != null) {
            flags.flag(UpdateFlag.FACE_POSITION);
        }
    }

    /**
     * Retrieves the skill with {@code id}.
     *
     * @param id The identifier.
     * @return The skill.
     */
    public Skill skill(int id) {
        return skills.getSkill(id);
    }

    /**
     * Returns if this mob is alive.
     */
    public boolean isAlive() {
        return skill(HITPOINTS).getLevel() > 0;
    }

    /**
     * Returns this mob instance as a Player. Throws {@link IllegalStateException} if the mob is not a player.
     */
    public Player asPlr() {
        checkState(type == EntityType.PLAYER, "Mob instance is not a Player.");
        if (playerInstance == null) {
            playerInstance = (Player) this;
        }
        return playerInstance;
    }

    /**
     * Returns this mob instance as a Npc. Throws {@link IllegalStateException} if the mob is not a npc.
     */
    public Npc asNpc() {
        checkState(type == EntityType.NPC, "Mob instance is not a Npc.");
        if (npcInstance == null) {
            npcInstance = (Npc) this;
        }
        return npcInstance;
    }

    /**
     * A function invoked when teleporting.
     *
     * @param newPosition The teleport position.
     */
    public void onTeleport(Position newPosition) {

    }

    /**
     * @return The update flag set.
     */
    public final UpdateFlagSet getFlags() {
        return flags;
    }

    /**
     * @return The current walking direction.
     */
    public final Direction getWalkingDirection() {
        return walkingDirection;
    }

    /**
     * Sets the value for {@link #walkingDirection}.
     *
     * @param walkingDirection The new walking direction.
     */
    public final void setWalkingDirection(Direction walkingDirection) {
        this.walkingDirection = walkingDirection;
    }

    /**
     * @return The current running direction.
     */
    public Direction getRunningDirection() {
        return runningDirection;
    }

    /**
     * Sets the value for {@link #runningDirection}.
     *
     * @param runningDirection The new running direction.
     */
    public void setRunningDirection(Direction runningDirection) {
        this.runningDirection = runningDirection;
    }

    public Direction getLastDirection() {
        return lastDirection;
    }

    // todo set this last direction field for immobile npcs that don't move on spawn. faceposition should set direction too? might solve for both?
    public void setLastDirection(Direction lastDirection) {
        this.lastDirection = lastDirection;
    }

    /**
     * @return The current animation.
     */
    public final Optional<Animation> getAnimation() {
        return animation;
    }

    /**
     * @return The current position being faced.
     */
    public final Optional<Position> getFacePosition() {
        return facePosition;
    }

    /**
     * @return The current message being forced.
     */
    public final Optional<String> getForcedChat() {
        return forcedChat;
    }

    /**
     * @return The current graphic.
     */
    public final Optional<Graphic> getGraphic() {
        return graphic;
    }

    /**
     * @return The current interaction index.
     */
    public final OptionalInt getInteractionIndex() {
        return interactionIndex;
    }

    /**
     * @return The current primary hitsplat.
     */
    public final Optional<Hit> getPrimaryHit() {
        return primaryHit;
    }

    /**
     * @return The current secondary hitsplat.
     */
    public final Optional<Hit> getSecondaryHit() {
        return secondaryHit;
    }

    /**
     * @return The walking queue.
     */
    public final WalkingQueue getWalking() {
        return walking;
    }

    /**
     * @return The skill set.
     */
    public final SkillSet getSkills() {
        return skills;
    }

    /**
     * @return The entity being interacted with.
     */
    public final Optional<Entity> getInteractingWith() {
        return interactingWith;
    }

    /**
     * @return The transformation identifier.
     */
    public OptionalInt getTransformId() {
        return transformId;
    }

    /**
     * @return The action set.
     */
    public ActionQueue getActions() {
        return actions;
    }

    /**
     * @return {@code true} if this mob is action-locked.
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * @return The {@code x} coordinate.
     */
    public int getX() {
        return position.getX();
    }

    /**
     * @return The {@code y} coordinate.
     */
    public int getY() {
        return position.getY();
    }

    /**
     * @return The {@code z} coordinate.
     */
    public int getZ() {
        return position.getZ();
    }

    /**
     * @return {@code true} if a teleportation is in progress.
     */
    public boolean isTeleporting() {
        return teleporting;
    }
}
