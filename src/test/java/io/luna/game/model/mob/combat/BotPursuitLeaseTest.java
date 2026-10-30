package io.luna.game.model.mob.combat;

import api.bot.script.InventoryProductionFixtures;
import io.luna.game.model.EntityState;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.combat.state.PlayerCombatContext;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.mob.movement.NavigationRequest;
import io.luna.game.model.mob.movement.NavigationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that bot positioning suppresses only its exact live NPC chase and releases on lifecycle changes.
 *
 * @author lare96
 */
class BotPursuitLeaseTest {

    @BeforeAll static void initializeCombat() {
        InventoryProductionFixtures.INSTANCE.initializeCombat();
    }

    /**
     * A real player combat context with a controlled world clock and living NPC.
     *
     * @author lare96
     */
    private static class Fixture {
        final Bot bot = mock(Bot.class, RETURNS_DEEP_STUBS);
        final Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        final AtomicLong tick = new AtomicLong(100);
        final AtomicBoolean active = new AtomicBoolean(true);
        final Object owner = new Object();
        final PlayerCombatContext combat;

        Fixture() {
            when(bot.getState()).thenReturn(EntityState.ACTIVE);
            when(npc.getState()).thenReturn(EntityState.ACTIVE);
            when(bot.isAlive()).thenReturn(true);
            when(npc.isAlive()).thenReturn(true);
            when(bot.getWorld().getCurrentTick()).thenAnswer(call -> tick.get());
            combat = spy(new PlayerCombatContext(bot));
            when(bot.getCombat()).thenReturn(combat);
        }

        void hold() {
            combat.holdPursuit(owner, npc, active::get);
        }
    }

    @Test void targetReplacementAndScriptExitInvalidateTheLease() {
        Fixture f = new Fixture();
        f.hold();
        assertTrue(f.combat.isPursuitHeld(f.npc));
        f.combat.releasePursuit(new Object());
        assertTrue(f.combat.isPursuitHeld(f.npc));
        assertFalse(f.combat.isPursuitHeld(mock(Npc.class)));
        assertFalse(f.combat.isPursuitHeld(f.npc));
        f.hold();
        f.active.set(false);
        assertFalse(f.combat.isPursuitHeld(f.npc));
        f.active.set(true);
        assertFalse(f.combat.isPursuitHeld(f.npc));
    }

    @Test void staleLeasesExpireAndRenewalKeepsAnAttendedPosition() {
        Fixture f = new Fixture();
        f.hold();
        f.tick.set(119);
        assertTrue(f.combat.isPursuitHeld(f.npc));
        f.hold();
        f.tick.set(138);
        assertTrue(f.combat.isPursuitHeld(f.npc));
        f.tick.set(140);
        assertFalse(f.combat.isPursuitHeld(f.npc));
    }

    @Test void deathCombatFinishAndNewLifeReleasePositioning() {
        Fixture f = new Fixture();
        f.hold();
        when(f.npc.isAlive()).thenReturn(false);
        assertFalse(f.combat.isPursuitHeld(f.npc));
        when(f.npc.isAlive()).thenReturn(true);
        f.hold();
        f.combat.onCombatFinished();
        assertFalse(f.combat.isPursuitHeld(f.npc));
        f.hold();
        f.combat.reset();
        assertFalse(f.combat.isPursuitHeld(f.npc));
        f.hold();
        when(f.npc.getState()).thenReturn(EntityState.INACTIVE);
        assertFalse(f.combat.isPursuitHeld(f.npc));
    }

    @Test void ordinaryHumanCombatCannotAcquireBotPositioning() {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        PlayerCombatContext combat = new PlayerCombatContext(player);
        Npc npc = mock(Npc.class);
        combat.holdPursuit(this, npc, () -> true);
        assertFalse(combat.isPursuitHeld(npc));
    }

    @Test void combatDoesNotReplaceAnchorMovementAndResumesPursuitAfterRelease() {
        Fixture f = new Fixture();
        when(f.bot.getPosition()).thenReturn(new Position(100, 100));
        when(f.npc.getPosition()).thenReturn(new Position(105, 100));
        when(f.bot.isWithinDistance(f.npc, Position.VIEWING_DISTANCE + 5)).thenReturn(true);
        CombatAttack<?> attack = mock(CombatAttack.class);
        when(attack.getInteractionPolicy()).thenReturn(InteractionPolicy.STANDARD_LINE_OF_SIGHT);
        doReturn(attack).when(f.combat).getNextAttack(f.npc);
        doReturn(true).when(f.combat).onCombatProcess(false);
        f.combat.setTarget(f.npc);
        f.hold();
        CombatAction action = new CombatAction(f.bot);
        assertFalse(action.run());
        verify(f.bot.getNavigator(), never()).submit(any(NavigationRequest.class));
        f.combat.releasePursuit(f.owner);
        var pending = new CompletableFuture<NavigationResult>();
        when(f.bot.getNavigator().submit(any(NavigationRequest.class))).thenReturn(pending);
        when(f.bot.getNavigator().getCurrentPending()).thenReturn(pending);
        assertFalse(action.run());
        verify(f.bot.getNavigator()).submit(any(NavigationRequest.class));

        var anchorWalk = new CompletableFuture<NavigationResult>();
        when(f.bot.getNavigator().getCurrentPending()).thenReturn(anchorWalk);
        f.hold();
        assertFalse(action.run());
        verify(f.bot.getNavigator(), never()).cancel();
        verify(f.bot.getWalking(), never()).clear();
    }
}
