package io.luna.game.model.mob.combat;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.interact.InteractionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CombatInitiationTest {
    private Player player;
    private Npc target;
    private CombatAttack<?> attack;
    private CombatAction action;

    @BeforeEach void setUp() {
        player = mock(Player.class, RETURNS_DEEP_STUBS);
        target = mock(Npc.class);
        attack = mock(CombatAttack.class);
        when(player.getPosition()).thenReturn(new Position(110, 110));
        when(player.isAlive()).thenReturn(true);
        when(target.isAlive()).thenReturn(true);
        when(player.isWithinDistance(target, Position.VIEWING_DISTANCE + 5)).thenReturn(true);
        when(player.getCombat().getTarget()).thenReturn(target);
        when(player.getCombat().getLastCombatWith()).thenReturn(null);
        when(player.getCombat().getAutoRetaliateTarget()).thenReturn(null);
        var combat = player.getCombat();
        doReturn(attack).when(combat).getNextAttack(target);
        when(attack.getInteractionPolicy()).thenReturn(InteractionPolicy.STANDARD_SIZE);
        when(player.getCombat().onCombatProcess(anyBoolean())).thenReturn(true);
        when(player.getCombat().checkMultiCombat(target)).thenReturn(true);
        when(player.getCombat().inCombat()).thenReturn(false);
        when(player.getNavigator().getCurrentTarget()).thenReturn(null);
        action = new CombatAction(player);
    }

    private void reached(boolean reached) {
        when(player.getWorld().getCollisionManager().reached(any(), eq(target), eq(InteractionPolicy.STANDARD_SIZE)))
                .thenReturn(reached);
    }

    @Test void freshEngagementSurvivesApproachAndAttacksOnArrival() {
        reached(false);
        assertFalse(action.run());
        verify(player.getNavigator()).submit(any());
        verify(attack, never()).apply();
        verify(player.getCombat(), never()).setTarget(null);
        reached(true);
        when(player.getCombat().isAttackReady()).thenReturn(true);
        assertFalse(action.run());
        verify(attack).apply();
    }

    @Test void freshEngagementSurvivesInitialAttackCooldown() {
        reached(true);
        assertFalse(action.run());
        verify(attack, never()).apply();
        when(player.getCombat().isAttackReady()).thenReturn(true);
        assertFalse(action.run());
        verify(attack).apply();
    }

    @Test void unreachableEngagementTimesOutAndCancelsOwnedPursuit() {
        reached(false);
        when(player.getNavigator().getCurrentTarget()).thenReturn(target);
        when(player.getNavigator().isCurrentContinuous()).thenReturn(true);
        assertFalse(action.run());
        for (int tick = 0; tick < 29; tick++) assertFalse(action.run());
        assertTrue(action.run());
        verify(player.getNavigator()).cancel();
        verify(player.getCombat()).setTarget(null);
    }

    @Test void movementResetsPendingEngagementTimeout() {
        reached(false);
        for (int tick = 0; tick < 25; tick++) assertFalse(action.run());
        when(player.getPosition()).thenReturn(new Position(111, 110));
        for (int tick = 0; tick < 25; tick++) assertFalse(action.run());
        verify(player.getCombat(), never()).setTarget(null);
    }

    @Test void deadDisabledAndContestedTargetsStillStopCombat() {
        reached(true);
        when(target.isAlive()).thenReturn(false);
        assertTrue(action.run());
        when(target.isAlive()).thenReturn(true);
        when(player.getCombat().isDisabled()).thenReturn(true);
        assertTrue(action.run());
        when(player.getCombat().isDisabled()).thenReturn(false);
        when(player.getCombat().checkMultiCombat(target)).thenReturn(false);
        assertTrue(action.run());
        verify(attack, never()).apply();
    }
}
