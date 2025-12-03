package io.luna.game.model.mob;

import com.google.common.base.MoreObjects;
import io.luna.LunaContext;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.def.NpcCombatDefinition;
import io.luna.game.model.def.NpcDefinition;
import io.luna.game.model.mob.block.UpdateBlockData;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;

import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A model representing a non-player-controlled mob.
 *
 * @author lare96
 */
public class Npc extends Mob {

    /**
     * The spawn identifier.
     */
    private final int id;

    /**
     * The spawn position.
     */
    private final Position basePosition;

    /**
     * The direction this NPC should always face. NPCs with this value present will not move.
     */
    private Optional<Direction> defaultDirection = Optional.empty();

    /**
     * The definition.
     */
    private NpcDefinition definition;

    /**
     * The combat definition.
     */
    private Optional<NpcCombatDefinition> combatDefinition;

    /**
     * If this NPC should respawn.
     */
    private boolean respawn;

    /**
     * The NPC wander radius.
     */
    private int wanderRadius; // TODO NPC wandering
    private int respawnTicks = -1;

    /**
     * Creates a new {@link Npc}.
     *
     * @param context The context instance.
     * @param id The identifier.
     * @param position The position.
     */
    public Npc(LunaContext context, int id, Position position) {
        super(context, EntityType.NPC);
        this.id = id; // Base identifier, for resetting transformations.
        basePosition = position; // Base position, for respawning.

        // Set definition values.
        definition = NpcDefinition.ALL.retrieve(id);
        combatDefinition = NpcCombatDefinition.ALL.get(id);

        // Set skill levels.
        setSkills();

        // Set position.
        setPosition(position);
    }

    @Override
    public void onTeleport(Position newPosition) {
        teleporting = true;
    }

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

    @Override
    public int hashCode() {
        return Objects.hash(getIndex());
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
        return definition.getSize();
    }

    @Override
    public final int sizeY() {
        return definition.getSize();
    }

    @Override
    public void reset(UpdateBlockData.Builder oldBlockData) {

    }

    @Override
    public int getCombatLevel() {
        return combatDefinition.map(NpcCombatDefinition::getLevel).orElse(0);
    }

    @Override
    public int getTotalHealth() {
        return combatDefinition.map(NpcCombatDefinition::getHitpoints).orElse(-1);
    }

    @Override
    public void transform(int id) {
        definition = NpcDefinition.ALL.retrieve(id);
        combatDefinition = NpcCombatDefinition.ALL.get(id);
        pendingBlockData.transform(id);
        setSkills();
        flags.flag(UpdateFlag.TRANSFORM);
    }

    @Override
    public void resetTransform() {
        transform(id);
    }

    /**
     * Determines if {@code entity} is within the viewing cone of this NPC. This is based on the direction that this
     * NPC is currently facing. An example of this; if this NPC's back is turned to {@code entity}, this function will return
     * {@code false}.
     * <p>
     * If this NPC is interacting with someone currently, returns {@code false} unless the interacting entity
     * matches {@code entity}.
     *
     * @param entity The entity.
     * @return {@code true} if the entity can be seen.
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

    public boolean inViewCone(Entity entity) {
        return inViewCone(entity, Position.VIEWING_DISTANCE);
    }

    /**
     * Sets all the combat skill levels.
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
     * @return The base identifier.
     */
    public int getBaseId() {
        return id;
    }

    /**
     * @return The base position. The one the class was created with.
     */
    public Position getBasePosition() {
        return basePosition;
    }

    /**
     * @return The identifier.
     */
    public int getId() {
        return definition.getId();
    }

    /**
     * @return The definition.
     */
    public NpcDefinition getDefinition() {
        return definition;
    }

    /**
     * @return The combat definition.
     */
    public Optional<NpcCombatDefinition> getCombatDef() {
        return combatDefinition;
    }

    /**
     * @return {@code true} if this NPC respawns, {@code false} otherwise.
     */
    public boolean isRespawn() {
        return respawn;
    }

    /**
     * @return {@code true} if this NPC doesn't move, {@code false} otherwise.
     */
    public boolean isStationary() {
        return defaultDirection.isPresent();
    }

    /**
     * @return The direction this NPC should always face. NPCs with this value set will not move.
     */
    public Optional<Direction> getDefaultDirection() {
        return defaultDirection;
    }

    /**
     * Sets the direction this NPC should always face. NPCs with this value set will not move.
     */
    public void setDefaultDirection(Optional<Direction> defaultDirection) {
        this.defaultDirection = defaultDirection;
    }

    /**
     * Forces this NPC to respawn when killed.
     *
     * @return This instance, for chaining.
     */
    public Npc setRespawning() {
        respawn = true;
        return this;
    }

    public void setRespawnTicks(int respawnTicks) {
        this.respawnTicks = respawnTicks;
    }

    public int getRespawnTicks() {
        return respawnTicks;
    }

    /**
     * Forces this NPC to start wandering. This will undo its current {@link #defaultDirection}. The radius must be
     * 0 or above.
     *
     * @return This instance, for chaining.
     */
    public Npc setWandering(int radius) {
        checkArgument(radius >= 0, "Radius must be 0 or above.");
        if (wanderRadius != radius) { // only change if different from current
            if (radius == 0) { // TODO wandering code
                // cancel wander action
            } else {
                // start or modify "action"
            }

            setDefaultDirection(Optional.empty());
        }
        return this;
    }
}
