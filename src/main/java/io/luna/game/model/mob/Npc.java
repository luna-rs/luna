package io.luna.game.model.mob;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Sets;
import engine.combat.status.StatusEffectType;
import io.luna.LunaContext;
import io.luna.game.action.Action;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityState;
import io.luna.game.model.EntityType;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.game.model.area.Area;
import io.luna.game.model.def.NpcCombatDefinition;
import io.luna.game.model.def.NpcDefinition;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.model.mob.combat.state.NpcCombatContext;
import io.luna.game.model.mob.movement.wandering.DumbWanderingAction;
import io.luna.game.model.mob.movement.wandering.PatrolAction;
import io.luna.game.model.mob.movement.wandering.SmartWanderingAction;
import io.luna.game.model.mob.movement.wandering.WanderingAction;
import io.luna.game.model.mob.movement.wandering.WanderingFrequency;
import io.luna.game.model.path.GamePathfinder;
import io.luna.game.model.path.Pathfinders;
import io.luna.game.model.path.route.RouteStrategy;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents a non-player-controlled mob in the world.
 * <p>
 * <b>Identity equality:</b> {@link #equals(Object)} and {@link #hashCode()} use identity semantics (this instance
 * only).
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
     * When this value is present, the NPC is considered stationary and will not perform wandering movements. It may
     * still turn to face interacting entities if logic elsewhere overrides this behaviour.
     */
    private Optional<Direction> defaultDirection = Optional.empty();

    /**
     * The rule for moving onto a tile, such as {@link RouteStrategy#BLOCKED} for an NPC that stays on water.
     */
    private RouteStrategy routeStrategy = RouteStrategy.NORMAL;

    /**
     * The current NPC definition (may change if the NPC is transformed).
     */
    private NpcDefinition definition;

    /**
     * The current combat definition for this NPC, if any.
     * <p>
     * This may be empty for non-combat or non-attackable NPCs.
     */
    private Optional<NpcCombatDefinition> combatDefinition;

    /**
     * The time, in ticks until this NPC respawns after death.
     * <p>
     * A value of {@code -1} usually indicates no respawn time or that respawning is handled elsewhere.
     */
    private int respawnTicks;

    /**
     * How many times this NPC has entered the world: 1 after its first spawn, one more for each respawn. Code that
     * keeps a reference across a death compares it to tell a new life from the one it saw die.
     */
    private int spawnCount;

    /**
     * The set of human players that currently have this NPC in their local view.
     * <p>
     * This is maintained as a concurrent set and can be used for things like targeted updates, proximity-based
     * behaviour, or analytics.
     */
    private final Set<Player> localHumans = Sets.newConcurrentHashSet();

    /**
     * The combat context holding important combat data.
     */
    protected final NpcCombatContext combat = new NpcCombatContext(this);

    /**
     * The NPC aggression handler.
     */
    protected final NpcAggression aggression = new NpcAggression(this);

    /**
     * Creates a new {@link Npc}.
     *
     * @param context The server context.
     * @param id The base NPC identifier.
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
     * Uses identity-based hashing.
     * <p>
     * An NPC's index is {@code -1} before it is added to the world and again once it is removed, so a hash based on
     * the index changes while the NPC sits in a hash-based collection, and it can no longer be found or removed.
     * Final, so a subclass cannot bring that back.
     */
    @Override
    public final int hashCode() {
        return System.identityHashCode(this);
    }

    /**
     * Uses identity-based equality.
     *
     * @see #hashCode()
     */
    @Override
    public final boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("index", getIndex()).
                add("name", definition.getName()).
                add("id", getId()).toString();
    }

    @Override
    public final int size() {
        return definition.getSize();
    }

    @Override
    public final int sizeX() {
        return 1;
    }

    @Override
    public final int sizeY() {
        return 1;
    }

    @Override
    public void reset() {

    }

    @Override
    public int getCombatLevel() {
        return definition.getCombatLevel();
    }

    @Override
    public int getTotalHealth() {
        return combatDefinition.map(NpcCombatDefinition::getHitpoints).orElse(-1);
    }

    @Override
    public void transform(int requestedId) {
        definition = NpcDefinition.ALL.retrieve(requestedId);
        combatDefinition = NpcCombatDefinition.ALL.get(requestedId);
        transformId = requestedId;
        setSkills();
        flags.flag(UpdateFlag.TRANSFORM);
    }

    @Override
    public void resetTransform() {
        transform(id);
    }

    @Override
    public NpcCombatContext getCombat() {
        return combat;
    }

    @Override
    public GamePathfinder<Position> getInteractionPf() {
        return Pathfinders.forNpc(this);
    }

    @Override
    public RouteStrategy getRouteStrategy() {
        return routeStrategy;
    }

    /**
     * Sets the rule for moving onto a tile. It applies to wandering, single steps and the routes of this NPC.
     *
     * @param routeStrategy The new rule.
     */
    public void setRouteStrategy(RouteStrategy routeStrategy) {
        this.routeStrategy = Objects.requireNonNull(routeStrategy);
    }

    @Override
    protected final void onActive() {
        spawnCount++;
        status.load();
        onSpawn();
    }

    @Override
    protected boolean canReactivate() {
        // Dead and waiting to respawn while it keeps its index, gone for good once it has none.
        return getIndex() != -1;
    }

    /**
     * Run each time the NPC is spawned into the game world, including when it respawns.
     */
    public void onSpawn() {

    }

    /**
     * Takes this NPC out of the world until it {@link #respawn()}s: it becomes {@link EntityState#INACTIVE} but keeps
     * its index, and its actions are dropped so nothing from its last life runs while it is dead. NPCs that don't
     * respawn are removed with {@link MobList#remove(Mob)} instead.
     */
    public void despawn() {
        if (getState() != EntityState.ACTIVE) {
            return;
        }
        actions.clear();
        setState(EntityState.INACTIVE);
    }

    /**
     * Brings this NPC back after {@link #despawn()}, as it was first spawned: at its base position, untransformed,
     * with its combat levels restored and nothing left over from its last life. Does nothing if the NPC was removed
     * from the world while it was dead.
     */
    public void respawn() {
        if (getState() != EntityState.INACTIVE || getIndex() == -1) {
            return;
        }
        for (StatusEffectType type : StatusEffectType.values()) {
            status.remove(type);
        }
        combat.reset();
        getAttributes().clear();
        navigator.cancel();
        walking.clear();
        walking.setLocked(false);
        walking.setRunning(false);
        unlock();
        interact(null);
        setLastDirection(Direction.SOUTH);

        definition = NpcDefinition.ALL.retrieve(id);
        combatDefinition = NpcCombatDefinition.ALL.get(id);
        transformId = -1;
        setSkills();

        setPosition(basePosition);
        setState(EntityState.ACTIVE);
    }

    /**
     * Determines if the given {@link Entity} is within the viewing cone of this NPC.
     * <p>
     * The viewing cone is derived from the NPC's last movement direction. If the entity is directly behind the NPC
     * (outside that cone), this method returns {@code false}. If the entity occupies the same tile, it always
     * returns {@code true}.
     * <p>
     * If this NPC is currently interacting with an entity, the interacting entity is always considered visible even
     * if outside the normal viewing cone.
     *
     * @param locatable The entity to test visibility against.
     * @param viewingDistance The maximum distance in tiles at which the NPC can see the entity.
     * @return {@code true} if the entity is within range and inside the NPC's viewing cone.
     */
    public boolean inViewCone(Locatable locatable, int viewingDistance) {
        if (!position.isWithinDistance(locatable, viewingDistance)) {
            return false;
        }

        // Whoever we're interacting with takes priority.
        if (Objects.equals(getInteractingWith(), locatable)) {
            return true;
        }

        // Get deltas representing where the entity is relative to this NPC.
        Direction relativeDir = Direction.between(position, locatable.abs());
        if (relativeDir == Direction.NONE) {
            // On top of the entity, always seen.
            return true;
        }

        // If the relative direction is within the NPCs view, return true.
        Direction lastDir = getLastDirection();
        return Direction.getAllVisible(lastDir).contains(relativeDir);
    }

    /**
     * Determines if the given {@link Entity} is within this NPC's viewing cone, using the default
     * {@link Position#VIEWING_DISTANCE}.
     *
     * @param entity The entity to test visibility against.
     * @return {@code true} if the entity is within the default viewing distance and inside the viewing cone.
     */
    public boolean inViewCone(Locatable entity) {
        return inViewCone(entity, Position.VIEWING_DISTANCE);
    }

    /**
     * Sets the NPC's combat-related skills (attack, strength, defence, ranged, magic, hitpoints) based on its current
     * {@link NpcCombatDefinition}, if one is present. Base levels are established before dynamic levels so later
     * combat damage and drains can recover toward the definition values.
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

            attack.setStaticLevel(def.getAttackLevel());
            strength.setStaticLevel(def.getStrengthLevel());
            defence.setStaticLevel(def.getDefenceLevel());
            ranged.setStaticLevel(def.getRangedLevel());
            magic.setStaticLevel(def.getMagicLevel());
            hitpoints.setStaticLevel(def.getHitpoints());

            attack.setLevel(def.getAttackLevel());
            strength.setLevel(def.getStrengthLevel());
            defence.setLevel(def.getDefenceLevel());
            ranged.setLevel(def.getRangedLevel());
            magic.setLevel(def.getMagicLevel());
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
     * This is the position the NPC was created at and is usually the center of its wandering radius and respawn
     * location.
     *
     * @return The base spawn position.
     */
    public Position getBasePosition() {
        return basePosition;
    }

    /**
     * Returns the current NPC identifier, which may differ from {@link #getBaseId()} if the NPC has been transformed.
     *
     * @return The current NPC identifier from the active definition.
     */
    public int getId() {
        return definition.id();
    }

    /**
     * Returns the active {@link NpcDefinition} for this NPC.
     *
     * @return The current NPC definition.
     */
    public NpcDefinition def() {
        return definition;
    }

    /**
     * Returns the active {@link NpcCombatDefinition} for this NPC, if any.
     *
     * @return An {@link Optional} containing the combat definition, or empty if none exists.
     */
    public NpcCombatDefinition combatDef() {
        return combatDefinition.orElse(NpcCombatDefinition.ALL.retrieve(1));
    }

    /**
     * Determines if this NPC is stationary.
     * <p>
     * An NPC is considered stationary if it has a {@link #defaultDirection} set, meaning it is expected to always
     * face that direction and not wander.
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
     * NPCs with a default direction set are considered stationary by wandering logic and will not be scheduled
     * for movement unless the direction is cleared.
     *
     * @param defaultDirection The default direction, or {@link Optional#empty()} to allow movement.
     */
    public void setDefaultDirection(Optional<Direction> defaultDirection) {
        this.defaultDirection = defaultDirection;
    }

    /**
     * Sets the remaining respawn ticks for this NPC.
     * <p>
     * How this value is decremented and acted upon is controlled by higher-level NPC or world management code.
     *
     * @param respawnTicks The number of ticks until this NPC should respawn.
     */
    public void setRespawnTicks(int respawnTicks) {
        this.respawnTicks = respawnTicks;
    }

    /**
     * @return How many times this NPC has entered the world, one more for each respawn.
     */
    public int getSpawnCount() {
        return spawnCount;
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
     *     <li>Interrupts any existing wandering {@link Action} such as {@link DumbWanderingAction},
     *     {@link SmartWanderingAction} or {@link PatrolAction}.</li>
     *     <li>Clears the {@link #defaultDirection} so the NPC is no longer considered stationary.</li>
     *     <li>Constructs an {@link Area} centered on the current position with the given radius.</li>
     *     <li>Submits a {@link SmartWanderingAction} if the area is larger than {@code 64x64}
     *     (size > 4096), otherwise submits a {@link DumbWanderingAction}.</li>
     * </ul>
     *
     * @param radius The wandering radius in tiles. Must be 0 or greater.
     * @param frequency The {@link WanderingFrequency} controlling how often the NPC
     * attempts to move within the area.
     */
    public void startWandering(int radius, WanderingFrequency frequency) {
        checkArgument(radius >= 0, "Radius must be 0 or above.");

        setDefaultDirection(Optional.empty());

        Area wanderingArea = Area.of(basePosition, radius);
        if (wanderingArea.size() >= 4096) {
            // If our wandering area is bigger than 64x64, use smart wanderer.
            actions.submitIfAbsent(new SmartWanderingAction(this, wanderingArea, frequency));
        } else {
            actions.submitIfAbsent(new DumbWanderingAction(this, wanderingArea, frequency));
        }
    }

    /**
     * Starts a patrol route for this NPC.
     * <p>
     * Any active {@link WanderingAction} instances are interrupted before the patrol begins, since wandering and
     * patrolling are mutually exclusive movement states.
     *
     * @param patrolBuilder The builder used to create the patrol action to submit.
     */
    public void startPatrolling(PatrolAction.Builder patrolBuilder) {
        setDefaultDirection(Optional.empty());

        actions.submitIfAbsent(patrolBuilder.build());
    }

    /**
     * Returns the set of human players that currently have this NPC in their local view.
     *
     * @return A concurrent {@link Set} of players that currently see this NPC.
     */
    public Set<Player> getLocalHumans() {
        return localHumans;
    }

    /**
     * @return The NPC aggression handler.
     */
    public NpcAggression getAggression() {
        return aggression;
    }

    /**
     * @return The NPC aggression profile.
     */
    public NpcAggressionProfile getAggressionProfile() {
        return combatDef().getAggression();
    }

    /**
     * @return {@code true} if this NPC is a boss.
     */
    public boolean isBoss() {
        return NpcCombatContext.isBoss(id);
    }
}
