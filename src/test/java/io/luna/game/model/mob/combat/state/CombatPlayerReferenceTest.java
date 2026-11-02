package io.luna.game.model.mob.combat.state;

import io.luna.game.model.EntityState;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.CombatAttackHistory;
import io.luna.game.model.mob.combat.CombatDamageHistory;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.damage.CombatDamageType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.luna.game.model.mob.MobReferenceFixtures.register;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that persistent combat state stores identities and values instead of owning another session.
 *
 * @author lare96
 */
class CombatPlayerReferenceTest {

    /**
     * A registered NPC with real combat state and an isolated world.
     *
     * @author lare96
     */
    private static final class Fixture {
        final Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        final NpcCombatContext combat;

        Fixture() {
            register(npc, 1);
            combat = new NpcCombatContext(npc);
            when(npc.getCombat()).thenReturn(combat);
        }

        Player player(int slot) {
            Player player = mock(Player.class, RETURNS_DEEP_STUBS);
            when(player.asPlr()).thenReturn(player);
            register(player, slot);
            return player;
        }

        CombatAttack<?> attack(Mob attacker, Mob victim) {
            CombatAttack<Mob> attack = mock(CombatAttack.class);
            when(attack.getAttacker()).thenReturn(attacker);
            when(attack.getVictim()).thenReturn(victim);
            when(attack.getTimestamp()).thenReturn(Instant.ofEpochMilli(1234));
            when(attack.getLaunchTick()).thenReturn(42L);
            doCallRealMethod().when(attack).getHistory();
            return attack;
        }
    }

    @Test
    void targetsAndOpponentResolveToNullAsSoonAsTheOriginalSessionLeaves() {
        Fixture f = new Fixture();
        Player player = f.player(2);
        f.combat.setTarget(player);
        f.combat.setAutoRetaliateTarget(player);
        f.combat.setLastCombatWith(player);
        assertSame(player, f.combat.getTarget());
        assertSame(player, f.combat.getAutoRetaliateTarget());
        assertSame(player, f.combat.getLastCombatWith());

        when(player.getState()).thenReturn(EntityState.INACTIVE);

        assertNull(f.combat.getTarget());
        assertNull(f.combat.getAutoRetaliateTarget());
        assertNull(f.combat.getLastCombatWith());
    }

    @Test
    void allRecipientsShareOneSnapshotWithoutRetainingTheExecutableAttack() {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        Player player = first.player(2);
        CombatAttack<?> source = first.attack(player, first.npc);
        first.combat.setLastAttackSent(source);
        first.combat.setLastAttackReceived(source);
        second.combat.setLastAttackReceived(source);
        CombatAttackHistory history = source.getHistory();
        assertSame(history, first.combat.getLastAttackSent());
        assertSame(history, first.combat.getLastAttackReceived());
        assertSame(history, second.combat.getLastAttackReceived());
        assertSame(player, history.getAttacker());
        assertSame(first.npc, history.getVictim());
        assertEquals(Instant.ofEpochMilli(1234), history.getTimestamp());
        assertEquals(42, history.getLaunchTick());
        assertNotSame(history, first.attack(player, first.npc).getHistory());

        when(player.getState()).thenReturn(EntityState.INACTIVE);

        assertSame(history, first.combat.getLastAttackReceived());
        assertNull(history.getAttacker());
        assertEquals(42, history.getLaunchTick());
    }

    @Test
    void damageHistoryKeepsAppliedValuesAfterItsAttackerLeaves() {
        Fixture f = new Fixture();
        Player player = f.player(2);
        CombatDamage damage = mock(CombatDamage.class);
        when(damage.getAttacker()).thenReturn(player);
        when(damage.getVictim()).thenReturn(f.npc);
        when(damage.getType()).thenReturn(CombatDamageType.MAGIC);
        when(damage.getRawAmount()).thenReturn(6);
        when(damage.isAccurate()).thenReturn(true);
        f.combat.setLastDamageReceived(damage);
        CombatDamageHistory history = f.combat.getLastDamageReceived();
        assertSame(player, history.getAttacker());
        assertSame(f.npc, history.getVictim());

        when(player.getState()).thenReturn(EntityState.INACTIVE);

        assertNull(history.getAttacker());
        assertEquals(6, history.getRawAmount());
        assertEquals(CombatDamageType.MAGIC, history.getType());
        assertTrue(history.isAccurate());
    }

    @Test
    void replacingAWorldSlotCannotRebindHistoryToASameNameLogin() {
        Fixture f = new Fixture();
        Player original = f.player(2);
        when(original.getUsernameHash()).thenReturn(123L);
        CombatAttackHistory history = f.attack(original, f.npc).getHistory();
        f.combat.setTarget(original);
        f.combat.setLastCombatWith(original);
        Player replacement = mock(Player.class, RETURNS_DEEP_STUBS);
        var world = original.getWorld();
        when(replacement.getWorld()).thenReturn(world);
        when(replacement.getUsernameHash()).thenReturn(123L);
        register(replacement, 2);

        assertNull(history.getAttacker());
        assertNull(f.combat.getTarget());
        assertNull(f.combat.getLastCombatWith());
        f.combat.setTarget(replacement);
        assertSame(replacement, f.combat.getTarget());
    }

    @Test
    void historyValuesHaveNoFieldsThatOwnParticipantsOrExecutableSources() {
        for (Class<?> value : new Class<?>[]{CombatAttackHistory.class, CombatDamageHistory.class}) {
            for (var field : value.getDeclaredFields()) {
                assertFalse(Mob.class.isAssignableFrom(field.getType()), field.toString());
                assertFalse(CombatAttack.class.isAssignableFrom(field.getType()), field.toString());
                assertFalse(CombatDamage.class.isAssignableFrom(field.getType()), field.toString());
            }
        }
    }
}
