package io.luna.game.model.mob.combat;

import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.block.Animation.AnimationPriority;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.mob.interact.InteractionType;
import io.luna.util.RandomUtils;

public final class NpcCombatContext extends CombatContext {
    private final Npc npc;

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
}
