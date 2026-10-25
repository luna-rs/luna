package io.luna.game.model.mob.combat;

import io.luna.game.action.ActionQueue;
import io.luna.game.action.impl.NpcRetreatAction;
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy;
import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.def.NpcCombatMovement;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.combat.state.NpcCombatContext;
import io.luna.game.model.mob.interact.InteractionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Exercises combat-to-escape transitions, leash termination, and bounded collision-aware fleeing.
 *
 * @author lare96
 */
class NpcRetreatTest {

    private Npc npc;
    private Player player;
    private NpcCombatContext combat;
    private ActionQueue actions;

    @BeforeEach
    void setUp() {
        npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        player = mock(Player.class, RETURNS_DEEP_STUBS);
        when(npc.isAlive()).thenReturn(true);
        when(player.isAlive()).thenReturn(true);
        when(npc.size()).thenReturn(1);
        when(player.size()).thenReturn(1);
        when(npc.getPosition()).thenReturn(new Position(100, 100));
        when(npc.getBasePosition()).thenReturn(new Position(100, 100));
        when(player.getPosition()).thenReturn(new Position(101, 101));
        when(player.getX()).thenAnswer(invocation -> player.getPosition().getX());
        when(player.getY()).thenAnswer(invocation -> player.getPosition().getY());
        when(npc.getHealth()).thenReturn(20);
        when(npc.combatDef().getMovement()).thenReturn(new NpcCombatMovement(7, 0, 8));
        when(npc.getWalking().isEmpty()).thenReturn(true);
        actions = new ActionQueue(npc);
        when(npc.getActions()).thenReturn(actions);
        combat = spy(new NpcCombatContext(npc));
        when(npc.getCombat()).thenReturn(combat);
    }

    @Test
    void absoluteHealthThresholdReplacesCombatWithEscape() {
        combat.attack(player);
        CombatAction previous = actions.first(CombatAction.class);
        assertNotNull(previous);
        when(npc.getHealth()).thenReturn(8);
        combat.retaliate(player);
        assertTrue(combat.isRetreating());
        assertNull(combat.getTarget());
        assertTrue(previous.isFinished());
        assertFalse(combat.isAutoRetaliate());
        verify(npc.getNavigator()).cancel();
        verify(npc.getWalking()).clear();
        combat.attack(player);
        assertNull(combat.getTarget());
    }

    @Test
    void healthAboveThresholdKeepsCurrentEngagement() {
        combat.attack(player);
        when(npc.getHealth()).thenReturn(9);
        combat.retaliate(player);
        assertFalse(combat.isRetreating());
        assertSame(player, combat.getTarget());
    }

    @Test
    void unsetHealthThresholdDoesNotFleeEvenAtOneHitpoint() {
        when(npc.combatDef().getMovement()).thenReturn(NpcCombatMovement.DEFAULT);
        when(npc.getHealth()).thenReturn(1);
        combat.retaliate(player);
        assertFalse(combat.isRetreating());
        assertSame(player, combat.getTarget());
    }

    @Test
    void retaliationOutsideLeashEscapesInsteadOfChasing() {
        when(player.getPosition()).thenReturn(new Position(110, 100));
        combat.attack(player);
        assertNull(combat.getTarget());
        combat.retaliate(player);
        assertTrue(combat.isRetreating());
    }

    @Test
    void explicitNonePolicyDisablesAutomaticEscapeAndLeash() {
        combat.setRetreatPolicy(RetreatPolicy.NONE);
        when(player.getPosition()).thenReturn(new Position(110, 100));
        when(npc.getHealth()).thenReturn(1);
        combat.retaliate(player);
        assertFalse(combat.isRetreating());
        assertSame(player, combat.getTarget());
    }

    @Test
    void chaseEndsAsSoonAsTargetCrossesLeash() {
        combat.setTarget(player);
        when(npc.isWithinDistance(player, Position.VIEWING_DISTANCE + 5)).thenReturn(true);
        CombatAttack<?> attack = mock(CombatAttack.class);
        when(attack.getInteractionPolicy()).thenReturn(InteractionPolicy.STANDARD_SIZE);
        doReturn(attack).when(combat).getNextAttack(player);
        when(player.getPosition()).thenReturn(new Position(109, 100));
        assertTrue(new CombatAction(npc).run());
        assertNull(combat.getTarget());
        verify(attack, never()).apply();
        verify(npc.getNavigator(), never()).step(any());
    }

    @Test
    void leashCancelsPursuitWhileCombatTimerIsStillRunning() {
        combat.setTarget(player);
        combat.resetCombatTimer();
        when(npc.isViewableFrom(player)).thenReturn(true);
        when(npc.getWorld().getCollisionManager().reached(any(), eq(player), any())).thenReturn(true);
        // Keep pursuit active without relying on collision/map fixtures.
        PursuitAction pursuit = mock(PursuitAction.class);
        when(pursuit.getState()).thenReturn(io.luna.game.action.ActionState.PROCESSING);
        ActionQueue queue = spy(actions);
        when(npc.getActions()).thenReturn(queue);
        doReturn(pursuit).when(queue).first(PursuitAction.class);
        when(player.getPosition()).thenReturn(new Position(109, 100));
        when(npc.isWithinDistance(player, Position.VIEWING_DISTANCE + 5)).thenReturn(true);
        CombatAttack<?> attack = mock(CombatAttack.class);
        when(attack.getInteractionPolicy()).thenReturn(InteractionPolicy.STANDARD_SIZE);
        doReturn(attack).when(combat).getNextAttack(player);
        assertFalse(new CombatAction(npc).run());
        assertNull(combat.getTarget());
        verify(pursuit).complete();
        verify(npc.getWalking()).clear();
        verify(attack, never()).apply();
    }

    @Test
    void damagePipelineChecksHealthEvenDuringAnExistingFight() {
        combat.attack(player);
        when(npc.getHealth()).thenReturn(8);
        var damage = mock(io.luna.game.model.mob.combat.damage.CombatDamage.class);
        when(damage.getAttacker()).thenReturn(player);
        when(damage.getVictim()).thenReturn(npc);
        doNothing().when(combat).onNextDefence(any(), any());
        new io.luna.game.model.mob.combat.damage.CombatDamageAction(damage, null, false).run();
        assertTrue(combat.isRetreating());
        verify(damage).apply(null);
    }

    @Test
    void escapeUsesDiagonalThenHorizontalThenVertical() {
        NpcRetreatAction action = new NpcRetreatAction(npc, player);
        when(npc.getNavigator().step(Direction.SOUTH)).thenReturn(true);
        assertFalse(action.run());
        var order = inOrder(npc.getNavigator());
        order.verify(npc.getNavigator()).step(Direction.SOUTH_WEST);
        order.verify(npc.getNavigator()).step(Direction.WEST);
        order.verify(npc.getNavigator()).step(Direction.SOUTH);
        order.verifyNoMoreInteractions();
    }

    @Test
    void escapeNeverStepsPastSpawnBounds() {
        when(npc.getPosition()).thenReturn(new Position(93, 93));
        NpcRetreatAction action = new NpcRetreatAction(npc, player);
        for (int tick = 0; tick < 10; tick++) {
            assertFalse(action.run());
        }
        verify(npc.getNavigator(), never()).step(any());
    }

    @Test
    void blockedEscapeReturnsToDefaultBehaviorAfterFiveStalledTicks() {
        NpcRetreatAction action = new NpcRetreatAction(npc, player);
        actions.submit(action);
        for (int tick = 0; tick < 5; tick++) {
            assertFalse(action.run());
        }
        assertTrue(action.run());
        action.complete();
        assertFalse(combat.isRetreating());
        assertTrue(combat.isAutoRetaliate());
    }

    @Test
    void deadDistantAndOffPlaneThreatsEndEscape() {
        NpcRetreatAction action = new NpcRetreatAction(npc, player);
        when(player.getPosition()).thenReturn(new Position(126, 100));
        assertTrue(action.run());
        when(player.getPosition()).thenReturn(new Position(101, 100, 1));
        assertTrue(action.run());
        when(player.getPosition()).thenReturn(new Position(101, 100));
        when(player.isAlive()).thenReturn(false);
        assertTrue(action.run());
    }

    @Test
    void frozenNpcCannotQueueEscapeSteps() {
        when(npc.getStatus().isImmobilized()).thenReturn(true);
        assertFalse(new NpcRetreatAction(npc, player).run());
        verify(npc.getNavigator(), never()).step(any());
    }
}
