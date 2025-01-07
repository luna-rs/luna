package io.luna.game.model.mob;

import com.google.common.base.MoreObjects;
import io.luna.LunaContext;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.def.NpcCombatDefinition;
import io.luna.game.model.def.NpcDefinition;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A model representing a non-player-controlled mob.
 *
 * @author lare96
 */
public class Npc extends Mob {


    /**
     * The base identifier. The one the class was created with.
     */
    private final int id;

    /**
     * The base position. The one the class was created with.
     */
    private final Position basePosition;

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
    public void reset() {
        transformId = OptionalInt.empty();
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
        transformId = OptionalInt.of(id);
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
    public boolean isInViewCone(Entity entity) {
        if (!position.isWithinDistance(entity.getPosition(), Position.VIEWING_DISTANCE / 2)) {
            return false;
        }

        // Whoever we're interacting with takes priority.
        if (getInteractingWith().isPresent()) {
            return getInteractingWith().get().equals(entity);
        }

        // Get deltas representing where the entity is relative to this NPC.
        int deltaX = entity.getPosition().getX() - position.getX();
        int deltaY = entity.getPosition().getY() - position.getY();
        if (deltaX > 0) {
            deltaX = 1;
        }
        if (deltaY > 0) {
            deltaY = 1;
        }
        if (deltaX < 0) {
            deltaX = -1;
        }
        if (deltaY < 0) {
            deltaY = -1;
        }

        // Get the direction that matches the relative direction.
        // Ie. Entity is to the WEST of this NPC.
        Direction relativeDir = Direction.NONE;
        for (Direction dir : Direction.ALL) {
            if (dir.getTranslation().getX() == deltaX &&
                    dir.getTranslation().getY() == deltaY) {
                relativeDir = dir;
                break;
            }
        }

        if (relativeDir == Direction.NONE) {
            // On top of the entity, always seen.
            return true;
        }

        // If the relative direction is within the NPCs view, return true.
        Direction lastDir = getLastDirection();
        return Direction.getAllVisible(lastDir).contains(relativeDir);
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
    public Optional<NpcCombatDefinition> getCombatDefinition() {
        return combatDefinition;
    }

    /**
     * @return {@code true} if this NPC respawns, {@code false} otherwise.
     */
    public boolean isRespawn() {
        return respawn;
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
}
