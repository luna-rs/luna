package io.luna.game.model.mob.combat.state;

import api.combat.npc.NpcCombatHandler;
import com.google.common.collect.ImmutableSet;
import io.luna.game.action.impl.NpcRetreatAction;
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy;
import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.combat.attack.MeleeCombatAttack;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.damage.CombatDamageAction;
import io.luna.game.model.mob.combat.damage.CombatDamageType;

import java.util.Set;

/**
 * A {@link CombatContext} implementation for {@link Npc}s.
 * <p>
 * This class provides NPC-specific combat behavior.
 *
 * @author lare96
 */
public final class NpcCombatContext extends CombatContext<Npc> {

    /**
     * Cached immutable set of NPC ids that should be treated as bosses.
     */
    private static volatile ImmutableSet<Integer> BOSSES = ImmutableSet.of();

    /**
     * Replaces the current boss id set with an immutable snapshot of the supplied ids.
     *
     * @param bosses The boss ids to cache.
     */
    public static void setBosses(Set<Integer> bosses) {
        BOSSES = ImmutableSet.copyOf(bosses);
    }

    /**
     * Checks whether the given npc id is currently registered as a boss.
     *
     * @param id The npc id to test.
     * @return {@code true} if the id exists in the cached boss set, otherwise {@code false}.
     */
    public static boolean isBoss(int id) {
        return BOSSES.contains(id);
    }

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
        return mob.getMaxHit() > 0 ? mob.getMaxHit() : mob.combatDef().getMaximumHit();
    }

    @Override
    public CombatAttack<?> getNextAttack(Mob victim) {
        return NpcCombatHandler.INSTANCE.supplyAttack(mob, victim);
    }

    @Override
    public void onNextDefence(Mob attacker, CombatDamage damage, CombatDamageAction action) {
        NpcCombatHandler.INSTANCE.consumeDefence(mob, attacker, action);
    }

    @Override
    public EquipmentBonus getAttackStyleBonus() {
        EquipmentBonus bonus = mob.combatDef().getDefaultAttackBonus();
        return bonus != null ? bonus : mob.combatDef().findHighestAttackBonus();
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
    public CombatAttack<Npc> getDefaultAttack(Mob victim) {
        return new MeleeCombatAttack<>(mob, victim, mob.combatDef().getAttackAnimation(), 1, mob.combatDef().getAttackSpeed());
    }

    @Override
    public boolean isAttackable() {
        return mob.isAlive() &&
                mob.combatDef().getLevel() > 0 &&
                mob.def().getActions().contains("Attack");
    }

    /**
     * Plays the default defence animation for this NPC.
     * <p>
     * The animation ID is taken directly from the NPC's combat definition.
     */
    public void handleDefaultDefence() {
        int id = mob.combatDef().getDefenceAnimation();
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