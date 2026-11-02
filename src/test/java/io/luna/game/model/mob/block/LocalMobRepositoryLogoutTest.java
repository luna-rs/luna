package io.luna.game.model.mob.block;

import io.luna.LunaContext;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.MobReferenceFixtures;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerCredentials;
import io.luna.game.model.mob.bot.Bot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies observer detachment when a session stops receiving local-view updates.
 *
 * @author lare96
 */
class LocalMobRepositoryLogoutTest {

    @BeforeAll
    static void bindings() {
        api.bot.script.InventoryProductionFixtures.INSTANCE.initializeCombat();
    }

    @Test
    void logoutDetachesNpcAndBotObserversAndClearsBothViews() {
        Player owner = player("departing");
        Player remaining = player("remaining");
        Npc npc = npc();
        Bot bot = bot();
        Player ordinary = player("ordinary");
        LocalMobRepository repository = owner.getLocalMobs();
        updating(() -> {
            assertTrue(repository.add(npc));
            assertTrue(repository.add(bot));
            assertTrue(repository.add(ordinary));
            assertTrue(remaining.getLocalMobs().add(npc));
            assertTrue(remaining.getLocalMobs().add(bot));
            assertEquals(2, repository.updatingPlayersCount());
            assertEquals(1, repository.updatingNpcsCount());
        });

        repository.clearForLogout();

        assertEquals(Set.of(remaining), players(npc.getLocalHumans()));
        assertEquals(Set.of(remaining), players(bot.getLocalHumans()));
        assertEquals(0, repository.localPlayersCount());
        assertEquals(0, repository.localNpcsCount());
        assertFalse(repository.inLocalView(npc));
        assertFalse(repository.inLocalView(bot));
        updating(() -> {
            assertEquals(0, repository.updatingPlayersCount());
            assertEquals(0, repository.updatingNpcsCount());
            assertTrue(remaining.getLocalMobs().inUpdatingView(npc));
            assertTrue(remaining.getLocalMobs().inUpdatingView(bot));
        });
    }

    @Test
    void repeatedCleanupKeepsAReconnectedSessionWithTheSameUsername() {
        Player oldSession = player("returning");
        Player newSession = player("returning");
        assertNotSame(oldSession, newSession);
        assertEquals(oldSession, newSession);
        Npc npc = npc();
        Bot bot = bot();
        LocalMobRepository oldView = oldSession.getLocalMobs();
        updating(() -> {
            oldView.add(npc);
            oldView.add(bot);
        });
        oldView.clearForLogout();
        updating(() -> {
            newSession.getLocalMobs().add(npc);
            newSession.getLocalMobs().add(bot);
        });

        oldView.clearForLogout();

        assertSame(newSession, npc.getLocalHumans().iterator().next());
        assertSame(newSession, bot.getLocalHumans().iterator().next());
        assertEquals(1, npc.getLocalHumans().size());
        assertEquals(1, bot.getLocalHumans().size());
    }

    @Test
    void cleanupMatchesTheSessionIdentityRatherThanUsernameEquality() {
        Player oldSession = player("returning");
        Player newSession = player("returning");
        Npc npc = npc();
        Bot bot = bot();
        LocalMobRepository repository = oldSession.getLocalMobs();
        updating(() -> {
            repository.add(npc);
            repository.add(bot);
        });
        // Simulate a reverse observer already replaced while the old repository still contains its outgoing view.
        npc.getLocalHumans().clear();
        npc.getLocalHumans().add(newSession);
        bot.getLocalHumans().clear();
        bot.getLocalHumans().add(newSession);

        repository.clearForLogout();

        assertSame(newSession, npc.getLocalHumans().iterator().next());
        assertSame(newSession, bot.getLocalHumans().iterator().next());
        assertEquals(0, repository.localPlayersCount());
        assertEquals(0, repository.localNpcsCount());
    }

    @Test
    void offGameThreadCleanupFailsBeforeChangingAnyCollections() {
        Player owner = player("departing");
        Npc npc = npc();
        Bot bot = bot();
        LocalMobRepository repository = owner.getLocalMobs();
        updating(() -> {
            repository.add(npc);
            repository.add(bot);
        });
        when(owner.getContext().getGame().getThread()).thenReturn(new Thread());

        assertThrows(IllegalStateException.class, repository::clearForLogout);

        assertSame(owner, npc.getLocalHumans().iterator().next());
        assertSame(owner, bot.getLocalHumans().iterator().next());
        assertEquals(1, repository.localPlayersCount());
        assertEquals(1, repository.localNpcsCount());
        updating(() -> {
            assertEquals(1, repository.updatingPlayersCount());
            assertEquals(1, repository.updatingNpcsCount());
        });
    }

    @Test
    void normalUpdatePruningStillRemovesObserverMembership() {
        Player owner = player("departing");
        Npc npc = npc();
        Bot bot = bot();
        LocalMobRepository repository = owner.getLocalMobs();
        updating(() -> {
            repository.add(npc);
            repository.add(bot);
            repository.forUpdatingNpcs(other -> {
                repository.removeLocal(other);
                return true;
            });
            repository.forUpdatingPlayers(other -> {
                repository.removeLocal(other);
                return true;
            });
        });

        repository.clearForLogout();

        assertTrue(npc.getLocalHumans().isEmpty());
        assertTrue(bot.getLocalHumans().isEmpty());
    }

    private static Player player(String username) {
        LunaContext context = mock(LunaContext.class, RETURNS_DEEP_STUBS);
        when(context.getGame().getThread()).thenReturn(Thread.currentThread());
        Player player = new Player(context, new PlayerCredentials(username, "fixture"));
        MobReferenceFixtures.register(player, 1);
        return player;
    }

    private static Npc npc() {
        Npc npc = mock(Npc.class);
        when(npc.asNpc()).thenReturn(npc);
        when(npc.getLocalHumans()).thenReturn(new LocalHumanView());
        return npc;
    }

    private static Bot bot() {
        Bot bot = mock(Bot.class);
        when(bot.asPlr()).thenReturn(bot);
        when(bot.asBot()).thenReturn(bot);
        when(bot.isBot()).thenReturn(true);
        when(bot.getLocalHumans()).thenReturn(new LocalHumanView());
        return bot;
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

    private static Set<Player> players(LocalHumanView view) {
        Set<Player> players = new HashSet<>();
        view.forEach(players::add);
        return players;
    }
}
