package io.luna.game.model.mob.combat;

import io.luna.game.action.ActionQueue;
import io.luna.game.model.EntityState;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.combat.attack.MeleeCombatAttack;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.interact.InteractionPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies recipient-owned combat deadlines, delivery order, and lifecycle cancellation.
 * <p>
 * The tests use a controllable world tick and the real queue and projectile schedulers. Shooter removal, recipient
 * cleanup, nested submissions, and directional melee are checked independently of a live server.
 *
 * @author lare96
 */
class CombatImpactQueueTest {

    /**
     * A recipient with a real incoming queue, a controllable clock, and recorded callback events.
     *
     * @author lare96
     */
    private static class Fixture {
        /**
         * The recipient that owns the scheduled events.
         */
        final Mob recipient;
        /**
         * The world tick used for event deadlines.
         */
        final AtomicLong tick = new AtomicLong(100);
        /**
         * Whether the recipient remains alive for delivery.
         */
        final AtomicBoolean alive = new AtomicBoolean(true);
        /**
         * The real recipient-owned queue under test.
         */
        final CombatImpactQueue queue;
        /**
         * The arrival and completion callbacks in observed order.
         */
        final List<String> events = new ArrayList<>();

        /**
         * Creates a queue fixture with a generic mocked recipient.
         */
        Fixture() { this(mock(Mob.class, RETURNS_DEEP_STUBS)); }

        /**
         * Creates a queue fixture for a specific recipient type.
         *
         * @param recipient The player, NPC, or generic mob receiving events.
         */
        Fixture(Mob recipient) {
            this.recipient = recipient;
            when(recipient.isAlive()).thenAnswer(call -> alive.get());
            when(recipient.getState()).thenReturn(EntityState.ACTIVE);
            when(recipient.getWorld().getCurrentTick()).thenAnswer(call -> tick.get());
            queue = new CombatImpactQueue(recipient);
            when(recipient.getCombat().getImpacts()).thenReturn(queue);
        }

        /**
         * Moves the world clock and processes one recipient queue pass.
         *
         * @param nextTick The world tick at which the queue should run.
         */
        void at(long nextTick) {
            tick.set(nextTick);
            queue.process();
        }

        /**
         * Schedules an event whose arrival and completion are recorded by name.
         *
         * @param delay The delay in game ticks.
         * @param name The label used in the event trace.
         */
        void schedule(int delay, String name) {
            queue.schedule(delay, () -> events.add(name + "@" + tick.get()),
                    interrupted -> events.add(name + ":" + interrupted));
        }
    }

    @Test void deadlinesUseWorldTicksAndEqualDeadlinesKeepSubmissionOrder() {
        Fixture f = new Fixture();
        f.schedule(3, "later");
        f.schedule(1, "first");
        f.schedule(1, "second");
        f.at(100);
        assertTrue(f.events.isEmpty());
        f.at(101);
        assertEquals(List.of("first@101", "first:false", "second@101", "second:false"), f.events);
        f.at(102);
        assertEquals(4, f.events.size());
        f.at(103);
        assertEquals(List.of("first@101", "first:false", "second@101", "second:false",
                "later@103", "later:false"), f.events);
    }

    @Test void nestedZeroDelayEventsWaitForAnotherPass() {
        Fixture f = new Fixture();
        f.queue.schedule(0, () -> f.schedule(0, "nested"), ignored -> { });
        f.at(100);
        assertTrue(f.events.isEmpty());
        f.at(101);
        assertEquals(List.of("nested@101", "nested:false"), f.events);
    }

    @Test void clearingDuringDeliveryDiscardsOtherDueEventsAndFinishesEachOnce() {
        Fixture f = new Fixture();
        f.queue.schedule(0, f.queue::clear, ignored -> f.events.add("clear"));
        f.schedule(0, "due");
        f.schedule(3, "future");
        f.at(100);
        f.queue.clear();
        f.at(105);
        assertEquals(List.of("future:true", "clear", "due:true"), f.events);
    }

    @Test void lethalFirstHitDiscardsTheSecondHitAndDoesNotCarryIntoRespawn() {
        Fixture f = new Fixture();
        f.queue.schedule(0, () -> f.alive.set(false), ignored -> { });
        f.schedule(0, "second");
        f.schedule(4, "future");
        f.at(100);
        f.at(101);
        f.alive.set(true);
        f.at(105);
        assertEquals(List.of("second:true", "future:true"), f.events);
    }

    @Test void removedRecipientsDiscardPendingAndNewEvents() {
        Fixture f = new Fixture();
        f.schedule(3, "pending");
        when(f.recipient.getState()).thenReturn(EntityState.INACTIVE);
        f.schedule(1, "new");
        f.at(101);
        assertEquals(List.of("new:true", "pending:true"), f.events);
    }

    /**
     * Exposes the real modern and legacy projectile schedulers while recording their lifecycle callbacks.
     *
     * @author lare96
     */
    private static class Projectile extends CombatAttack<Mob> {
        /**
         * The recipient fixture recording projectile callback order.
         */
        final Fixture fixture;

        /**
         * Creates a projectile whose callbacks are observed by the supplied fixture.
         *
         * @param attacker The mob launching the projectile.
         * @param fixture The recipient and event trace used for delivery.
         */
        Projectile(Mob attacker, Fixture fixture) {
            super(attacker, fixture.recipient, InteractionPolicy.STANDARD_LINE_OF_SIGHT, 4);
            this.fixture = fixture;
        }

        @Override public void attack() { scheduleProjectileArrival(3); }
        @Override public CombatDamage calculateDamage(Mob other) { return null; }
        @Override public void onProjectileLaunched() { fixture.events.add("launch@" + fixture.tick.get()); }
        @Override public void onProjectileReached() { fixture.events.add("hit@" + fixture.tick.get()); }
        @Override public void onProjectileFinished(boolean interrupted) { fixture.events.add("finish:" + interrupted); }
        /**
         * Schedules the legacy launch at the next tick and arrival three ticks after scheduling.
         */
        void legacy() { launchProjectile(0, 2); }
    }

    @Test void launchedProjectileSurvivesShooterQueueClearDeathAndRemoval() {
        Fixture f = new Fixture();
        Mob attacker = mock(Mob.class, RETURNS_DEEP_STUBS);
        ActionQueue actions = new ActionQueue(attacker);
        when(attacker.getActions()).thenReturn(actions);
        new Projectile(attacker, f).attack();
        actions.clear();
        when(attacker.isAlive()).thenReturn(false);
        when(attacker.getState()).thenReturn(EntityState.INACTIVE);
        f.at(102);
        assertTrue(f.events.isEmpty());
        f.at(103);
        assertEquals(List.of("hit@103", "finish:false"), f.events);
        assertEquals(0, actions.size());
    }

    @Test void legacyFlightKeepsItsLaunchAndArrivalOffsets() {
        Fixture f = new Fixture();
        new Projectile(mock(Mob.class), f).legacy();
        f.at(100);
        assertTrue(f.events.isEmpty());
        f.at(101);
        f.at(102);
        f.at(103);
        assertEquals(List.of("launch@101", "hit@103", "finish:false"), f.events);
    }

    @Test void clearingLegacyFlightCallsItsFinishHookOnlyOnce() {
        Fixture f = new Fixture();
        new Projectile(mock(Mob.class), f).legacy();
        f.queue.clear();
        f.queue.clear();
        f.at(110);
        assertEquals(List.of("finish:true"), f.events);
    }

    @Test void ordinaryMeleeUsesDirectionalDelaysIndependentOfPlayerIndexOrder() {
        for (boolean npcAttacker : List.of(false, true)) {
            Mob attacker = npcAttacker ? mock(Npc.class) : mock(Player.class);
            for (boolean playerVictim : List.of(false, true)) {
                Fixture f = new Fixture(playerVictim ? mock(Player.class, RETURNS_DEEP_STUBS) :
                        mock(Npc.class, RETURNS_DEEP_STUBS));
                CombatDamage damage = mock(CombatDamage.class);
                when(damage.getVictim()).thenReturn(f.recipient);
                when(damage.getAttacker()).thenReturn(attacker);
                doAnswer(call -> { f.events.add("hit@" + f.tick.get()); return null; })
                        .when(f.recipient).submitAction(any());
                MeleeCombatAttack<Mob> melee = new MeleeCombatAttack<>(attacker, f.recipient, -1, 1, 4) {
                    { nextDamage = damage; }
                };
                melee.attack();
                f.at(100);
                assertEquals(npcAttacker && playerVictim ? List.of("hit@100") : List.of(), f.events);
                f.at(101);
                assertEquals(List.of(npcAttacker && playerVictim ? "hit@100" : "hit@101"), f.events);
            }
        }
    }
}
