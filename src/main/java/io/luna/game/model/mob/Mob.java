package io.luna.game.model.mob;

import io.luna.LunaContext;
import io.luna.game.action.Action;
import io.luna.game.action.ActionSet;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.mob.attr.AttributeMap;
import io.luna.game.model.mob.block.UpdateFlagSet;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;

import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static io.luna.game.model.mob.Skill.HITPOINTS;

/**
 * A model representing an entity able to move around.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class Mob extends Entity {

    /**
     * The attribute map.
     */
    protected final AttributeMap attributes = new AttributeMap();

    /**
     * The update flag set.
     */
    protected final UpdateFlagSet updateFlags = new UpdateFlagSet();

    /**
     * The skill set.
     */
    protected final SkillSet skillSet = new SkillSet(this);

    /**
     * The action set.
     */
    protected final ActionSet actionSet = new ActionSet();

    /**
     * The walking queue.
     */
    protected final WalkingQueue walkingQueue = new WalkingQueue(this);

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
     * If a teleportation is in progress.
     */
    private boolean teleporting;

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
    private Optional<Integer> interactionIndex = Optional.empty();

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
    Optional<Integer> transformId = Optional.empty();

    /**
     * Creates a new {@link Mob}.
     *
     * @param context The context instance.
     */
    public Mob(LunaContext context, EntityType type) {
        super(context, type);

        // Needed for initial placement.
        teleporting = true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Mob) {
            Mob other = (Mob) obj;
            return index == other.index && type == other.type;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, type);
    }

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
    public final int getCurrentHealth() {
        return skill(HITPOINTS).getLevel();
    }

    /**
     * Sets the current health of this mob.
     *
     * @param health The new health.
     */
    public final void setCurrentHealth(int health) {
        skill(HITPOINTS).setLevel(health);
    }

    /**
     * Adds or subtracts {@code health} from the current health level.
     *
     * @param health The amount to add or subtract.
     */
    public final void changeCurrentHealth(int health) {
        setCurrentHealth(getCurrentHealth() + health);
    }

    /**
     * Shortcut to function {@link ActionSet#submit(Action)}.
     *
     * @param pending The action to submit.
     */
    public final void submitAction(Action<? extends Mob> pending) {
        actionSet.submit(pending);
    }

    /**
     * Shortcut to function {@link ActionSet#interrupt()}.
     */
    public final void interruptAction() {
        actionSet.interrupt();
    }

    /**
     * Teleports to {@code position}. Will also stop movement and interrupt the current action.
     *
     * @param position The position to teleport to.
     */
    public final void teleport(Position position) {
        setPosition(position);
        teleporting = true;
        walkingQueue.clear();
        actionSet.interrupt();
        resetInteractingWith();
    }

    /**
     * Attempts to perform {@code newAnimation}.
     *
     * @param newAnimation The animation to perform.
     */
    public final void animation(Animation newAnimation) {
        Optional<Animation> set = animation.
                filter(newAnimation::overrides).
                map(it -> newAnimation);
        if (set.isPresent()) {
            animation = set;
            updateFlags.flag(UpdateFlag.ANIMATION);
        }
    }

    /**
     * Faces this mob to {@code position}.
     *
     * @param position The position.
     */
    public final void face(Position position) {
        facePosition = Optional.of(position);
        updateFlags.flag(UpdateFlag.FACE_POSITION);
    }

    /**
     * Faces this mob to {@code direction}.
     *
     * @param direction The direction to face.
     */
    public final void face(Direction direction) {
        switch (direction) {
            case NONE:
                throw new IllegalArgumentException("cannot use <NONE>");
            case NORTH_WEST:
                face(position.translate(-1, 1));
                break;
            case NORTH:
                face(position.translate(0, 1));
                break;
            case NORTH_EAST:
                face(position.translate(1, 1));
                break;
            case WEST:
                face(position.translate(-1, 0));
                break;
            case EAST:
                face(position.translate(1, 0));
                break;
            case SOUTH_WEST:
                face(position.translate(-1, -1));
                break;
            case SOUTH:
                face(position.translate(0, -1));
                break;
            case SOUTH_EAST:
                face(position.translate(1, -1));
                break;
        }
    }

    /**
     * Forces {@code message} as chat.
     *
     * @param message The message to force.
     */
    public final void forceChat(String message) {
        forcedChat = Optional.of(message);
        updateFlags.flag(UpdateFlag.FORCED_CHAT);
    }

    /**
     * Performs {@code graphic}.
     *
     * @param newGraphic The graphic to perform.
     */
    public final void graphic(Graphic newGraphic) {
        graphic = Optional.of(newGraphic);
        updateFlags.flag(UpdateFlag.GRAPHIC);
    }

    /**
     * Interacts with {@code entity}.
     */
    public final void interact(Entity entity) {
        if (entity == null) {
            // Reset the current interaction.
            interactionIndex = Optional.of(65535);
            updateFlags.flag(UpdateFlag.INTERACTION);
        } else if (entity instanceof Mob) {
            // Interact with player or npc.
            Mob mob = (Mob) entity;
            interactionIndex = mob.type == EntityType.PLAYER ?
                    Optional.of(mob.index + 32768) : Optional.of(mob.index);
            updateFlags.flag(UpdateFlag.INTERACTION);
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
     * Displays a primary hitsplat.
     *
     * @param hit The hit to display.
     */
    private void primaryHit(Hit hit) {
        primaryHit = Optional.of(hit);
        updateFlags.flag(UpdateFlag.PRIMARY_HIT);
    }

    /**
     * Displays a secondary hitsplat.
     *
     * @param hit The hit to display.
     */
    private void secondaryHit(Hit hit) {
        secondaryHit = Optional.of(hit);
        updateFlags.flag(UpdateFlag.SECONDARY_HIT);
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
        reset();
        teleporting = false;
        animation = Optional.empty();
        forcedChat = Optional.empty();
        facePosition = Optional.empty();
        interactionIndex = Optional.empty();
        primaryHit = Optional.empty();
        secondaryHit = Optional.empty();
        updateFlags.clear();
    }

    /**
     * Retrieves the skill with {@code id}.
     *
     * @param id The identifier.
     * @return The skill.
     */
    public Skill skill(int id) {
        return skillSet.getSkill(id);
    }

    /**
     * @return The attribute map.
     */
    public final AttributeMap getAttributes() {
        return attributes;
    }

    /**
     * @return The update flag set.
     */
    public final UpdateFlagSet getUpdateFlags() {
        return updateFlags;
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

    /**
     * @return {@code true} if a teleportation is in progress.
     */
    public final boolean isTeleporting() {
        return teleporting;
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
    public final Optional<Integer> getInteractionIndex() {
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
    public final WalkingQueue getWalkingQueue() {
        return walkingQueue;
    }

    /**
     * @return The skill set.
     */
    public final SkillSet getSkills() {
        return skillSet;
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
    public Optional<Integer> getTransformId() {
        return transformId;
    }
}
