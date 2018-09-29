package io.luna.game.model.mob;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import io.luna.LunaContext;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.def.NpcCombatDefinition;
import io.luna.game.model.def.NpcDefinition;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;

import java.util.Optional;

/**
 * A model representing a non-player-controlled mob.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Npc extends Mob {

    /**
     * The base identifier. The one the class was created with.
     */
    private final int id;

    /**
     * The definition.
     */
    private NpcDefinition definition;

    /**
     * The combat definition.
     */
    private Optional<NpcCombatDefinition> combatDefinition;

    /**
     * The transformation identifier.
     */
    private Optional<Integer> transformId = Optional.empty();

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

        // Set definition values.
        definition = NpcDefinition.ALL.retrieve(id);
        combatDefinition = NpcCombatDefinition.ALL.get(id);

        // Set skill levels.
        setSkills();

        // Set position.
        setPosition(position);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("index", getIndex()).
                add("name", definition.getName()).
                add("id", getId()).toString();
    }


    @Override
    public int size() {
        return definition.getSize();
    }

    @Override
    public void reset() {
        transformId = Optional.empty();
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
        transformId = Optional.of(id);
        setSkills();
        updateFlags.flag(UpdateFlag.TRANSFORM);
    }

    @Override
    public void resetTransform() {
        transform(id);
    }

    /**
     * Sets all the combat skill levels.
     */
    private void setSkills() {
        // Set the attack, strength, defence, ranged, and magic levels.
        combatDefinition.ifPresent(def -> {
            ImmutableList<Integer> skills = def.getSkills();
            Skill attack = skill(Skill.ATTACK);
            Skill strength = skill(Skill.STRENGTH);
            Skill defence = skill(Skill.DEFENCE);
            Skill ranged = skill(Skill.RANGED);
            Skill magic = skill(Skill.MAGIC);
            Skill hitpoints = skill(Skill.HITPOINTS);

            attack.setLevel(skills.get(NpcCombatDefinition.ATTACK));
            strength.setLevel(skills.get(NpcCombatDefinition.STRENGTH));
            defence.setLevel(skills.get(NpcCombatDefinition.DEFENCE));
            ranged.setLevel(skills.get(NpcCombatDefinition.RANGED));
            magic.setLevel(skills.get(NpcCombatDefinition.MAGIC));
            hitpoints.setLevel(def.getHitpoints());
        });
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

}
