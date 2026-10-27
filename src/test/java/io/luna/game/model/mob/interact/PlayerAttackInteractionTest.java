package io.luna.game.model.mob.interact;

import io.luna.game.event.impl.PlayerClickEvent.PlayerFirstClickEvent;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerContextMenuOption;
import io.luna.game.model.mob.bot.Bot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that player attack clicks hand off to combat before reaching a moving target.
 *
 * @author lare96
 */
class PlayerAttackInteractionTest {

    @Test
    void movingBotAttackStartsWithoutReachAndConsumesOtherClickListeners() {
        verifyAttackHandoff(mock(Bot.class));
    }

    @Test
    void movingHumanAttackUsesTheSameHandoff() {
        verifyAttackHandoff(mock(Player.class));
    }

    private void verifyAttackHandoff(Player target) {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        when(player.getPosition()).thenReturn(new Position(100, 100));
        when(target.getPosition()).thenReturn(new Position(110, 100));
        when(player.getContextMenu().contains(PlayerContextMenuOption.ATTACK)).thenReturn(true);
        when(player.getWalking().isEmpty()).thenReturn(true);
        AtomicInteger otherCallbacks = new AtomicInteger();
        var listeners = new ArrayList<>(List.of(
                new InteractionActionListener(InteractionPolicy.STANDARD_SIZE, () -> player.getCombat().attack(target)),
                new InteractionActionListener(InteractionPolicy.STANDARD_SIZE, otherCallbacks::incrementAndGet)));
        var action = new InteractionAction(player, listeners, target, new PlayerFirstClickEvent(player, target));

        assertTrue(action.run());
        verify(player.getCombat()).attack(target);
        verify(player.getWalking()).clear();
        verify(player, never()).sendMessage(anyString());
        assertEquals(1, otherCallbacks.get());
        assertTrue(listeners.isEmpty());
    }

    @Test
    void challengeStillWaitsForReach() {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        Player target = mock(Player.class);
        when(player.getPosition()).thenReturn(new Position(100, 100));
        when(player.getWalking().isEmpty()).thenReturn(true);
        AtomicInteger callbacks = new AtomicInteger();
        var listeners = new ArrayList<>(List.of(
                new InteractionActionListener(InteractionPolicy.STANDARD_SIZE, callbacks::incrementAndGet)));
        var action = new InteractionAction(player, listeners, target, new PlayerFirstClickEvent(player, target));

        assertFalse(action.run());
        assertTrue(action.run());
        assertEquals(0, callbacks.get());
        verify(player).sendMessage("I can't reach that!");
        verify(player.getCombat(), never()).attack(any());
    }
}
