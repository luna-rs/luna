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
    protected final UpdateFlagSet flags = new UpdateFlagSet();

    /**
     * The skill set.
     */
    protected final SkillSet skills = new SkillSet(this);

    /**
     * The action set.
     */
    protected final ActionSet actions = new ActionSet();

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
     * The current animation.
     */
    private Animation animation;

    /**
     * The current position being faced.
     */
    private Position facePosition;

    /**
     * The current message being forced.
     */
    private String forcedChat;

    /**
     * The current graphic.
     */
    private Graphic graphic;

    /**
     * The current interaction index.
     */
    private int interactionIndex = -1;

    /**
     * The current primary hitsplat.
     */
    private Hit primaryHit;

    /**
     * The current secondary hitsplat.
     */
    private Hit secondaryHit;

    /**
     * The entity being interacted with.
     */
    private Entity interactingWith;

    /**
     * The transformation identifier.
     */
    int transformId = -1;

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
     * Invoked when this mob submits an action.
     *
     * @param action The action that was submitted.
     */
    public void onSubmitAction(Action action) {

    }

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
        actions.submit(pending);
    }

    /**
     * Shortcut to function {@link ActionSet#interrupt()}.
     */
    public final void interruptAction() {
        actions.interrupt();
    }

    /**
     * Teleports to {@code position}. Will also stop movement and interrupt the current action.
     *
     * @param position The position to teleport to.
     */
    public final void teleport(Position position) {
        setPosition(position);
        walking.clear();
        actions.interrupt();
        resetInteractingWith();
        onTeleport(position);
    }

    /**
     * Attempts to perform {@code newAnimation}.
     *
     * @param newAnimation The animation to perform.
     */
    public final void animation(Animation newAnimation) {
        if (animation == null || newAnimation.overrides(animation)) {
            animation = newAnimation;
            flags.flag(UpdateFlag.ANIMATION);
        }
    }

    /**
     * Faces this mob to {@code position}.
     *
     * @param position The position.
     */
    public final void face(Position position) {
        facePosition = position;
        flags.flag(UpdateFlag.FACE_POSITION);
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
        forcedChat = message;
        flags.flag(UpdateFlag.FORCED_CHAT);
    }

    /**
     * Performs {@code graphic}.
     *
     * @param newGraphic The graphic to perform.
     */
    public final void graphic(Graphic newGraphic) {
        graphic = newGraphic;
        flags.flag(UpdateFlag.GRAPHIC);
    }

    /**
     * Interacts with {@code entity}.
     */
    public final void interact(Entity entity) {
        if (entity == null) {
            // Reset the current interaction.
            interactionIndex = 65535;
            flags.flag(UpdateFlag.INTERACTION);
        } else if (entity instanceof Mob) {
            // Interact with player or npc.
            Mob mob = (Mob) entity;
            interactionIndex = mob.type == EntityType.PLAYER ? mob.index + 32768 : mob.index;
            flags.flag(UpdateFlag.INTERACTION);
        } else {
            // Interact with a non-movable entity.
            face(entity.getPosition());
        }
        
        interactingWith = entity;
    }

    /**
     * Resets the current {@link Entity} we are interacting with.
     */
    public final void resetInteractingWith() {
        // Only reset if we're currently interacting.
        if (interactingWith != null) {
            interact(null);
        }
    }

    /**
     * Displays a primary hitsplat.
     *
     * @param hit The hit to display.
     */
    private void primaryHit(Hit hit) {
        primaryHit = hit;
        flags.flag(UpdateFlag.PRIMARY_HIT);
    }

    /**
     * Displays a secondary hitsplat.
     *
     * @param hit The hit to display.
     */
    private void secondaryHit(Hit hit) {
        secondaryHit = hit;
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
        reset();
        animation = null;
        forcedChat = null;
        facePosition = null;
        interactionIndex = -1;
        primaryHit = null;
        secondaryHit = null;
        flags.clear();
    }

    public final boolean isAlive() {
        return skill(Skill.HITPOINTS).getLevel() > 0;
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
     * A function invoked when teleporting.
     *
     * @param newPosition The teleport position.
     */
    public void onTeleport(Position newPosition) {

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

    /**
     * @return The current animation.
     */
    public final Optional<Animation> getAnimation() {
        return Optional.ofNullable(animation);
    }

    /**
     * @return The current position being faced.
     */
    public final Optional<Position> getFacePosition() {
        return Optional.ofNullable(facePosition);
    }

    /**
     * @return The current message being forced.
     */
    public final Optional<String> getForcedChat() {
        return Optional.ofNullable(forcedChat);
    }

    /**
     * @return The current graphic.
     */
    public final Optional<Graphic> getGraphic() {
        return Optional.ofNullable(graphic);
    }

    /**
     * @return The current interaction index.
     */
    public final int getInteractionIndex() {
        return interactionIndex;
    }

    /**
     * @return The current primary hitsplat.
     */
    public final Optional<Hit> getPrimaryHit() {
        return Optional.ofNullable(primaryHit);
    }

    /**
     * @return The current secondary hitsplat.
     */
    public final Optional<Hit> getSecondaryHit() {
        return Optional.ofNullable(secondaryHit);
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
        return Optional.ofNullable(interactingWith);
    }

    /**
     * @return The transformation identifier.
     */
    public int getTransformId() {
        return transformId;
    }
}
