package io.luna.game.model.mob.combat;

import io.luna.game.action.*;
import io.luna.game.action.impl.ConsumeAction;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.damage.CombatDamageAction;
import io.luna.game.model.mob.combat.state.CombatContext;
import io.luna.game.model.mob.interact.InteractionPolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CombatSchedulingTest {

    @BeforeAll static void bindings() {
        api.bot.script.InventoryProductionFixtures.INSTANCE.initialize();
    }

    private static class Fixture {
        final Mob mob = mock(Mob.class, RETURNS_DEEP_STUBS);
        final Mob victim = mock(Mob.class, RETURNS_DEEP_STUBS);
        final CombatContext<?> combat = mock(CombatContext.class,
                withSettings().useConstructor(mob).defaultAnswer(CALLS_REAL_METHODS));
        final CombatAttack<?> attack = mock(CombatAttack.class);
        final ActionQueue actions = new ActionQueue(mob);
        final AtomicBoolean reached = new AtomicBoolean(true);
        final AtomicBoolean alive = new AtomicBoolean(true);
        final List<Integer> swings = new ArrayList<>();
        int tick;

        Fixture() {
            doReturn(combat).when(mob).getCombat();
            when(mob.getActions()).thenReturn(actions);
            when(mob.getType()).thenReturn(EntityType.NPC);
            when(mob.isAlive()).thenAnswer(call -> alive.get());
            when(victim.isAlive()).thenReturn(true);
            when(mob.getPosition()).thenReturn(new Position(100, 100));
            when(victim.getPosition()).thenReturn(new Position(101, 100));
            when(mob.isWithinDistance(victim, Position.VIEWING_DISTANCE + 5)).thenReturn(true);
            doReturn(true).when(combat).inCombat();
            doReturn(false).when(combat).isDisabled();
            doReturn(true).when(combat).checkMultiCombat(victim);
            doReturn(attack).when(combat).getNextAttack(victim);
            when(attack.getInteractionPolicy()).thenReturn(InteractionPolicy.STANDARD_SIZE);
            when(mob.getWorld().getCollisionManager().reached(any(), eq(victim), any()))
                    .thenAnswer(call -> reached.get());
            doAnswer(call -> {
                swings.add(tick);
                combat.setAttackDelay(4);
                return null;
            }).when(attack).apply();
            combat.setTarget(victim);
        }

        void turn() {
            combat.processAttackDelay();
            actions.process();
            actions.processAfterMovement();
            actions.normalize();
            tick++;
        }
    }

    @Test void postMovementRetryKeepsFourTickCadence() {
        Fixture f = new Fixture();
        f.actions.submit(new CombatAction(f.mob));
        for (int tick = 0; tick < 10; tick++) f.turn();
        assertEquals(List.of(0, 4, 8), f.swings);
    }

    @Test void interactionStartsCombatInTheSameTurnWithoutDoubleSwinging() {
        Fixture f = new Fixture();
        f.actions.submit(new Action<Mob>(f.mob, ActionType.WEAK) {
            @Override public ActionPhase getPhase() { return ActionPhase.INTERACTION; }
            @Override public boolean run() {
                f.actions.submit(new CombatAction(f.mob));
                return true;
            }
        });
        f.turn();
        assertEquals(List.of(0), f.swings);
    }

    @Test void arrivingInRangeAttacksWithoutRepeatingSelectionOrProcessingHooks() {
        Fixture f = new Fixture();
        f.reached.set(false);
        f.actions.submit(new CombatAction(f.mob));
        f.combat.processAttackDelay();
        f.actions.process();
        assertTrue(f.swings.isEmpty());
        f.reached.set(true); // The walking phase reaches the target.
        f.actions.processAfterMovement();
        assertEquals(List.of(0), f.swings);
        verify(f.combat, times(1)).getNextAttack(f.victim);
        verify(f.combat, times(1)).onCombatProcess(anyBoolean());
        f.actions.processAfterMovement();
        assertEquals(List.of(0), f.swings);
    }

    @Test void queuedLethalDamagePrecedesAnAlreadyRegisteredDueCombatAction() {
        Fixture f = new Fixture();
        f.combat.setAttackDelay(2);
        f.actions.submit(new CombatAction(f.mob));
        CombatDamage damage = mock(CombatDamage.class);
        when(damage.getAttacker()).thenReturn(f.victim);
        when(damage.getVictim()).thenReturn(f.mob);
        doAnswer(call -> { f.alive.set(false); return null; }).when(damage).apply(f.attack);
        f.actions.submit(new CombatDamageAction(damage, f.attack, false));
        f.turn();
        f.turn();
        assertFalse(f.alive.get());
        assertTrue(f.swings.isEmpty());
    }

    @Test void strongActionsInterruptCombatWithoutResettingReadiness() {
        Fixture f = new Fixture();
        f.combat.setAttackDelay(4);
        CombatAction combat = new CombatAction(f.mob);
        f.actions.submit(combat);
        f.actions.submit(new Action<Mob>(f.mob, ActionType.STRONG) {
            @Override public boolean run() { return true; }
        });
        f.turn();
        assertEquals(ActionState.INTERRUPTED, combat.getState());
        assertEquals(3, f.combat.getAttackDelay());
        f.turn();
        assertEquals(2, f.combat.getAttackDelay());
        assertTrue(f.swings.isEmpty());
    }

    @Test void consumptionAppliesDuringInputAndRetainsItsThrottle() {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        ActionQueue actions = new ActionQueue(player);
        when(player.getActions()).thenReturn(actions);
        TimeSource source = mock(TimeSource.class);
        when(source.ready(3)).thenReturn(true, false);
        AtomicInteger effects = new AtomicInteger();
        for (int click = 0; click < 2; click++) {
            actions.submit(new ConsumeAction(player, source, 3) {
                @Override public void execute() { effects.incrementAndGet(); }
            });
        }
        assertEquals(1, effects.get());
        assertEquals(0, actions.size());
        actions.process();
        assertEquals(1, effects.get());
    }

    @Test void aStrongActionSuppressesConsumptionWithoutUsingTheThrottle() {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        ActionQueue actions = new ActionQueue(player);
        when(player.getActions()).thenReturn(actions);
        actions.submit(new Action<Player>(player, ActionType.STRONG) {
            @Override public boolean run() { return false; }
        });
        TimeSource source = mock(TimeSource.class);
        ConsumeAction consume = new ConsumeAction(player, source, 3) {
            @Override public void execute() { fail("Strong action must prevent consumption"); }
        };
        actions.submit(consume);
        assertEquals(ActionState.INTERRUPTED, consume.getState());
        verifyNoInteractions(source);
    }
}
