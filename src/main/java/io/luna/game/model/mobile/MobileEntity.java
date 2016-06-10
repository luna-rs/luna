package io.luna.game.model.mobile;

import io.luna.LunaContext;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.mobile.attr.AttributeMap;
import io.luna.game.model.mobile.update.UpdateFlagHolder;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

/**
 * Something that exists in the Runescape world and is able to move around.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class MobileEntity extends Entity {

    /**
     * An {@link AttributeMap} instance assigned to this {@code MobileEntity}.
     */
    protected final AttributeMap attributes = new AttributeMap();

    /**
     * An {@link UpdateFlagHolder} instance assigned to this {@code MobileEntity}.
     */
    protected final UpdateFlagHolder updateFlags = new UpdateFlagHolder();

    /**
     * The {@link SkillSet} for this {@code MobileEntity}.
     */
    protected final SkillSet skills = new SkillSet(this);

    /**
     * The {@link WalkingQueue} assigned to this {@code MobileEntity}.
     */
    private final WalkingQueue walkingQueue = new WalkingQueue(this);

    /**
     * The index of this mob in its list.
     */
    private int index = -1;

    /**
     * The walking direction of this {@code MobileEntity}.
     */
    private Direction walkingDirection = Direction.NONE;

    /**
     * The running direction of this {@code MobileEntity}.
     */
    private Direction runningDirection = Direction.NONE;

    /**
     * If this {@code MobileEntity} is teleporting.
     */
    private boolean teleporting = true;

    /**
     * The {@link Animation} to perform this cycle.
     */
    private Animation animation;

    /**
     * The {@link Position} to face this cycle.
     */
    private Position facePosition;

    /**
     * The chat to forcibly display this cycle.
     */
    private String forceChat;

    /**
     * The {@link Graphic} to perform this cycle.
     */
    private Graphic graphic;

    /**
     * The {@code MobileEntity} to interact with this cycle.
     */
    private int interactionIndex = -1;

    /**
     * The primary {@link Hit} to display this cycle.
     */
    private Hit primaryHit;

    /**
     * The secondary {@link Hit} to perform this cycle.
     */
    private Hit secondaryHit;

    /**
     * Creates a new {@link MobileEntity}.
     *
     * @param context The context to be managed in.
     */
    public MobileEntity(LunaContext context) {
        super(context);
    }

    /**
     * Clears flags specific to certain types of {@code MobileEntity}s.
     */
    public abstract void reset();

    /**
     * Teleports this {@code MobileEntity} to {@code position}.
     *
     * @param position The {@link Position} to teleport to.
     */
    public final void teleport(Position position) {
        setPosition(position);
        teleporting = true;
        walkingQueue.clear();
    }

    /**
     * Perform {@code animation} on this cycle.
     *
     * @param newAnimation The {@link Animation} to perform this cycle.
     */
    public final void animation(Animation newAnimation) {
        if (animation == null || animation.getPriority().getValue() <= newAnimation.getPriority().getValue()) {
            animation = requireNonNull(newAnimation);
            updateFlags.flag(UpdateFlag.ANIMATION);
        }
    }

    /**
     * Face {@code facePosition} on this cycle.
     *
     * @param facePosition The {@link Position} to face this cycle.
     */
    public final void face(Position facePosition) {
        this.facePosition = requireNonNull(facePosition);
        updateFlags.flag(UpdateFlag.FACE_POSITION);
    }

    /**
     * Force the chat message {@code forceChat} on this cycle.
     *
     * @param forceChat The chat to forcibly display this cycle.
     */
    public final void forceChat(String forceChat) {
        this.forceChat = requireNonNull(forceChat);
        updateFlags.flag(UpdateFlag.FORCE_CHAT);
    }

    /**
     * Perform {@code graphic} on this cycle.
     *
     * @param graphic The {@link Graphic} to perform this cycle.
     */
    public final void graphic(Graphic graphic) {
        this.graphic = requireNonNull(graphic);
        updateFlags.flag(UpdateFlag.GRAPHIC);
    }

    /**
     * Interact with {@code entity} on this cycle.
     *
     * @param entity The {@code MobileEntity} to interact with this cycle.
     */
    public final void interact(MobileEntity entity) {
        this.interactionIndex =
            entity == null ? 65535 : entity.type() == EntityType.PLAYER ? entity.index + 32768 : entity.index;
        updateFlags.flag(UpdateFlag.INTERACTION);
    }

    /**
     * Display {@code primaryHit} on this cycle.
     *
     * @param primaryHit The primary {@link Hit} to display this cycle.
     */
    private void primaryHit(Hit primaryHit) {
        this.primaryHit = requireNonNull(primaryHit);
        updateFlags.flag(UpdateFlag.PRIMARY_HIT);
    }

    /**
     * Display {@code secondaryHit} on this cycle.
     *
     * @param secondaryHit The secondary {@link Hit} to display this cycle.
     */
    private void secondaryHit(Hit secondaryHit) {
        this.secondaryHit = requireNonNull(secondaryHit);
        updateFlags.flag(UpdateFlag.SECONDARY_HIT);
    }

    /**
     * @return The index of this {@link MobileEntity} in its list.
     */
    public final int getIndex() {
        return index;
    }

    /**
     * Sets the value for {@link #index}, cannot be below {@code 1} unless the value is {@code -1}.
     */
    public final void setIndex(int index) {
        if (index != -1) {
            checkArgument(index >= 1, "index < 1");
        }
        this.index = index;
    }

    /**
     * Clears all of the various flags for this cycle.
     */
    public final void clearFlags() {
        reset();
        teleporting = false;
        animation = null;
        forceChat = null;
        facePosition = null;
        interactionIndex = -1;
        primaryHit = null;
        secondaryHit = null;
        updateFlags.clear();
    }

    /**
     * Retrieves a skill from the backing {@link SkillSet}.
     */
    public Skill skill(int id) {
        return skills.getSkill(id);
    }

    /**
     * @return The {@link AttributeMap} instance assigned to this {@code MobileEntity}.
     */
    public final AttributeMap attr() {
        return attributes;
    }

    /**
     * @return The {@link UpdateFlagHolder} instance assigned to this {@code MobileEntity}.
     */
    public final UpdateFlagHolder getUpdateFlags() {
        return updateFlags;
    }

    /**
     * @return The walking direction of this {@code MobileEntity}.
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
     * @return The running direction of this {@code MobileEntity}.
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
     * @return {@code true} if this {@code MobileEntity} is teleporting, {@code false} otherwise.
     */
    public final boolean isTeleporting() {
        return teleporting;
    }

    /**
     * @return The {@link Animation} to perform this cycle.
     */
    public final Animation getAnimation() {
        return animation;
    }

    /**
     * @return The {@link Position} to face this cycle.
     */
    public final Position getFacePosition() {
        return facePosition;
    }

    /**
     * @return The chat to forcibly display this cycle.
     */
    public final String getForceChat() {
        return forceChat;
    }

    /**
     * @return The {@link Graphic} to perform this cycle..
     */
    public final Graphic getGraphic() {
        return graphic;
    }

    /**
     * @return The {@code MobileEntity} to interact with this cycle.
     */
    public final int getInteractionIndex() {
        return interactionIndex;
    }

    /**
     * @return The primary {@link Hit} to display this cycle.
     */
    public final Hit getPrimaryHit() {
        return primaryHit;
    }

    /**
     * @return The secondary {@link Hit} to display this cycle.
     */
    public final Hit getSecondaryHit() {
        return secondaryHit;
    }

    /**
     * @return The {@link WalkingQueue} assigned to this {@code MobileEntity}.
     */
    public WalkingQueue getWalkingQueue() {
        return walkingQueue;
    }

    /**
     * @return The {@link SkillSet} for this {@code MobileEntity}.
     */
    public SkillSet getSkills() {
        return skills;
    }

    /**
     * @return The combat level of this {@code MobileEntity}.
     */
    public int getCombatLevel() {
        return skills.getCombatLevel();
    }
}
