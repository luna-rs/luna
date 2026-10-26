package io.luna.game.model.mob.combat;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.combat.state.CombatContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies the exclusive tick boundaries used by recent combat, single-combat ownership, and logout.
 * <p>
 * The world clock is advanced explicitly so no assertion depends on elapsed wall time. Ownership expiry, combat
 * refreshes, and full context resets are checked against the real combat-window methods.
 *
 * @author lare96
 */
class CombatWindowTest {

    @BeforeAll static void bindings() {
        api.bot.script.InventoryProductionFixtures.INSTANCE.initialize();
    }

    /**
     * A combat context whose elapsed time is controlled by a mock world tick.
     *
     * @author lare96
     */
    private static class Fixture {
        /**
         * The mob owning the combat-window state.
         */
        final Mob mob = mock(Mob.class, RETURNS_DEEP_STUBS);
        /**
         * The world tick observed by the combat timer.
         */
        final AtomicLong tick = new AtomicLong(100);
        /**
         * The real combat-window methods bound to the controlled clock.
         */
        final CombatContext<?> combat;

        /**
         * Creates an unstarted combat timer bound to the controlled world clock.
         */
        Fixture() {
            when(mob.getWorld().getCurrentTick()).thenAnswer(call -> tick.get());
            when(mob.getPosition()).thenReturn(new Position(100, 100));
            combat = mock(CombatContext.class, withSettings().useConstructor(mob).defaultAnswer(CALLS_REAL_METHODS));
            doReturn(combat).when(mob).getCombat();
        }
    }

    @Test void ownershipExpiresBeforeTheIndependentRecentCombatAndLogoutWindows() {
        Fixture f = new Fixture();
        assertFalse(f.combat.inCombat());
        assertFalse(f.combat.isSingleCombatLocked());
        assertFalse(f.combat.isLogoutBlocked());
        f.combat.resetCombatTimer();
        f.tick.set(107);
        assertTrue(f.combat.isSingleCombatLocked());
        f.tick.set(108);
        assertFalse(f.combat.isSingleCombatLocked());
        assertTrue(f.combat.inCombat());
        assertTrue(f.combat.isLogoutBlocked());
        f.tick.set(116);
        assertTrue(f.combat.isLogoutBlocked());
        f.tick.set(117);
        assertFalse(f.combat.inCombat());
        assertFalse(f.combat.isLogoutBlocked());
    }

    @Test void singleCombatChecksUseOwnershipRatherThanTheLogoutWindow() {
        Fixture f = new Fixture();
        Mob oldOpponent = mock(Mob.class);
        Mob newOpponent = mock(Mob.class, RETURNS_DEEP_STUBS);
        when(newOpponent.getPosition()).thenReturn(new Position(101, 100));
        f.combat.setLastCombatWith(oldOpponent);
        f.combat.resetCombatTimer();
        assertFalse(f.combat.checkMultiCombat(newOpponent));
        f.tick.set(108);
        assertTrue(f.combat.inCombat());
        assertTrue(f.combat.checkMultiCombat(newOpponent));
    }

    @Test void aNewAttackRefreshesWindowsAndResetClearsThem() {
        Fixture f = new Fixture();
        Mob attacker = mock(Mob.class);
        f.combat.markAttackedBy(attacker);
        f.tick.set(107);
        f.combat.markAttackedBy(attacker);
        f.tick.set(114);
        assertTrue(f.combat.isSingleCombatLocked());
        f.tick.set(115);
        assertFalse(f.combat.isSingleCombatLocked());
        assertTrue(f.combat.isLogoutBlocked());
        f.combat.reset();
        assertFalse(f.combat.inCombat());
        assertFalse(f.combat.isLogoutBlocked());
        assertNull(f.combat.getLastCombatWith());
        assertEquals(0, f.combat.getAttackDelay());
    }

    @Test void repeatedReadsDoNotAdvanceGameplayTime() {
        Fixture f = new Fixture();
        f.combat.resetCombatTimer();
        for (int read = 0; read < 100; read++) {
            assertEquals(0, f.combat.getTimer().getDurationTicks());
            assertTrue(f.combat.isSingleCombatLocked());
        }
    }
}
