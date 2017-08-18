package io.luna.game.model.mob;

import io.luna.LunaContext;
import io.luna.game.action.Action;
import io.luna.game.action.ActionSet;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.mob.attr.AttributeMap;
import io.luna.game.model.mob.update.UpdateFlagSet;
import io.luna.game.model.mob.update.UpdateFlagSet.UpdateFlag;

import java.util.Optional;
import java.util.OptionalInt;

import static com.google.common.base.Preconditions.checkArgument;

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
    private Optional<String> forceChat = Optional.empty();

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
     * Creates a new {@link Mob}.
     *
     * @param context The context instance.
     */
    public Mob(LunaContext context, EntityType type) {
        super(context, type);
        teleporting = true; /* Needed for initial placement. */
    }

    /**
     * Resets additional data for the next tick.
     */
    public abstract void reset();

    /**
     * Shortcut to function {@code ActionSet.submit(Action<? extends Mob>)}.
     */
    public final void submitAction(Action<? extends Mob> pending) {
        actionSet.submit(pending);
    }

    /**
     * Shortcut to function {@code ActionSet.interrupt()}.
     */
    public final void interruptAction() {
        actionSet.interrupt();
    }

    /**
     * Teleports to {@code position}. Will also stop movement and interrupt the current action.
     */
    public final void teleport(Position position) {
        setPosition(position);
        teleporting = true;
        walkingQueue.clear();
        actionSet.interrupt();
    }

    /**
     * Attempts to perform {@code newAnimation}.
     */
    public final void animation(Animation newAnimation) {
        if (animation.isPresent()) {
            Animation current = animation.get();
            if (!newAnimation.overrides(current)) {
                return;
            }
        }
        animation = Optional.of(newAnimation);
        updateFlags.flag(UpdateFlag.ANIMATION);
    }

    /**
     * Faces this mob to {@code position}.
     */
    public final void face(Position position) {
        facePosition = Optional.of(position);
        updateFlags.flag(UpdateFlag.FACE_POSITION);
    }

    /**
     * Faces this mob to {@code direction}.
     */
    public final void face(Direction direction) {
        int currentX = position.getX();
        int currentY = position.getY();

        switch (direction) {
        case NONE:
            throw new IllegalArgumentException("cannot use <NONE>");
        case NORTH_WEST:
            face(new Position(currentX - 1, currentY + 1));
            break;
        case NORTH:
            face(new Position(currentX, currentY + 1));
            break;
        case NORTH_EAST:
            face(new Position(currentX + 1, currentY + 1));
            break;
        case WEST:
            face(new Position(currentX - 1, currentY));
            break;
        case EAST:
            face(new Position(currentX + 1, currentY));
            break;
        case SOUTH_WEST:
            face(new Position(currentX - 1, currentY - 1));
            break;
        case SOUTH:
            face(new Position(currentX, currentY - 1));
            break;
        case SOUTH_EAST:
            face(new Position(currentX + 1, currentY - 1));
            break;
        }
    }

    /**
     * Forces {@code message} as chat.
     */
    public final void forceChat(String message) {
        forceChat = Optional.of(message);
        updateFlags.flag(UpdateFlag.FORCE_CHAT);
    }

    /**
     * Performs {@code graphic}.
     */
    public final void graphic(Graphic newGraphic) {
        graphic = Optional.of(newGraphic);
        updateFlags.flag(UpdateFlag.GRAPHIC);
    }

    /**
     * Interacts with {@code entity}.
     */
    public final void interact(Entity entity) {
        if (entity == null) { /* Reset the current interaction. */
            interactionIndex = OptionalInt.of(65535);
            updateFlags.flag(UpdateFlag.INTERACTION);
        } else if (entity instanceof Mob) { /* Interact with player or npc. */
            Mob mob = (Mob) entity;
            interactionIndex = OptionalInt.of(mob.type == EntityType.PLAYER ? mob.index + 32768 : mob.index);
            updateFlags.flag(UpdateFlag.INTERACTION);
        } else { /* Interact with a non-movable entity. */
            face(entity.getPosition());
        }
        interactingWith = Optional.ofNullable(entity);
    }

    /**
     * Displays a primary hitsplat.
     */
    private void primaryHit(Hit hit) {
        primaryHit = Optional.of(hit);
        updateFlags.flag(UpdateFlag.PRIMARY_HIT);
    }

    /**
     * Displays a secondary hitsplat.
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
     * Sets the value for {@link #index}.
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
        forceChat = Optional.empty();
        facePosition = Optional.empty();
        interactionIndex = OptionalInt.empty();
        primaryHit = Optional.empty();
        secondaryHit = Optional.empty();
        updateFlags.clear();
    }

    /**
     * Retrieves the skill with {@code id}.
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
    public final Optional<String> getForceChat() {
        return forceChat;
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
    public WalkingQueue getWalkingQueue() {
        return walkingQueue;
    }

    /**
     * @return The skill set.
     */
    public SkillSet getSkills() {
        return skillSet;
    }

    /**
     * Returns the combat level.
     */
    public int getCombatLevel() {
        return skillSet.getCombatLevel();
    }

    /**
     * @return The entity being interacted with.
     */
    public Optional<Entity> getInteractingWith() {
        return interactingWith;
    }
}
