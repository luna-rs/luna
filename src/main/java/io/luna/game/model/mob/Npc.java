package io.luna.game.model.mob;

import com.google.common.base.MoreObjects;
import io.luna.LunaContext;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.def.NpcCombatDefinition;
import io.luna.game.model.def.NpcDefinition;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;

import java.util.List;
import java.util.Objects;
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
    private NpcCombatDefinition combatDefinition;

    /**
     * The transformation identifier.
     */
    private int transformId = -1;

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
        combatDefinition = NpcCombatDefinition.ALL.get(id).orElse(null);

        // Set skill levels.
        setSkills();

        // Set position.
        setPosition(position);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Npc)) {
            return false;
        }
        
        return getIndex() == ((Npc) obj).getIndex();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getIndex());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("index", getIndex())
                .add("name", definition.getName())
                .add("id", getId())
                .toString();
    }
    
    @Override
    public int size() {
        return definition.getSize();
    }

    @Override
    public void reset() {
        transformId = -1;
    }

    @Override
    public int getCombatLevel() {
        return combatDefinition == null ? 0 : combatDefinition.getLevel();
    }

    @Override
    public int getTotalHealth() {
        return combatDefinition == null ? -1 : combatDefinition.getHitpoints();
    }

    @Override
    public void transform(int id) {
        definition = NpcDefinition.ALL.retrieve(id);
        combatDefinition = NpcCombatDefinition.ALL.get(id).orElse(null);
        transformId = id;
        setSkills();
        flags.flag(UpdateFlag.TRANSFORM);
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
        if (combatDefinition != null) {
            List<Integer> skills = combatDefinition.getSkills();
            
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
            hitpoints.setLevel(combatDefinition.getHitpoints());
        }
    }

    /**
     * @return The base identifier.
     */
    public int getBaseId() {
        return id;
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
        return Optional.ofNullable(combatDefinition);
    }
}
