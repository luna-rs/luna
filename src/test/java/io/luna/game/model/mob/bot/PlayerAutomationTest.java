package io.luna.game.model.mob.bot;

import api.bot.script.BotScript;
import api.bot.script.DynamicBotScript;
import kotlin.coroutines.Continuation;
import io.luna.LunaContext;
import io.luna.game.model.EntityState;
import io.luna.game.model.def.CombatStyleDefinition;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerCredentials;
import io.luna.game.model.mob.PlayerRights;
import io.luna.game.plugin.PluginBootstrap;
import io.luna.net.client.GameClient;
import io.luna.net.msg.GameMessageWriter;
import io.luna.net.msg.in.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Exercises real player identity and session-only script control without a second world entity.
 *
 * @author lare96
 */
final class PlayerAutomationTest {
    private LunaContext context;
    private Bot player;
    private GameClient client;

    @BeforeEach
    void setUp() throws Exception {
        api.bot.script.InventoryProductionFixtures.INSTANCE.initialize();
        if (CombatStyleDefinition.ALL.isEmpty()) {
            new io.luna.util.parser.impl.WeaponTypeDefinitionFileParser().run();
        }
        context = mock(LunaContext.class, RETURNS_DEEP_STUBS);
        var bindings = PluginBootstrap.class.getDeclaredMethod("setBindings", LunaContext.class);
        bindings.setAccessible(true);
        bindings.invoke(null, context);
        player = Bot.connectedPlayer(context, new PlayerCredentials("tester", "original"));
        client = mock(GameClient.class, RETURNS_DEEP_STUBS);
        player.setClient(client);
        player.setRights(PlayerRights.DEVELOPER);
        var state = io.luna.game.model.Entity.class.getDeclaredField("state");
        state.setAccessible(true);
        state.set(player, EntityState.ACTIVE);
    }

    @Test
    void humanIdentityCredentialsViewportAndNetworkRemainHuman() {
        assertFalse(player.isBot());
        assertTrue(player.isConnectedPlayer());
        assertFalse(player.isAutomationActive());
        assertEquals("original", player.getPassword());
        assertNotNull(player.getLocalMobs());
        assertSame(client, player.getClient());
        assertNotSame(client, player.getBotClient());
        GameMessageWriter message = mock(GameMessageWriter.class);
        player.queue(message);
        verify(client).queue(message);
        verify(context.getWorld().getBots(), never()).add(any());
        assertThrows(IllegalStateException.class, player::login);
    }

    @Test
    void reconnectingHumanRetainsTheExistingCollectionHash() {
        Bot reconnected = Bot.connectedPlayer(context, new PlayerCredentials("tester", "different"));
        Set<Player> players = new HashSet<>();
        players.add(player);

        assertEquals(Objects.hash(player.getUsernameHash()), player.hashCode());
        assertEquals(player.hashCode(), reconnected.hashCode());
        assertTrue(players.contains(reconnected));
    }

    @Test
    void injectedScriptUsesTheSameEntityAndMirrorsMessagesWithoutReplacingNetwork() {
        BotScript script = mock(BotScript.class);
        when(script.getBot()).thenReturn(player);
        when(script.isIdle()).thenReturn(true);
        player.startAutomation(script);
        assertSame(script, player.getScriptStack().current());
        assertSame(player, script.getBot());
        assertTrue(player.isAutomationActive());
        GameMessageWriter message = mock(GameMessageWriter.class);
        player.queue(message);
        verify(client).queue(message);
        player.getBotClient().flush();
        assertEquals(1, player.getInput().getReceived().size());
        player.stopAutomation();
        assertFalse(player.isAutomationActive());
        assertEquals(0, player.getScriptStack().size());
        assertTrue(player.getInput().getReceived().isEmpty());
        verify(script).stop();
        assertSame(client, player.getClient());
    }

    @Test
    void rejectsOtherOwnersNonDevelopersAndDisconnectedSessions() {
        BotScript script = mock(BotScript.class);
        when(script.isIdle()).thenReturn(true);
        when(script.getBot()).thenReturn(mock(Bot.class));
        assertThrows(IllegalArgumentException.class, () -> player.startAutomation(script));
        when(script.getBot()).thenReturn(player);
        player.setRights(PlayerRights.PLAYER);
        assertThrows(IllegalStateException.class, () -> player.startAutomation(script));
        player.setRights(PlayerRights.DEVELOPER);
        when(client.isPendingLogout()).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> player.startAutomation(script));
    }

    @Test
    void manualActionsInterruptButBackgroundClientTrafficDoesNot() {
        assertTrue(PlayerAutomation.interrupts(new WalkingMessageReader()));
        assertTrue(PlayerAutomation.interrupts(new ButtonClickMessageReader()));
        assertTrue(PlayerAutomation.interrupts(new CommandMessageReader()));
        assertFalse(PlayerAutomation.interrupts(new KeepAliveMessageReader()));
        assertFalse(PlayerAutomation.interrupts(new ArrowKeyMessageReader()));
        assertFalse(PlayerAutomation.interrupts(new FocusChangedMessageReader()));
    }

    @Test
    void developerCapabilityIsAutomaticAndOtherAccountsStayOrdinaryPlayers() {
        var pending = new io.luna.game.model.mob.Player(context, new PlayerCredentials("tester", "secret"));
        pending.setLowMemory(true);
        assertSame(pending, PlayerAutomation.prepareLoginPlayer(pending, PlayerRights.PLAYER));
        assertSame(pending, PlayerAutomation.prepareLoginPlayer(pending, PlayerRights.ADMINISTRATOR));
        var developer = PlayerAutomation.prepareLoginPlayer(pending, PlayerRights.DEVELOPER);
        assertTrue(developer instanceof Bot);
        assertTrue(((Bot) developer).isConnectedPlayer());
        assertFalse(developer.isBot());
        assertFalse(((Bot) developer).isAutomationActive());
        assertTrue(developer.isLowMemory());
        assertEquals("tester", developer.getUsername());
        assertEquals("secret", developer.getPassword());
        verify(context.getWorld().getBots(), never()).add(any());
    }

    @Test
    void capabilitySelectionUsesTheExistingDeveloperRightsRules() {
        assertEquals(PlayerRights.DEVELOPER, PlayerRights.forLogin("tester", PlayerRights.DEVELOPER, false, false));
        assertEquals(PlayerRights.PLAYER, PlayerRights.forLogin("tester", PlayerRights.PLAYER, true, true));
        assertEquals(PlayerRights.ADMINISTRATOR, PlayerRights.forLogin("tester", PlayerRights.ADMINISTRATOR, true, true));
        assertEquals(PlayerRights.DEVELOPER, PlayerRights.forLogin("tester", null, true, false));
        assertEquals(PlayerRights.DEVELOPER, PlayerRights.forLogin("tester", null, false, true));
        assertEquals(PlayerRights.PLAYER, PlayerRights.forLogin("tester", null, false, false));
        assertEquals(PlayerRights.DEVELOPER, PlayerRights.forLogin("lare96", PlayerRights.PLAYER, false, false));
    }

    @Test
    void idleDeveloperCapabilityDoesNotRunReflexesOrStartScripts() {
        var reflex = mock(api.bot.script.ReflexBotScript.class);
        when(reflex.shouldReact()).thenReturn(true);
        player.getReflex().add(reflex);
        player.process();
        verify(reflex, never()).shouldReact();
        verify(reflex, never()).start();
        assertFalse(player.isAutomationActive());
        assertNull(player.getScriptStack().current());
    }

    @Test
    void realCoroutineMutatesTheConnectedCharacter() {
        var game = api.predef.PropertiesPredefKt.getGameService();
        var executor = game.getGameExecutor();
        java.util.Queue<Runnable> tasks = new java.util.ArrayDeque<>();
        doAnswer(call -> { tasks.add(call.getArgument(0)); return null; })
                .when(executor).execute(any(Runnable.class));
        doAnswer(call -> { tasks.add(call.getArgument(0)); return null; })
                .when(game).yield(any(Runnable.class));
        DynamicBotScript script = new DynamicBotScript(player) {
            @Override
            protected ActionSafety getActionSafety() {
                return ActionSafety.NONE;
            }

            @Override
            public Object run(Continuation<? super Boolean> continuation) {
                getBot().setRunEnergy(37);
                return true;
            }
        };
        player.startAutomation(script);
        player.process();
        for (int count = 0; !tasks.isEmpty() && count < 20; count++) tasks.remove().run();
        assertTrue(tasks.isEmpty());
        assertTrue(script.isPassive());
        assertEquals(37, player.getRunEnergy());
        verify(client, atLeastOnce()).queue(any(io.luna.net.msg.out.UpdateRunEnergyMessageWriter.class));
        player.process();
        player.process();
        assertFalse(player.isAutomationActive());
        assertSame(client, player.getClient());
    }

    @Test
    void stopsOnDisconnectAndDiscardsSyntheticPackets() {
        BotScript script = mock(BotScript.class);
        when(script.getBot()).thenReturn(player);
        when(script.isIdle()).thenReturn(true);
        player.startAutomation(script);
        var packet = mock(io.luna.net.msg.GameMessage.class, RETURNS_DEEP_STUBS);
        player.getBotClient().queueSimulated(packet);
        when(client.isPendingLogout()).thenReturn(true);
        player.processAutomationInput();
        assertFalse(player.isAutomationActive());
        verify(packet.getPayload()).releaseAll();
        verify(script).stop();
        var latePacket = mock(io.luna.net.msg.GameMessage.class, RETURNS_DEEP_STUBS);
        player.getBotClient().queueSimulated(latePacket);
        verify(latePacket.getPayload()).releaseAll();
    }

    @Test
    void networkActionsCancelBeforeTheReaderRuns() {
        BotScript script = mock(BotScript.class);
        when(script.getBot()).thenReturn(player);
        when(script.isIdle()).thenReturn(true);
        player.startAutomation(script);
        var repository = mock(io.luna.net.msg.GameMessageRepository.class);
        var network = new GameClient(mock(io.netty.channel.Channel.class), repository, player);
        var reader = mock(WalkingMessageReader.class);
        var packet = mock(io.luna.net.msg.GameMessage.class, RETURNS_DEEP_STUBS);
        doReturn(reader).when(repository).get(0);
        doAnswer(call -> { assertFalse(player.isAutomationActive()); return null; })
                .when(reader).submitMessage(player, packet);
        network.onMessageReceived(packet);
        network.handleDecodedMessages();
        verify(reader).submitMessage(player, packet);
        verify(script).stop();
        verify(packet.getPayload()).releaseAll();
    }

    @Test
    void stopCurrentRemovesAnIdleScriptInsteadOfStartingItNextTick() {
        BotScript script = mock(BotScript.class);
        when(script.getBot()).thenReturn(player);
        when(script.isIdle()).thenReturn(true);
        player.startAutomation(script);
        assertTrue(player.getScriptStack().stopCurrent());
        assertNull(player.getScriptStack().current());
        player.process();
        verify(script, never()).start();
        assertFalse(player.isAutomationActive());
    }

    @Test
    void stoppingAutomationCancelsAnActiveReflex() {
        BotScript script = mock(BotScript.class);
        when(script.getBot()).thenReturn(player);
        when(script.isIdle()).thenReturn(true);
        var reflex = mock(api.bot.script.ReflexBotScript.class);
        when(reflex.shouldReact()).thenReturn(true);
        player.getReflex().add(reflex);
        player.startAutomation(script);
        player.process();
        verify(reflex).start();
        player.stopAutomation();
        verify(reflex).stop();
        assertNull(player.getReflex().getActiveReflex());
    }
}
