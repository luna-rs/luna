package io.luna.game.model.mob.combat.state;

import api.combat.npc.NpcCombatHandler;
import io.luna.game.action.impl.NpcRetreatAction;
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy;
import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.combat.attack.MeleeCombatAttack;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.damage.CombatDamageType;

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
    public int getDefaultMaxHit(CombatDamageType type) {
        return mob.getMaxHit() > 0 ? mob.getMaxHit() : mob.getCombatDef().getMaximumHit();
    }

    @Override
    public CombatAttack<?> getNextAttack(Mob victim, boolean attackReady) {
        return NpcCombatHandler.INSTANCE.supplyAttack(mob, victim, attackReady);
    }

    @Override
    public void onNextDefence(Mob attacker, CombatDamage damage) {
        NpcCombatHandler.INSTANCE.consumeDefence(mob, attacker);
    }

    @Override
    public EquipmentBonus getAttackStyleBonus() {
        EquipmentBonus bonus = mob.getCombatDef().getDefaultAttackBonus();
        return bonus != null ? bonus : mob.getCombatDef().findHighestAttackBonus();
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

    /**
     * Creates the default melee attack for this NPC against the specified victim.
     * <p>
     * The default attack uses the NPC's configured attack animation, a melee range of {@code 1}, and the attack speed
     * from the combat definition.
     *
     * @param victim the current combat target
     * @return the default melee combat attack for this NPC
     */
    public CombatAttack<Npc> getDefaultAttack(Mob victim) {
        return new MeleeCombatAttack<>(mob, victim, mob.getCombatDef().getAttackAnimation(), 1, mob.getCombatDef().getAttackSpeed());
    }

    /**
     * Plays the default defence animation for this NPC.
     * <p>
     * The animation ID is taken directly from the NPC's combat definition.
     */
    public void handleDefaultDefence() {
        int id = mob.getCombatDef().getDefenceAnimation();
        mob.animation(new Animation(id));
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