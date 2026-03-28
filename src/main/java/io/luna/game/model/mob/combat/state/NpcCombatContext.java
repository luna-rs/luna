package io.luna.game.model.mob.combat.state;

import io.luna.game.action.impl.NpcRetreatAction;
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy;
import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.block.Animation.AnimationPriority;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.combat.attack.MeleeCombatAttack;
import io.luna.game.model.mob.combat.damage.CombatDamageType;
import io.luna.util.RandomUtils;

/**
 * A {@link CombatContext} implementation for {@link Npc}s.
 * <p>
 * This class provides NPC-specific combat behavior.
 *
 * @author lare96
 */
public final class NpcCombatContext extends CombatContext<Npc> {

    /**
     * The retreating policy of this mob.
     */
    private RetreatPolicy retreatPolicy = RetreatPolicy.NONE;

    /**
     * Creates a new {@link NpcCombatContext}.
     *
     * @param npc The owning mob.
     */
    public NpcCombatContext(Npc npc) {
        super(npc);
    }

    @Override
    public int getMaxHit(CombatDamageType type) {
        return RandomUtils.inclusive(mob.getMaxHit() > 0 ? mob.getMaxHit() : mob.getCombatDef().getMaximumHit());
    }

    @Override
    public CombatAttack<Npc> getNextAttack(Mob victim) {

        /*
          todo    For the NPCs its
               - class based hook
               - id based hook
               - default melee using its attack animations
               hook example
               combat(1) {
                 ...
                 attack = {
                  npc.speak("i kill noobs!")
                  return@combat RangedCombatAttack(...)
                 }
                 defend = {
                 npc.speak("ow.. noob dealt $damage.rawAmount damage!")
                 }
                 maxHit = 100 // generic max hit override

               }
         */
        Animation attack = new Animation(mob.getCombatDef().getAttackAnimation(), AnimationPriority.HIGH);
        return new MeleeCombatAttack<>(mob, victim, attack, 1, mob.getCombatDef().getAttackSpeed());
    }

    @Override
    public EquipmentBonus getAttackStyleBonus() {
        EquipmentBonus bonus = mob.getCombatDef().getDefaultAttackBonus();
        return bonus != null ? bonus : mob.getCombatDef().findHighestAttackBonus();
    }

    @Override
    public Animation getDefenceAnimation() {
        return new Animation(mob.getCombatDef().getDefenceAnimation());
    }

    @Override
    public boolean isAutoRetaliate() {
        return true;
    }

    @Override
    public boolean onCombatProcess(boolean reached) {
        boolean retreat = false;
        switch (retreatPolicy) {
            case LOW_HEALTH:
                if (mob.getHealthPercent() <= 25) {
                    retreat = true;
                }
                break;
            case DISTANCE:
                if (!mob.getBasePosition().isViewable(mob)) {
                    retreat = true;
                }
                break;
        }
        if (retreat) {
            mob.getActions().submitIfAbsent(new NpcRetreatAction(mob));
            return false;
        }
        return true;
    }

    @Override
    public void onCombatFinished() {
        // TODO Do regular NPCs like "Man" travel back to their spawn area after combat? Or can they be leashed
        //  indefinitely? Or do they retreat?
    }

    /**
     * Returns whether this NPC is currently retreating.
     * <p>
     * An NPC is considered retreating if its action queue currently contains an active {@link NpcRetreatAction}.
     *
     * @return {@code true} if this NPC is retreating, otherwise {@code false}.
     */
    public boolean isRetreating() {
        return mob.getActions().contains(NpcRetreatAction.class);
    }

    /**
     * Returns the retreat policy assigned to this mob.
     * <p>
     * The retreat policy determines how this NPC behaves when retreat conditions are met, such as whether it flees,
     * resets, or continues fighting.
     *
     * @return The current retreat policy.
     */
    public RetreatPolicy getRetreatPolicy() {
        return retreatPolicy;
    }

    /**
     * Sets the retreat policy for this mob.
     * <p>
     * The retreat policy controls how this NPC should respond when its retreat logic is triggered.
     *
     * @param retreatPolicy The retreat policy to assign.
     */
    public void setRetreatPolicy(RetreatPolicy retreatPolicy) {
        this.retreatPolicy = retreatPolicy;
    }
}