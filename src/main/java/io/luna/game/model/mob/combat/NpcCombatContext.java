package io.luna.game.model.mob.combat;

import io.luna.game.action.impl.NpcRetreatAction;
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy;
import io.luna.game.model.Direction;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.block.Animation.AnimationPriority;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.mob.interact.InteractionType;
import io.luna.util.RandomUtils;

/**
 * A {@link CombatContext} implementation for {@link Npc}s.
 * <p>
 * This class provides NPC-specific combat behavior.
 *
 * @author lare96
 */
public final class NpcCombatContext extends CombatContext {

    /**
     * The owning NPC.
     */
    private final Npc npc;

    /**
     * The retreating policy of this NPC.
     */
    private RetreatPolicy retreatPolicy = RetreatPolicy.NONE;

    /**
     * Creates a new {@link NpcCombatContext}.
     *
     * @param npc The owning NPC.
     */
    public NpcCombatContext(Npc npc) {
        super(npc);
        this.npc = npc;
    }

    @Override
    public int computeMaxHit(CombatDamageType type) {
        return RandomUtils.inclusive(npc.getCombatDef().getMaximumHit());
    }

    @Override
    public InteractionPolicy computeInteractionPolicy() {
        // TODO Changes depending on NPC and combat attack type.
        return new InteractionPolicy(InteractionType.SIZE, 1);
    }

    @Override
    public int computeAttackSpeed() {
        return npc.getCombatDef().getAttackSpeed();
    }

    @Override
    public Animation getAttackAnimation() {
        return new Animation(npc.getCombatDef().getAttackAnimation(), AnimationPriority.HIGH);
    }

    @Override
    public Animation getDefenceAnimation() {
        return new Animation(npc.getCombatDef().getDefenceAnimation());
    }

    @Override
    public boolean isAutoRetaliate() {
        return true;
    }

    @Override
    public boolean onCombatHook(boolean reached) {
        boolean retreat = false;
        switch (retreatPolicy) {
            case LOW_HEALTH:
                if (npc.getHealthPercent() <= 25) {
                    retreat = true;
                }
                break;
            case DISTANCE:
                if (!npc.getBasePosition().isViewable(npc)) {
                    retreat = true;
                }
                break;
        }
        if (retreat) {
            npc.getActions().submitIfAbsent(new NpcRetreatAction(npc));
            return false;
        }

        Mob target = getTarget();
        if (target != null && target.getPosition().equals(npc.getPosition()) && reached) {
            Direction inverseLastDir = target.getLastDirection().opposite();
            if (!npc.getNavigator().step(inverseLastDir)) {
                npc.getNavigator().stepRandom(false);
            }
        }
        return true;
    }

    /**
     * Returns whether this NPC is currently retreating.
     * <p>
     * An NPC is considered retreating if its action queue currently contains an active {@link NpcRetreatAction}.
     *
     * @return {@code true} if this NPC is retreating, otherwise {@code false}.
     */
    public boolean isRetreating() {
        return npc.getActions().contains(NpcRetreatAction.class);
    }

    /**
     * Returns the retreat policy assigned to this NPC.
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
     * Sets the retreat policy for this NPC.
     * <p>
     * The retreat policy controls how this NPC should respond when its retreat logic is triggered.
     *
     * @param retreatPolicy The retreat policy to assign.
     */
    public void setRetreatPolicy(RetreatPolicy retreatPolicy) {
        this.retreatPolicy = retreatPolicy;
    }
}