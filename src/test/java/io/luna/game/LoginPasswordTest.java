package io.luna.game;

import io.luna.Luna;
import io.luna.game.model.EntityState;
import io.luna.game.model.Entity;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.PlayerCredentials;
import io.luna.game.model.mob.PlayerRights;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.PlayerAutomation;
import io.luna.game.persistence.PlayerData;
import io.luna.net.client.LoginClient;
import io.luna.net.msg.login.LoginRequestMessage;
import io.luna.net.msg.login.LoginResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mindrot.jbcrypt.BCrypt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exercises credential preparation across worker, game-thread and persistence completion boundaries.
 *
 * @author lare96
 */
final class LoginPasswordTest {
    private World world;
    private Player player;
    private GameSettings settings;
    private LoginService service;
    private ManualWorkers workers;
    private MockedStatic<Luna> luna;
    private final HashMap<String, Player> online = new HashMap<>();
    private final ArrayDeque<Runnable> gameTasks = new ArrayDeque<>();
    private final List<CompletableFuture<Void>> saves = new ArrayList<>();
    private final List<String> savedHashes = new ArrayList<>();
    private final List<Thread> hashThreads = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        luna = mockStatic(Luna.class);
        settings = mock(GameSettings.class);
        var serverSettings = mock(io.luna.LunaSettings.class, RETURNS_DEEP_STUBS);
        when(serverSettings.game()).thenReturn(settings);
        when(settings.passwordStrength()).thenReturn(GameSettings.PasswordStrength.DEFAULT);
        luna.when(Luna::settings).thenReturn(serverSettings);
        world = mock(World.class, RETURNS_DEEP_STUBS);
        when(world.getPlayerMap()).thenReturn(online);
        when(world.getSerializerManager().getSerializer().loadPlayer(eq(world), anyString())).thenReturn(null);
        var game = world.getContext().getGame();
        doAnswer(call -> {
            Runnable action = call.getArgument(0);
            var future = new CompletableFuture<Void>();
            gameTasks.add(() -> {
                try {
                    action.run();
                    future.complete(null);
                } catch (Throwable error) {
                    future.completeExceptionally(error);
                }
            });
            return future;
        }).when(game).sync(any(Runnable.class));
        workers = new ManualWorkers();
        service = service(password -> {
            hashThreads.add(Thread.currentThread());
            return BCrypt.hashpw(password, BCrypt.gensalt(4));
        });
        player = player(Player.class);
        online.put(player.getUsername(), player);
    }

    @AfterEach
    void tearDown() {
        try {
            if (workers != null) workers.shutdownNow();
        } finally {
            if (luna != null) luna.close();
        }
    }

    @Test
    void newAccountHashIsPreparedOffGameThreadBeforeRegistrationAndReusedBySnapshots() throws Exception {
        online.clear();
        when(player.getState()).thenReturn(EntityState.NEW);
        LoginClient client = loginClient();
        var request = request(client);
        assertTrue(service.addRequest(player.getUsername(), request));
        assertTrue(hashThreads.isEmpty());
        assertNull(player.getHashedPassword());

        workers.runFirst();
        assertEquals(1, hashThreads.size());
        assertNotSame(Thread.currentThread(), hashThreads.getFirst());
        assertNull(player.getHashedPassword());
        assertTrue(service.canFinishRequest(player.getUsername(), request));
        service.finishRequest(player.getUsername(), request);

        String hash = player.getHashedPassword();
        assertTrue(BCrypt.checkpw("initial", hash));
        var order = inOrder(player, world.getPlayers());
        order.verify(player).setHashedPassword(hash);
        order.verify(world.getPlayers()).add(player);
        order.verify(player).setState(EntityState.ACTIVE);
        for (int i = 0; i < 900; i++) {
            assertSame(hash, new PlayerData(player.getUsername()).save(player).password);
        }
        assertEquals(1, hashThreads.size());
    }

    @Test
    void preparedHashBelongsToTheFinalScriptCapableLoginEntity() throws Exception {
        online.clear();
        Player finalPlayer = player(Player.class);
        when(player.getState()).thenReturn(EntityState.NEW);
        LoginClient client = loginClient();
        var request = request(client);
        service.addRequest(player.getUsername(), request);
        workers.runFirst();
        try (var automation = mockStatic(PlayerAutomation.class)) {
            automation.when(() -> PlayerAutomation.prepareLoginPlayer(eq(player), any())).thenReturn(finalPlayer);
            service.finishRequest(player.getUsername(), request);
        }
        assertNull(player.getHashedPassword());
        assertTrue(BCrypt.checkpw("initial", finalPlayer.getHashedPassword()));
        verify(world.getPlayers()).add(finalPlayer);
    }

    @Test
    void returningAccountReusesItsStoredHash() throws Exception {
        online.clear();
        when(player.getState()).thenReturn(EntityState.NEW);
        PlayerData data = new PlayerData(player.getUsername());
        data.password = BCrypt.hashpw("initial", BCrypt.gensalt(4));
        data.rights = PlayerRights.PLAYER;
        when(world.getSerializerManager().getSerializer().loadPlayer(world, player.getUsername())).thenReturn(data);
        var request = request(loginClient());
        service.addRequest(player.getUsername(), request);
        workers.runFirst();
        service.finishRequest(player.getUsername(), request);
        assertSame(data.password, player.getHashedPassword());
        assertTrue(hashThreads.isEmpty());
    }

    @Test
    void failedNewAccountHashDoesNotRegisterOrSaveThePlayer() throws Exception {
        online.clear();
        when(player.getState()).thenReturn(EntityState.NEW);
        service = service(password -> { throw new IllegalStateException("Hash worker failed"); });
        LoginClient client = loginClient();
        var request = request(client);
        service.addRequest(player.getUsername(), request);
        workers.runFirst();
        service.finishRequest(player.getUsername(), request);
        verify(client).disconnect();
        verify(world.getPlayers(), never()).add(any());
        verify(player, never()).save();
    }

    @Test
    void changedPasswordIsInstalledOnGameThreadAndConfirmedOnlyAfterSaving() throws Exception {
        var result = service.changePassword(player, "changed");
        assertEquals("initial", player.getPassword());
        assertTrue(hashThreads.isEmpty());
        workers.runFirst();
        assertNotSame(Thread.currentThread(), hashThreads.getFirst());
        assertEquals("initial", player.getPassword());
        runGameTasks();
        assertEquals("changed", player.getPassword());
        assertTrue(BCrypt.checkpw("changed", player.getHashedPassword()));
        assertEquals(List.of(player.getHashedPassword()), savedHashes);
        assertFalse(result.isDone());
        saves.getFirst().complete(null);
        assertFalse(result.isDone());
        runGameTasks();
        assertTrue(result.join());
    }

    @Test
    void changedPasswordUsesTheSameCanonicalFormAsOrdinaryLogin() throws Exception {
        var result = service.changePassword(player, "  UpperCase  ");
        workers.runFirst();
        runGameTasks();
        saves.getFirst().complete(null);
        runGameTasks();
        assertTrue(result.join());
        assertEquals("uppercase", player.getPassword());
        var saved = new PlayerData(player.getUsername());
        saved.password = player.getHashedPassword();
        LoginClient client = mock(LoginClient.class);
        when(client.getLoginResponse(any(), any())).thenCallRealMethod();
        assertEquals(LoginResponse.NORMAL, client.getLoginResponse(saved,
                PlayerCredentials.normalizePassword("  UPPERcase  ")));
    }

    @Test
    void passwordCanonicalizationDoesNotDependOnServerLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("initial", PlayerCredentials.normalizePassword("  INITIAL  "));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void latestRequestWinsEvenWhenItsPasswordMatchesTheOriginal() throws Exception {
        var older = service.changePassword(player, "older");
        var latest = service.changePassword(player, "initial");
        workers.runLast();
        runGameTasks();
        saves.getFirst().complete(null);
        runGameTasks();
        workers.runFirst();
        runGameTasks();
        assertTrue(latest.join());
        assertFalse(older.join());
        assertEquals("initial", player.getPassword());
        assertTrue(BCrypt.checkpw("initial", player.getHashedPassword()));
        assertEquals(1, saves.size());
    }

    @Test
    void logoutWhileHashingDiscardsThePreparedPassword() throws Exception {
        var result = service.changePassword(player, "changed");
        online.clear();
        when(player.getState()).thenReturn(EntityState.INACTIVE);
        workers.runFirst();
        runGameTasks();
        assertFalse(result.join());
        assertEquals("initial", player.getPassword());
        verify(player, never()).save();
    }

    @Test
    void sameUsernameReplacementSessionCannotReceiveAnOldCompletion() throws Exception {
        var result = service.changePassword(player, "changed");
        Player replacement = player(Player.class);
        online.put(player.getUsername(), replacement);
        workers.runFirst();
        runGameTasks();
        assertFalse(result.join());
        assertEquals("initial", player.getPassword());
        assertEquals("initial", replacement.getPassword());
        verify(player, never()).save();
        verify(replacement, never()).save();
    }

    @Test
    void reactivatingTheSamePlayerObjectCannotApplyAnOlderSessionChange() throws Exception {
        var result = service.changePassword(player, "changed");
        online.clear();
        when(player.getState()).thenReturn(EntityState.INACTIVE);
        var overlays = Player.class.getDeclaredField("overlays");
        overlays.setAccessible(true);
        overlays.set(player, player.getOverlays());
        var plugins = Entity.class.getDeclaredField("plugins");
        plugins.setAccessible(true);
        plugins.set(player, player.getPlugins());
        setField(Player.class, "localMobs", player.getLocalMobs());
        setField(Player.class, "combat", player.getCombat());
        setField(Mob.class, "actions", player.getActions());
        setField(Mob.class, "navigator", player.getNavigator());
        var inactive = Player.class.getDeclaredMethod("onInactive");
        inactive.setAccessible(true);
        inactive.invoke(doCallRealMethod().when(player));
        inactive.invoke(player);
        when(player.getState()).thenReturn(EntityState.ACTIVE);
        online.put(player.getUsername(), player);
        workers.runFirst();
        runGameTasks();
        assertFalse(result.join());
        assertEquals("initial", player.getPassword());
        verify(player, never()).save();
    }

    @Test
    void pendingLogoutRejectsRequestsWithoutHashing() {
        when(player.getClient().isPendingLogout()).thenReturn(true);
        assertFalse(service.changePassword(player, "changed").join());
        assertEquals(0, workers.tasks.size());
        assertTrue(hashThreads.isEmpty());
    }

    @Test
    void pendingLogoutAfterWorkerCompletionPreventsCommit() throws Exception {
        var result = service.changePassword(player, "changed");
        workers.runFirst();
        when(world.getLogoutService().isSavePending(player.getUsername())).thenReturn(true);
        runGameTasks();
        assertFalse(result.join());
        assertEquals("initial", player.getPassword());
        verify(player, never()).save();
    }

    @Test
    void rawCredentialReplacementInvalidatesAnOlderPreparedChange() throws Exception {
        var result = service.changePassword(player, "changed");
        player.setPassword("administrator");
        player.setHashedPassword("prepared administrator hash");
        workers.runFirst();
        runGameTasks();
        assertFalse(result.join());
        assertEquals("administrator", player.getPassword());
        assertEquals("prepared administrator hash", player.getHashedPassword());
    }

    @Test
    void failedHashKeepsExistingCredentialsAndDoesNotSave() throws Exception {
        player.setHashedPassword("existing hash");
        service = service(password -> { throw new IllegalStateException("Hash worker failed"); });
        var result = service.changePassword(player, "changed");
        workers.runFirst();
        runGameTasks();
        assertThrows(CompletionException.class, result::join);
        assertEquals("initial", player.getPassword());
        assertEquals("existing hash", player.getHashedPassword());
        verify(player, never()).save();
    }

    @Test
    void failedSaveDoesNotReportSuccess() throws Exception {
        var result = service.changePassword(player, "changed");
        workers.runFirst();
        runGameTasks();
        saves.getFirst().completeExceptionally(new IllegalStateException("Save failed"));
        runGameTasks();
        assertThrows(CompletionException.class, result::join);
    }

    @Test
    void staleSaveFailureDoesNotOverrideOrFailANewerChange() throws Exception {
        var older = service.changePassword(player, "older");
        workers.runFirst();
        runGameTasks();
        var latest = service.changePassword(player, "latest");
        workers.runFirst();
        runGameTasks();
        saves.getLast().complete(null);
        saves.getFirst().completeExceptionally(new IllegalStateException("Older save failed"));
        runGameTasks();
        assertFalse(older.join());
        assertTrue(latest.join());
        assertEquals("latest", player.getPassword());
        assertTrue(BCrypt.checkpw("latest", player.getHashedPassword()));
    }

    @Test
    void invalidPasswordsAreRejectedBeforeAnyWorkerTask() {
        for (String password : new String[] {null, "", "   ", "x".repeat(21)}) {
            assertThrows(CompletionException.class, () -> service.changePassword(player, password).join());
        }
        assertEquals(0, workers.tasks.size());
        assertTrue(hashThreads.isEmpty());
        assertEquals("initial", player.getPassword());
    }

    @Test
    void unpreparedRealAccountSnapshotFailsInsteadOfHashingOnGameThread() {
        assertThrows(IllegalStateException.class, () -> new PlayerData(player.getUsername()).save(player));
        assertNull(player.getHashedPassword());
        assertTrue(hashThreads.isEmpty());
    }

    @Test
    void disabledPasswordPolicyRetainsCheapPlaceholderSnapshots() {
        when(settings.passwordStrength()).thenReturn(GameSettings.PasswordStrength.NONE);
        assertEquals("password", new PlayerData(player.getUsername()).save(player).password);
        assertNull(player.getHashedPassword());
    }

    @Test
    void autonomousBotsRetainCheapPlaceholderSnapshots() throws Exception {
        Bot bot = (Bot) player(Bot.class);
        when(bot.isBot()).thenReturn(true);
        assertEquals("password", new PlayerData(bot.getUsername()).save(bot).password);
        assertNull(bot.getHashedPassword());
    }

    private LoginService service(Function<String, String> hasher) {
        return new LoginService(world, workers, hasher);
    }

    private LoginClient loginClient() {
        LoginClient client = mock(LoginClient.class);
        when(client.getIpAddress()).thenReturn("198.51.100.1");
        when(client.getLoginResponse(nullable(PlayerData.class), eq("initial"))).thenReturn(LoginResponse.NORMAL);
        when(client.sendFinalLoginResponse(any(), nullable(PlayerData.class), any())).thenReturn(true);
        return client;
    }

    private LoginService.LoginRequest request(LoginClient client) {
        var message = new LoginRequestMessage(player.getUsername(), "initial", null, null, null);
        return new LoginService.LoginRequest(player, client, message);
    }

    private Player player(Class<? extends Player> type) throws Exception {
        Player target = mock(type, RETURNS_DEEP_STUBS);
        var credentials = Player.class.getDeclaredField("credentials");
        credentials.setAccessible(true);
        credentials.set(target, new PlayerCredentials("passwordtest", "initial"));
        when(target.getUsername()).thenCallRealMethod();
        when(target.getPassword()).thenCallRealMethod();
        when(target.getHashedPassword()).thenCallRealMethod();
        when(target.beginPasswordChange()).thenCallRealMethod();
        when(target.isPasswordChangeCurrent(anyLong())).thenCallRealMethod();
        doCallRealMethod().when(target).setPassword(any());
        doCallRealMethod().when(target).setHashedPassword(any());
        doCallRealMethod().when(target).applyPasswordChange(anyLong(), any(), any());
        when(target.getState()).thenReturn(EntityState.ACTIVE);
        when(target.getDynamicMap()).thenReturn(null);
        when(target.getFriends()).thenReturn(java.util.Set.of());
        when(target.getIgnores()).thenReturn(java.util.Set.of());
        when(target.save()).thenAnswer(call -> {
            savedHashes.add(new PlayerData(target.getUsername()).save(target).password);
            var future = new CompletableFuture<Void>();
            saves.add(future);
            return future;
        });
        return target;
    }

    private void runGameTasks() {
        while (!gameTasks.isEmpty()) {
            gameTasks.remove().run();
        }
    }

    private void setField(Class<?> owner, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(player, value);
    }

    /**
     * Controls completion order while executing each hash on a real worker thread.
     *
     * @author lare96
     */
    private static final class ManualWorkers extends AbstractExecutorService {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;

        void runFirst() throws InterruptedException {
            run(tasks.removeFirst());
        }

        void runLast() throws InterruptedException {
            run(tasks.removeLast());
        }

        private void run(Runnable action) throws InterruptedException {
            var error = new AtomicReference<Throwable>();
            Thread worker = new Thread(() -> {
                try {
                    action.run();
                } catch (Throwable failure) {
                    error.set(failure);
                }
            }, "PasswordTestWorker");
            worker.start();
            worker.join(5000);
            assertFalse(worker.isAlive(), "Password worker did not finish");
            assertNull(error.get());
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> remaining = new ArrayList<>(tasks);
            tasks.clear();
            return remaining;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }
    }
}
