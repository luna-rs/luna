package io.luna.game.model.mob;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Sets;
import io.luna.LunaContext;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.area.Area;
import io.luna.game.model.def.NpcCombatDefinition;
import io.luna.game.model.def.NpcDefinition;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.model.mob.wandering.DumbWanderingAction;
import io.luna.game.model.mob.wandering.PatrolAction;
import io.luna.game.model.mob.wandering.SmartWanderingAction;
import io.luna.game.model.mob.wandering.WanderingFrequency;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents a non-player-controlled mob in the world.
 * <p>
 * An {@link Npc} is defined by:
 * <ul>
 *     <li>A base spawn identifier and {@link NpcDefinition}.</li>
 *     <li>An optional {@link NpcCombatDefinition} describing combat stats and respawn time.</li>
 *     <li>A base spawn {@link Position}, used for respawning and wandering bounds.</li>
 *     <li>An optional {@link #defaultDirection} that can lock the NPC's facing direction
 *     and effectively make it stationary.</li>
 * </ul>
 * <p>
 * The class also exposes helpers for:
 * <ul>
 *     <li>Transforming the NPC into another definition.</li>
 *     <li>Checking whether an {@link Entity} is inside the NPC's viewing cone.</li>
 *     <li>Starting simple or “smart” wandering actions within a radius.</li>
 *     <li>Tracking nearby human players via {@link #getLocalHumans()}.</li>
 * </ul>
 *
 * @author lare96
 */
public class Npc extends Mob {

    /**
     * The base spawn identifier for this NPC.
     * <p>
     * This ID is used when resetting transformations back to the original definition.
     */
    private final int id;

    /**
     * The base spawn position for this NPC.
     * <p>
     * This is the position the NPC was created at and is typically used as the origin for
     * respawning and wandering radius calculations.
     */
    private final Position basePosition;

    /**
     * The direction this NPC should always face, if present.
     * <p>
     * When this value is present, the NPC is considered stationary and will not perform
     * wandering movements. It may still turn to face interacting entities if logic
     * elsewhere overrides this behaviour.
     */
    private Optional<Direction> defaultDirection = Optional.empty();

    /**
     * The current NPC definition (may change if the NPC is transformed).
     */
    private NpcDefinition definition;

    /**
     * The current combat definition for this NPC, if any.
     * <p>
     * This may be empty for non-combat or “dummy” NPCs.
     */
    private Optional<NpcCombatDefinition> combatDefinition;

    /**
     * The remaining ticks until this NPC should respawn after death.
     * <p>
     * A value of {@code -1} usually indicates no respawn time or that respawning
     * is handled elsewhere.
     */
    private int respawnTicks;

    /**
     * The set of human players that currently have this NPC in their local view.
     * <p>
     * This is maintained as a concurrent set and can be used for things like
     * targeted updates, proximity-based behaviour, or analytics.
     */
    private final Set<Player> localHumans = Sets.newConcurrentHashSet();

    /**
     * Creates a new {@link Npc}.
     * <p>
     * This constructor:
     * <ul>
     *     <li>Initializes the base identifier and spawn position.</li>
     *     <li>Loads the {@link NpcDefinition} and {@link NpcCombatDefinition} for the ID.</li>
     *     <li>Initializes combat skills based on the combat definition, if present.</li>
     *     <li>Sets the NPC's current position to the given spawn position.</li>
     * </ul>
     *
     * @param context  The server context.
     * @param id       The base NPC identifier.
     * @param position The initial spawn position.
     */
    public Npc(LunaContext context, int id, Position position) {
        super(context, EntityType.NPC);
        this.id = id; // Base identifier, for resetting transformations.
        basePosition = position; // Base position, for respawning.

        // Set definition values.
        definition = NpcDefinition.ALL.retrieve(id);
        combatDefinition = NpcCombatDefinition.ALL.get(id);
        respawnTicks = combatDefinition.map(NpcCombatDefinition::getRespawnTime).filter(it -> it > 0).orElse(-1);

        // Set skill levels.
        setSkills();

        // Set position.
        setPosition(position);
    }

    /**
     * Compares this NPC to another object for equality based on index identity.
     *
     * @param obj The object to compare with.
     * @return {@code true} if the other object is an {@link Npc} with the same index.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Npc) {
            Npc other = (Npc) obj;
            return getIndex() == other.getIndex();
        }
        return false;
    }

    /**
     * Computes a hash code for this NPC based on its index.
     *
     * @return The hash code.
     */
    @Override
    public int hashCode() {
        return Objects.hash(getIndex());
    }

    /**
     * Returns a debug-friendly string representation of this NPC including index, name and ID.
     *
     * @return A string representation of this NPC.
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("index", getIndex()).
                add("name", definition.getName()).
                add("id", getId()).toString();
    }

    /**
     * Returns the size (width and height) of this NPC in tiles.
     * <p>
     * This value comes from the NPC definition and is used for collision, clipping
     * and spatial calculations.
     *
     * @return The size of this NPC.
     */
    @Override
    public final int size() {
        return definition.getSize();
    }

    /**
     * Returns the width of this NPC in tiles.
     *
     * @return The X-size of this NPC.
     */
    @Override
    public final int sizeX() {
        return definition.getSize();
    }

    /**
     * Returns the height of this NPC in tiles.
     *
     * @return The Y-size of this NPC.
     */
    @Override
    public final int sizeY() {
        return definition.getSize();
    }

    /**
     * Resets any NPC-specific state.
     * <p>
     * The base implementation is currently empty; subclasses or future extensions
     * may override this to clear custom state when the NPC is reused.
     */
    @Override
    public void reset() {

    }

    /**
     * Returns the combat level of this NPC.
     *
     * @return The combat level, or {@code 0} if no combat definition is present.
     */
    @Override
    public int getCombatLevel() {
        return combatDefinition.map(NpcCombatDefinition::getLevel).orElse(0);
    }

    /**
     * Returns the maximum hitpoints of this NPC.
     *
     * @return The total hitpoints, or {@code -1} if no combat definition is present.
     */
    @Override
    public int getTotalHealth() {
        return combatDefinition.map(NpcCombatDefinition::getHitpoints).orElse(-1);
    }

    /**
     * Transforms this NPC into another definition.
     * <p>
     * This updates:
     * <ul>
     *     <li>The {@link #definition} and {@link #combatDefinition}.</li>
     *     <li>The skill levels, based on the new combat definition.</li>
     *     <li>The internal {@code transformId} field.</li>
     * </ul>
     * and flags a {@link UpdateFlag#TRANSFORM} so clients update their model.
     *
     * @param requestedId The new NPC identifier.
     */
    @Override
    public void transform(int requestedId) {
        definition = NpcDefinition.ALL.retrieve(requestedId);
        combatDefinition = NpcCombatDefinition.ALL.get(requestedId);
        transformId = requestedId;
        setSkills();
        flags.flag(UpdateFlag.TRANSFORM);
    }

    /**
     * Resets any prior transformation and returns this NPC to its base definition.
     */
    @Override
    public void resetTransform() {
        transform(id);
    }

    /**
     * Determines if the given {@link Entity} is within the viewing cone of this NPC.
     * <p>
     * The viewing cone is derived from the NPC's last movement direction. If the entity is
     * directly behind the NPC (outside that cone), this method returns {@code false}. If the
     * entity occupies the same tile, it always returns {@code true}.
     * <p>
     * If this NPC is currently interacting with an entity, the interacting entity is always
     * considered visible even if outside the normal viewing cone.
     *
     * @param entity          The entity to test visibility against.
     * @param viewingDistance The maximum distance in tiles at which the NPC can see the entity.
     * @return {@code true} if the entity is within range and inside the NPC's viewing cone.
     */
    public boolean inViewCone(Entity entity, int viewingDistance) {
        if (!position.isWithinDistance(entity.getPosition(), viewingDistance)) {
            return false;
        }

        // Whoever we're interacting with takes priority.
        if (Objects.equals(getInteractingWith(), entity)) {
            return true;
        }

        // Get deltas representing where the entity is relative to this NPC.
        Direction relativeDir = Direction.between(entity.getPosition(), position);
        if (relativeDir == Direction.NONE) {
            // On top of the entity, always seen.
            return true;
        }

        // If the relative direction is within the NPCs view, return true.
        Direction lastDir = getLastDirection();
        return Direction.getAllVisible(lastDir).contains(relativeDir);
    }

    /**
     * Determines if the given {@link Entity} is within this NPC's viewing cone,
     * using the default {@link Position#VIEWING_DISTANCE}.
     *
     * @param entity The entity to test visibility against.
     * @return {@code true} if the entity is within the default viewing distance and inside the viewing cone.
     */
    public boolean inViewCone(Entity entity) {
        return inViewCone(entity, Position.VIEWING_DISTANCE);
    }

    /**
     * Sets the NPC's combat-related skills (attack, strength, defence, ranged, magic, hitpoints)
     * based on its current {@link NpcCombatDefinition}, if one is present.
     */
    private void setSkills() {
        // Set the attack, strength, defence, ranged, and magic levels.
        combatDefinition.ifPresent(def -> {
            Skill attack = skill(Skill.ATTACK);
            Skill strength = skill(Skill.STRENGTH);
            Skill defence = skill(Skill.DEFENCE);
            Skill ranged = skill(Skill.RANGED);
            Skill magic = skill(Skill.MAGIC);
            Skill hitpoints = skill(Skill.HITPOINTS);

            attack.setLevel(def.getSkill(NpcCombatDefinition.ATTACK));
            strength.setLevel(def.getSkill(NpcCombatDefinition.STRENGTH));
            defence.setLevel(def.getSkill(NpcCombatDefinition.DEFENCE));
            ranged.setLevel(def.getSkill(NpcCombatDefinition.RANGED));
            magic.setLevel(def.getSkill(NpcCombatDefinition.MAGIC));
            hitpoints.setLevel(def.getHitpoints());
        });
    }

    /**
     * Returns the base identifier for this NPC.
     * <p>
     * This is the ID the NPC was created with, and is used when resetting transformations.
     *
     * @return The base NPC identifier.
     */
    public int getBaseId() {
        return id;
    }

    /**
     * Returns the base spawn position for this NPC.
     * <p>
     * This is the position the NPC was created at and is usually the center of its wandering
     * radius and respawn location.
     *
     * @return The base spawn position.
     */
    public Position getBasePosition() {
        return basePosition;
    }

    /**
     * Returns the current NPC identifier, which may differ from {@link #getBaseId()}
     * if the NPC has been transformed.
     *
     * @return The current NPC identifier from the active definition.
     */
    public int getId() {
        return definition.getId();
    }

    /**
     * Returns the active {@link NpcDefinition} for this NPC.
     *
     * @return The current NPC definition.
     */
    public NpcDefinition getDefinition() {
        return definition;
    }

    /**
     * Returns the active {@link NpcCombatDefinition} for this NPC, if any.
     *
     * @return An {@link Optional} containing the combat definition, or empty if none exists.
     */
    public Optional<NpcCombatDefinition> getCombatDef() {
        return combatDefinition;
    }

    /**
     * Determines if this NPC is stationary.
     * <p>
     * An NPC is considered stationary if it has a {@link #defaultDirection} set, meaning
     * it is expected to always face that direction and not wander.
     *
     * @return {@code true} if this NPC does not move, {@code false} otherwise.
     */
    public boolean isStationary() {
        return defaultDirection.isPresent();
    }

    /**
     * Returns the direction this NPC should always face, if configured.
     * <p>
     * NPCs with this value set are treated as stationary by wandering logic.
     *
     * @return An {@link Optional} containing the default facing direction, or empty if none is set.
     */
    public Optional<Direction> getDefaultDirection() {
        return defaultDirection;
    }

    /**
     * Sets the default facing direction for this NPC.
     * <p>
     * NPCs with a default direction set are considered stationary by wandering logic and
     * will not be scheduled for movement unless the direction is cleared.
     *
     * @param defaultDirection The default direction, or {@link Optional#empty()} to allow movement.
     */
    public void setDefaultDirection(Optional<Direction> defaultDirection) {
        this.defaultDirection = defaultDirection;
    }

    /**
     * Sets the remaining respawn ticks for this NPC.
     * <p>
     * How this value is decremented and acted upon is controlled by higher-level NPC
     * or world management code.
     *
     * @param respawnTicks The number of ticks until this NPC should respawn.
     */
    public void setRespawnTicks(int respawnTicks) {
        this.respawnTicks = respawnTicks;
    }

    /**
     * Returns the remaining respawn ticks for this NPC.
     *
     * @return The number of ticks until respawn, or {@code -1} if not applicable.
     */
    public int getRespawnTicks() {
        return respawnTicks;
    }

    /**
     * Starts a wandering behaviour for this NPC within the given radius.
     * <p>
     * This method:
     * <ul>
     *     <li>Validates that the radius is non-negative.</li>
     *     <li>Interrupts any existing wandering {@link io.luna.game.action.Action} such as
     *     {@link DumbWanderingAction}, {@link SmartWanderingAction} or {@link PatrolAction}.</li>
     *     <li>Clears the {@link #defaultDirection} so the NPC is no longer considered stationary.</li>
     *     <li>Constructs an {@link Area} centered on the current position with the given radius.</li>
     *     <li>Submits a {@link SmartWanderingAction} if the area is larger than {@code 64x64}
     *     (size ≥ 4096), otherwise submits a {@link DumbWanderingAction}.</li>
     * </ul>
     *
     * @param radius     The wandering radius in tiles. Must be 0 or greater.
     * @param frequency  The {@link WanderingFrequency} controlling how often the NPC
     *                   attempts to move within the area.
     */
    public void startWandering(int radius, WanderingFrequency frequency) {
        checkArgument(radius >= 0, "Radius must be 0 or above.");
        if (actions.contains(DumbWanderingAction.class) ||
                actions.contains(SmartWanderingAction.class) ||
                actions.contains(PatrolAction.class)) {
            actions.interruptWeak();
        }
        setDefaultDirection(Optional.empty());

        Area wanderingArea = Area.of(position, radius);
        if (wanderingArea.size() >= 4096) {
            // If our wandering area is bigger than 64x64, use smart wanderer.
            actions.submit(new SmartWanderingAction(this, wanderingArea, frequency));
        } else {
            actions.submit(new DumbWanderingAction(this, wanderingArea, frequency));
        }
    }

    /**
     * Returns the set of human players that currently have this NPC in their local view.
     * <p>
     * This set is backed by a concurrent collection, allowing safe modification from
     * multiple threads that manage local view state. It can be used for behaviours that
     * depend on which players can currently see this NPC (e.g., targeted updates or
     * dynamic dialogue).
     *
     * @return A concurrent {@link Set} of players that currently see this NPC.
     */
    public Set<Player> getLocalHumans() {
        return localHumans;
    }
}
