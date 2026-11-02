package io.luna.game.model.mob.combat.damage;

import io.luna.game.model.EntityState;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.combat.state.NpcCombatContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Exercises real damage delivery after the shooter logs out without retaining its session as NPC combat history.
 *
 * @author lare96
 */
class LoggedOutProjectileHistoryTest {

    @Test
    void deliversTheHitAndDamageCreditWithoutRestoringTheDepartedPlayerReference() {
        Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        Player shooter = mock(Player.class, RETURNS_DEEP_STUBS);
        Player replacement = mock(Player.class);
        AtomicLong tick = new AtomicLong(100);
        AtomicBoolean interrupted = new AtomicBoolean(true);
        NpcCombatContext combat = new NpcCombatContext(npc);
        when(npc.getCombat()).thenReturn(combat);
        when(npc.getWorld().getCurrentTick()).thenAnswer(call -> tick.get());
        when(npc.getState()).thenReturn(EntityState.ACTIVE);
        when(npc.isAlive()).thenReturn(true);
        when(npc.getHealth()).thenReturn(5);
        io.luna.game.model.mob.MobReferenceFixtures.register(npc, 1);
        io.luna.game.model.mob.MobReferenceFixtures.register(shooter, 1);
        when(shooter.asPlr()).thenReturn(shooter);
        when(shooter.getUsernameHash()).thenReturn(42L);
        when(npc.getWorld().getPlayer(42L)).thenReturn(Optional.of(replacement));
        CombatAttack<Player> source = mock(CombatAttack.class);
        when(source.getAttacker()).thenReturn(shooter);
        when(source.getVictim()).thenReturn(npc);
        doCallRealMethod().when(source).getHistory();
        CombatDamage damage = new CombatDamage(shooter, npc, CombatDamageType.RANGED, OptionalInt.of(8), true);
        combat.markAttackedBy(shooter);
        combat.getImpacts().schedule(2, () -> damage.apply(source), interrupted::set);

        when(shooter.getState()).thenReturn(EntityState.INACTIVE);
        tick.set(102);
        combat.getImpacts().process();

        assertFalse(interrupted.get());
        verify(npc).damage(5);
        ArgumentCaptor<CombatDamage> applied = ArgumentCaptor.forClass(CombatDamage.class);
        verify(source).onDamageApplied(applied.capture());
        assertEquals(5, applied.getValue().getRawAmount());
        assertSame(shooter, applied.getValue().getAttacker());
        assertSame(replacement, combat.getDamageStack().getHighestDamage());
        assertNotNull(combat.getLastAttackReceived());
        assertNull(combat.getLastAttackReceived().getAttacker());
        assertEquals(5, combat.getLastDamageReceived().getRawAmount());
        assertNull(combat.getLastDamageReceived().getAttacker());
        assertNull(combat.getLastCombatWith());
        assertNull(combat.getLastAttackingPlayer());
        combat.getImpacts().process();
        verify(npc, times(1)).damage(5);
    }
}
