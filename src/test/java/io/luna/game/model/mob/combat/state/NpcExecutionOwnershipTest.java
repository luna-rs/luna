package io.luna.game.model.mob.combat.state;

import io.luna.game.action.Action;
import io.luna.game.action.ActionQueue;
import io.luna.game.action.impl.LockedAction;
import io.luna.game.model.EntityState;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that the player's queue owns finishing actions and the NPC holds only a non-owning reservation.
 *
 * @author lare96
 */
class NpcExecutionOwnershipTest {

    @Test
    void clearingThePlayerQueueReleasesItsNpcReservation() {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        when(player.getState()).thenReturn(EntityState.ACTIVE);
        Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        NpcCombatContext combat = new NpcCombatContext(npc);
        ActionQueue queue = new ActionQueue(player);
        LockedAction finish = new LockedAction(player) {
            @Override
            public boolean run() {
                return false;
            }

            @Override
            public void onUnlock() {
                combat.clearExecution(this);
            }
        };
        combat.trackExecution(finish);
        queue.submit(finish);
        assertTrue(combat.isExecutionPending());

        queue.clear();

        assertFalse(combat.isExecutionPending());
        assertTrue(finish.isFinished());
        verify(player).unlock();
    }

    @Test
    void resettingTheNpcInterruptsAnActionStillOwnedByThePlayerQueue() {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        when(player.getState()).thenReturn(EntityState.ACTIVE);
        Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        NpcCombatContext combat = new NpcCombatContext(npc);
        ActionQueue queue = new ActionQueue(player);
        LockedAction finish = new LockedAction(player) {
            @Override
            public boolean run() {
                return false;
            }

            @Override
            public void onUnlock() {
                combat.clearExecution(this);
            }
        };
        queue.submit(finish);
        combat.trackExecution(finish);

        combat.reset();

        assertFalse(combat.isExecutionPending());
        assertTrue(finish.isFinished());
        verify(player).unlock();
    }

    @Test
    void anEarlierFinisherCannotClearItsReplacement() {
        Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        NpcCombatContext combat = new NpcCombatContext(npc);
        Action<?> previous = mock(Action.class, RETURNS_DEEP_STUBS);
        Action<?> replacement = mock(Action.class, RETURNS_DEEP_STUBS);
        combat.trackExecution(previous);
        combat.trackExecution(replacement);

        combat.clearExecution(previous);
        assertTrue(combat.isExecutionPending());
        combat.clearExecution(replacement);
        assertFalse(combat.isExecutionPending());
    }

    @Test
    void aPlayerDeathDoesNotCancelAnAlreadyStartedFinishWhileItsActionRemainsOwned() {
        Player player = mock(Player.class);
        when(player.getState()).thenReturn(EntityState.ACTIVE);
        when(player.isAlive()).thenReturn(false);
        Action<Player> finish = mock(Action.class);
        when(finish.getMob()).thenReturn(player);
        NpcCombatContext combat = new NpcCombatContext(mock(Npc.class, RETURNS_DEEP_STUBS));
        combat.trackExecution(finish);

        assertTrue(combat.isExecutionPending());
        when(player.getState()).thenReturn(EntityState.INACTIVE);
        assertFalse(combat.isExecutionPending());
    }
}
