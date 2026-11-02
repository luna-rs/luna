package io.luna.game.model.mob.combat;

import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.MobReference;
import io.luna.game.model.mob.combat.attack.CombatAttack;

import java.time.Instant;

/**
 * Describes a launched attack without retaining its executable source or either participant.
 * One snapshot is shared by every history entry for the same launch, including secondary and repeated hits.
 *
 * @author lare96
 */
public final class CombatAttackHistory {

    private final MobReference attacker;
    private final MobReference victim;
    private final Instant timestamp;
    private final long launchTick;

    /**
     * Copies the source's launch metadata and non-owning participant identities.
     *
     * @param source The launched attack being described.
     */
    public CombatAttackHistory(CombatAttack<?> source) {
        attacker = MobReference.capture(source.getAttacker());
        victim = MobReference.capture(source.getVictim());
        timestamp = source.getTimestamp();
        launchTick = source.getLaunchTick();
    }

    public Mob getAttacker() {
        return attacker == null ? null : attacker.resolve();
    }

    public Mob getVictim() {
        return victim == null ? null : victim.resolve();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public long getLaunchTick() {
        return launchTick;
    }
}
