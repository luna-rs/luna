package io.luna.game.model.mob.combat.state;

import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that an NPC's death sound follows its last incoming player attack across target changes and respawns.
 *
 * @author lare96
 */
class NpcCombatSoundRecipientTest {

    @Test
    void incomingPlayerAttacksReplaceTheRecipientWithoutRequiringDamage() {
        Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        NpcCombatContext combat = new NpcCombatContext(npc);
        when(npc.getCombat()).thenReturn(combat);
        Player first = mock(Player.class);
        Player second = mock(Player.class);

        assertNull(combat.getLastAttackingPlayer());
        combat.markAttackedBy(first);
        assertSame(first, combat.getLastAttackingPlayer());
        combat.markAttackedBy(second);
        assertSame(second, combat.getLastAttackingPlayer());
    }

    @Test
    void outgoingTargetsAndNpcAttackersDoNotReplaceTheLastAttackingPlayer() {
        Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        NpcCombatContext combat = new NpcCombatContext(npc);
        when(npc.getCombat()).thenReturn(combat);
        Player attacker = mock(Player.class);
        Player outgoingTarget = mock(Player.class);

        combat.markAttackedBy(attacker);
        combat.setLastCombatWith(outgoingTarget);
        assertSame(attacker, combat.getLastAttackingPlayer());
        combat.markAttackedBy(mock(Npc.class));
        assertSame(attacker, combat.getLastAttackingPlayer());
    }

    @Test
    void respawnResetClearsTheRecipientFromThePreviousLife() {
        Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        NpcCombatContext combat = new NpcCombatContext(npc);
        when(npc.getCombat()).thenReturn(combat);
        combat.markAttackedBy(mock(Player.class));

        combat.reset();

        assertNull(combat.getLastAttackingPlayer());
    }
}
