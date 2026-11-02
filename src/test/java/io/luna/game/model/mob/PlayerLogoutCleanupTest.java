package io.luna.game.model.mob;

import io.luna.LunaContext;
import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.event.impl.LogoutEvent;
import io.luna.game.model.EntityState;
import io.luna.game.model.mob.block.LocalHumanView;
import io.luna.game.model.mob.combat.state.NpcCombatContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that logout closes the departing player's own visibility and actions without enumerating the world.
 *
 * @author lare96
 */
class PlayerLogoutCleanupTest {

    @BeforeAll
    static void bindings() {
        api.bot.script.InventoryProductionFixtures.INSTANCE.initializeCombat();
    }

    @Test
    void inactivityClosesTheOwnedViewBeforeTheLogoutEvent() {
        LunaContext context = context();
        Player departing = player(context, "departing", 1);
        Npc npc = npc();
        updating(() -> departing.getLocalMobs().add(npc));
        assertSame(departing, npc.getLocalHumans().iterator().next());
        forbidWorldScan(context);
        var plugins = context.getPlugins();
        doAnswer(invocation -> {
            assertTrue(npc.getLocalHumans().isEmpty());
            assertEquals(0, departing.getLocalMobs().localNpcsCount());
            return null;
        }).when(plugins).post(any(LogoutEvent.class));

        departing.setState(EntityState.INACTIVE);

        assertTrue(npc.getLocalHumans().isEmpty());
        updating(() -> assertEquals(0, departing.getLocalMobs().updatingNpcsCount()));
        verify(plugins).post(any(LogoutEvent.class));
    }

    @Test
    void closingOneViewPreservesTheOtherSessionWithTheSameUsername() {
        LunaContext context = context();
        Player departing = player(context, "returning", 1);
        Player replacement = player(context, "returning", 2);
        assertEquals(departing, replacement);
        Npc npc = npc();
        updating(() -> {
            departing.getLocalMobs().add(npc);
            replacement.getLocalMobs().add(npc);
        });
        forbidWorldScan(context);

        departing.setState(EntityState.INACTIVE);

        assertEquals(1, npc.getLocalHumans().size());
        assertSame(replacement, npc.getLocalHumans().iterator().next());
        assertTrue(replacement.getLocalMobs().inLocalView(npc));
    }

    @Test
    void logoutInterruptsAnOwnedFinishingActionAndReleasesItsNpcReservation() {
        LunaContext context = context();
        Player departing = player(context, "departing", 1);
        Npc npc = npc();
        NpcCombatContext combat = new NpcCombatContext(npc);
        when(npc.getCombat()).thenReturn(combat);
        AtomicBoolean finished = new AtomicBoolean();
        Action<Player> action = new Action<>(departing, ActionType.SOFT) {
            @Override
            public boolean run() {
                return false;
            }

            @Override
            public void onFinished() {
                finished.set(true);
                combat.clearExecution(this);
            }
        };
        departing.getActions().submit(action);
        combat.trackExecution(action);
        assertTrue(combat.isExecutionPending());
        forbidWorldScan(context);

        departing.setState(EntityState.INACTIVE);

        assertTrue(finished.get());
        assertTrue(action.isFinished());
        assertEquals(0, departing.getActions().size(ActionType.SOFT));
        assertFalse(combat.isExecutionPending());
    }

    @Test
    void anUnobservedPlayerCanLeaveWithoutAnyWorldEnumeration() {
        LunaContext context = context();
        Player departing = player(context, "departing", 1);
        forbidWorldScan(context);

        departing.setState(EntityState.INACTIVE);

        verify(context.getPlugins()).post(any(LogoutEvent.class));
    }

    private static LunaContext context() {
        LunaContext context = mock(LunaContext.class, RETURNS_DEEP_STUBS);
        when(context.getGame().getThread()).thenReturn(Thread.currentThread());
        return context;
    }

    private static Player player(LunaContext context, String username, int slot) {
        Player player = new Player(context, new PlayerCredentials(username, "fixture"));
        MobReferenceFixtures.register(player, slot);
        return player;
    }

    private static Npc npc() {
        Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        when(npc.asNpc()).thenReturn(npc);
        when(npc.getLocalHumans()).thenReturn(new LocalHumanView());
        return npc;
    }

    private static void forbidWorldScan(LunaContext context) {
        var players = context.getWorld().getPlayers();
        var npcs = context.getWorld().getNpcs();
        doThrow(new AssertionError("Logout must not enumerate world players.")).when(players).iterator();
        doThrow(new AssertionError("Logout must not enumerate world players.")).when(players).stream();
        doThrow(new AssertionError("Logout must not enumerate world NPCs.")).when(npcs).iterator();
        doThrow(new AssertionError("Logout must not enumerate world NPCs.")).when(npcs).stream();
    }

    private static void updating(Runnable action) {
        Thread thread = Thread.currentThread();
        String previousName = thread.getName();
        thread.setName("PlayerUpdatingThread");
        try {
            action.run();
        } finally {
            thread.setName(previousName);
        }
    }
}
