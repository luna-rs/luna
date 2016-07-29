package io.luna.game.model.mobile;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import io.luna.LunaContext;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.def.NpcCombatDefinition;
import io.luna.game.model.def.NpcDefinition;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;

import java.util.Objects;

/**
 * A mobile entity that is controlled by the server.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Npc extends MobileEntity {

    /**
     * The identifier for this {@code Npc}.
     */
    private final int id;

    /**
     * The definition instance for this {@code Npc}.
     */
    private NpcDefinition definition;

    /**
     * The combat definition instance for this {@code Npc}.
     */
    private NpcCombatDefinition combatDefinition;

    /**
     * The identifier for the transformation {@code Npc}.
     */
    private int transformId = -1;

    /**
     * The current health value of this {@code Npc}.
     */
    private int currentHp;

    /**
     * Creates a new {@link Npc}.
     *
     * @param context The context to be managed in.
     * @param id The identifier for this {@code Npc}.
     * @param position The position of this {@code Npc}.
     */
    public Npc(LunaContext context, int id, Position position) {
        super(context);
        this.id = id;

        definition = NpcDefinition.DEFINITIONS.get(id);
        combatDefinition = NpcCombatDefinition.getDefinition(id);
        currentHp = combatDefinition.getHitpoints();

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
    public EntityType type() {
        return EntityType.NPC;
    }

    @Override
    public void reset() {
        transformId = -1;
    }

    @Override
    public int getCombatLevel() {
        return combatDefinition.getCombatLevel();
    }

    /**
     * Transforms this {@code Npc} into another {@code Npc}.
     *
     * @param id The identifier of the {@code Npc} to transform into.
     */
    public void transform(int id) {
        transformId = id;
        definition = NpcDefinition.DEFINITIONS.get(id);
        updateFlags.flag(UpdateFlag.TRANSFORM);
    }

    /**
     * @return The definition instance for this {@code Npc}.
     */
    public NpcDefinition getDefinition() {
        return definition;
    }

    /**
     * @return The definition instance for this {@code Npc}.
     */
    public NpcCombatDefinition getCombatDefinition() {
        return combatDefinition;
    }

    /**
     * @return The identifier for this {@link Npc}.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The identifier for the transformation {@code Npc}.
     */
    public int getTransformId() {
        return transformId;
    }

    /**
     * @return The current health value of this {@code Npc}.
     */
    public int getCurrentHp() {
        return currentHp;
    }

    /**
     * Sets the current health value of this {@code Npc}.
     */
    public void setCurrentHp(int currentHp) {
        this.currentHp = currentHp;
    }
}
