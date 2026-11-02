package io.luna.game.model.mob.combat.state;

import api.bot.script.InventoryProductionFixtures;
import io.luna.game.action.Action;
import io.luna.game.action.ActionQueue;
import io.luna.game.action.ActionType;
import io.luna.game.event.impl.NpcClickEvent.AttackNpcEvent;
import io.luna.game.event.impl.NpcClickEvent.NpcSecondClickEvent;
import io.luna.game.model.EntityState;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.mob.MobReference;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.CombatAction;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.interact.InteractionAction;
import io.luna.game.model.mob.interact.InteractionActionListener;
import io.luna.game.model.mob.interact.InteractionPolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that prepared attacks belong to their approach or combat action and end with that operation.
 *
 * @author lare96
 */
class PreparedAttackLogoutTest {

    @BeforeAll
    static void initializeCombat() {
        InventoryProductionFixtures.INSTANCE.initializeCombat();
    }

    /**
     * A real preparation cache and action queue bound to controllable reach and target identities.
     *
     * @author lare96
     */
    private static final class Fixture {
        final Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        final PlayerCombatContext combat = new PlayerCombatContext(player);
        final ActionQueue actions = new ActionQueue(player);
        final Npc target = target(1);

        Fixture() {
            when(player.getCombat()).thenReturn(combat);
            when(player.getActions()).thenReturn(actions);
            when(player.getPosition()).thenReturn(new Position(100, 100));
            when(player.getType()).thenReturn(EntityType.PLAYER);
            when(player.getState()).thenReturn(EntityState.ACTIVE);
        }

        Npc target(int index) {
            Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
            when(npc.getState()).thenReturn(EntityState.ACTIVE);
            when(npc.getPosition()).thenReturn(new Position(101, 100));
            when(npc.getIndex()).thenReturn(index);
            when(npc.getIncarnation()).thenReturn((long) index);
            MobReference reference = new MobReference(player.getWorld(), EntityType.NPC, index, index);
            when(npc.getReference()).thenReturn(reference);
            when(player.getWorld().getNpcs().get(index)).thenReturn(npc);
            return npc;
        }

        CombatAttack<Player> prepare(Npc victim) {
            CombatAttack<Player> attack = mock(CombatAttack.class);
            when(attack.getAttacker()).thenReturn(player);
            when(attack.getVictim()).thenReturn(victim);
            combat.setFirstAttack(attack);
            return attack;
        }

        InteractionAction approach(Npc victim, Runnable listener) {
            return new InteractionAction(player, new ArrayList<>(List.of(
                    new InteractionActionListener(InteractionPolicy.STANDARD_SIZE, listener))),
                    victim, new AttackNpcEvent(player, victim));
        }

        void reached(Npc victim) {
            when(player.getWorld().getCollisionManager().reached(player, victim, InteractionPolicy.STANDARD_SIZE))
                    .thenReturn(true);
        }
    }

    @Test
    void anUnreachableAttemptDiscardsItsPreparationWhenItFinishes() {
        Fixture f = new Fixture();
        CombatAttack<Player> prepared = f.prepare(f.target);
        when(f.player.getWalking().isEmpty()).thenReturn(true);
        f.actions.submit(f.approach(f.target, () -> fail("Unreachable listener must not run.")));
        assertSame(prepared, f.combat.getFirstAttack());

        f.actions.process();

        assertNull(f.combat.getFirstAttack());
    }

    @Test
    void aThrowingSubmissionListenerClosesPreparationBeforeTheActionCanBeQueued() {
        Fixture f = new Fixture();
        f.prepare(f.target);
        f.reached(f.target);
        IllegalStateException failure = new IllegalStateException("fixture listener failure");
        InteractionAction approach = f.approach(f.target, () -> { throw failure; });

        assertSame(failure, assertThrows(IllegalStateException.class, () -> f.actions.submit(approach)));

        assertTrue(approach.isFinished());
        assertNull(f.combat.getFirstAttack());
        assertEquals(0, f.actions.size());
    }

    @Test
    void aLongApproachRetainsPreparationUntilItIsCancelled() {
        Fixture f = new Fixture();
        CombatAttack<Player> prepared = f.prepare(f.target);
        when(f.player.getWalking().isEmpty()).thenReturn(false);
        InteractionAction approach = f.approach(f.target, () -> { });
        f.actions.submit(approach);

        for (int tick = 0; tick < 40; tick++) {
            f.actions.process();
            assertSame(prepared, f.combat.getFirstAttack());
        }
        approach.interrupt();

        assertNull(f.combat.getFirstAttack());
    }

    @Test
    void clearingTheOwningQueueClosesThePreparedAttack() {
        Fixture f = new Fixture();
        f.prepare(f.target);
        f.actions.submit(f.approach(f.target, () -> { }));

        f.actions.clear();

        assertNull(f.combat.getFirstAttack());
    }

    @Test
    void anOlderActionCannotClearAReplacementActionsPreparation() {
        Fixture f = new Fixture();
        f.prepare(f.target);
        InteractionAction previous = f.approach(f.target, () -> { });
        f.actions.submit(previous);
        Npc replacement = f.target(2);
        CombatAttack<Player> newPreparation = f.prepare(replacement);
        InteractionAction next = f.approach(replacement, () -> { });
        f.actions.submit(next);

        previous.interrupt();
        assertSame(newPreparation, f.combat.getFirstAttack());
        next.interrupt();
        assertNull(f.combat.getFirstAttack());
    }

    @Test
    void endingAnOlderFightDoesNotClearANewInteractionsPreparation() {
        Fixture f = new Fixture();
        CombatAction previousFight = new CombatAction(f.player);
        f.actions.submit(previousFight);
        CombatAttack<Player> preparation = f.prepare(f.target);
        InteractionAction approach = f.approach(f.target, () -> { });

        f.actions.submit(approach);

        assertTrue(previousFight.isFinished());
        assertSame(preparation, f.combat.getFirstAttack());
        approach.interrupt();
        assertNull(f.combat.getFirstAttack());
    }

    @Test
    void anAcceptedCombatActionConsumesTheOriginalPreparedAttack() {
        Fixture f = new Fixture();
        CombatAttack<Player> prepared = f.prepare(f.target);
        f.reached(f.target);
        f.actions.submit(f.approach(f.target, () -> {
            f.combat.setTarget(f.target);
            f.actions.submit(new CombatAction(f.player));
        }));

        assertSame(prepared, f.combat.getFirstAttack());
        assertSame(prepared, f.combat.getNextAttack(f.target));
        assertNull(f.combat.getFirstAttack());
    }

    @Test
    void manualSpellPreparationIsClosedWhenItsListenerDoesNotStartCombat() {
        Fixture f = new Fixture();
        f.reached(f.target);
        f.actions.submit(f.approach(f.target, () -> f.prepare(f.target)));

        assertNull(f.combat.getFirstAttack());
    }

    @Test
    void deferredListenersOwnPreparationUntilTheyExecute() {
        Fixture f = new Fixture();
        CombatAttack<Player> prepared = f.prepare(f.target);
        f.reached(f.target);
        InteractionAction approach = new InteractionAction(f.player, new ArrayList<>(List.of(
                new InteractionActionListener(InteractionPolicy.STANDARD_SIZE, () -> { }))),
                f.target, new NpcSecondClickEvent(f.player, f.target));
        f.actions.submit(approach);
        assertSame(prepared, f.combat.getFirstAttack());
        Action<?> deferred = f.actions.getAll(ActionType.STRONG).getFirst();

        assertTrue(deferred.run());
        deferred.complete();

        assertNull(f.combat.getFirstAttack());
    }

    @Test
    void rejectsPreparationThatReferencesAnInactiveParticipant() {
        Fixture f = new Fixture();
        CombatAttack<Player> prepared = f.prepare(f.target);
        when(f.player.getState()).thenReturn(EntityState.INACTIVE);

        assertNull(f.combat.getFirstAttack());
        f.combat.setFirstAttack(prepared);
        assertNull(f.combat.getFirstAttack());
    }
}
