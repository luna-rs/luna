package io.luna.game.model.mobile;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import io.luna.LunaContext;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.def.NpcCombatDefinition;
import io.luna.game.model.def.NpcDefinition;
import io.luna.game.model.mobile.update.UpdateFlagSet.UpdateFlag;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * A model representing a non-player-controlled mob.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Npc extends Mob {

    /**
     * The identifier.
     */
    private final int id;

    /**
     * The definition.
     */
    private NpcDefinition definition;

    /**
     * The combat definition.
     */
    private NpcCombatDefinition combatDefinition;

    /**
     * The transformation identifier.
     */
    private OptionalInt transformId = OptionalInt.empty();

    /**
     * The current hitpoint level.
     */
    private int currentHp;

    /**
     * Creates a new {@link Npc}.
     *
     * @param context The context instance.
     * @param id The identifier.
     * @param position The position.
     */
    public Npc(LunaContext context, int id, Position position) {
        super(context, EntityType.NPC);
        this.id = id;

        /* Set definition values. */
        definition = NpcDefinition.get(id);
        combatDefinition = NpcCombatDefinition.get(id);

        /* Set the current hitpoint level. */
        currentHp = combatDefinition.getHitpoints();

        /* Set the attack, strength, defence, ranged, and magic levels. */
        ImmutableList<Integer> skills = combatDefinition.getSkills();
        Skill attack = skill(Skill.ATTACK);
        Skill strength = skill(Skill.STRENGTH);
        Skill defence = skill(Skill.DEFENCE);
        Skill ranged = skill(Skill.RANGED);
        Skill magic = skill(Skill.MAGIC);

        attack.setLevel(skills.get(NpcCombatDefinition.ATTACK));
        strength.setLevel(skills.get(NpcCombatDefinition.STRENGTH));
        defence.setLevel(skills.get(NpcCombatDefinition.DEFENCE));
        ranged.setLevel(skills.get(NpcCombatDefinition.RANGED));
        magic.setLevel(skills.get(NpcCombatDefinition.MAGIC));

        /* Set the position. */
        setPosition(position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getIndex());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("name", definition.getName()).toString();
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
    public int size() {
        return definition.getSize();
    }

    @Override
    public void reset() {
        transformId = OptionalInt.empty();
    }

    @Override
    public int getCombatLevel() {
        return combatDefinition.getLevel();
    }

    /**
     * Transforms this npc into an npc with {@code id}.
     */
    public void transform(int id) {
        transformId = OptionalInt.of(id);
        definition = NpcDefinition.get(id);
        updateFlags.flag(UpdateFlag.TRANSFORM);
    }

    /**
     * @return The identifier.
     */
    public int getId() {
        return id;
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
    public NpcCombatDefinition getCombatDefinition() {
        return combatDefinition;
    }

    /**
     * @return The transformation identifier.
     */
    public OptionalInt getTransformId() {
        return transformId;
    }

    /**
     * @return The current hitpoint level.
     */
    public int getCurrentHp() {
        return currentHp;
    }

    /**
     * Sets the current hitpoint level.
     */
    public void setCurrentHp(int currentHp) {
        this.currentHp = currentHp;
    }
}
