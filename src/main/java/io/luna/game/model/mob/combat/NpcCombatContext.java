package io.luna.game.model.mob.combat;

import io.luna.game.action.impl.NpcRetreatAction;
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
        switch (npc.getRetreatPolicy()) {
            case LOW_HEALTH:
                if (npc.getHealthPercent() <= 0.25) {
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
}