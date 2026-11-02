package io.luna.game.model.mob;

import io.luna.LunaContext;
import io.luna.game.model.EntityState;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies non-owning world identities across logout, slot reuse, and same-name sessions.
 *
 * @author lare96
 */
class MobReferenceTest {

    @BeforeAll
    static void bindings() {
        api.bot.script.InventoryProductionFixtures.INSTANCE.initializeCombat();
    }

    @Test
    void slotReuseDoesNotRedirectAnOldReferenceToTheSameUsername() {
        LunaContext context = context();
        Player original = new Player(context, new PlayerCredentials("returning", "fixture"));
        MobReference reference = MobReferenceFixtures.register(original, 1);
        assertSame(original, reference.resolve());

        Player replacement = new Player(context, new PlayerCredentials("returning", "fixture"));
        MobReferenceFixtures.register(replacement, 1);

        assertEquals(original, replacement);
        assertNotEquals(original.getIncarnation(), replacement.getIncarnation());
        assertNull(reference.resolve());
        assertSame(replacement, replacement.getReference().resolve());
    }

    @Test
    void logoutInvalidatesResolutionEvenBeforeTheSlotIsRemoved() {
        LunaContext context = context();
        Player player = new Player(context, new PlayerCredentials("departing", "fixture"));
        MobReference reference = MobReferenceFixtures.register(player, 1);

        player.setState(EntityState.INACTIVE);

        assertNull(reference.resolve());
    }

    @Test
    void capturesReuseTheRegisteredHandleAndUnregistrationInvalidatesIt() {
        LunaContext context = context();
        Player player = new Player(context, new PlayerCredentials("registered", "fixture"));
        assertNull(MobReference.capture(player));
        MobReference reference = MobReferenceFixtures.register(player, 1);
        for (int read = 0; read < 100; read++) {
            assertSame(reference, MobReference.capture(player));
        }

        player.setIndex(-1);

        assertNull(MobReference.capture(player));
        assertNull(reference.resolve());
    }

    @Test
    void encodingClearsTheClientFacingIndexWhenItsSessionExpires() {
        LunaContext context = context();
        Player owner = new Player(context, new PlayerCredentials("observer", "fixture"));
        Player target = new Player(context, new PlayerCredentials("departing", "fixture"));
        MobReferenceFixtures.register(owner, 1);
        MobReferenceFixtures.register(target, 2);
        owner.interact(target);
        owner.buildBlockData();
        owner.resetFlags();

        target.setState(EntityState.INACTIVE);
        owner.buildBlockData();

        assertNull(owner.getInteractingWith());
        assertTrue(owner.getFlagData().contains(UpdateFlag.INTERACTION));
        assertEquals(65535, owner.getPendingBlockData().getInteract());
    }

    private static LunaContext context() {
        LunaContext context = mock(LunaContext.class, RETURNS_DEEP_STUBS);
        when(context.getGame().getThread()).thenReturn(Thread.currentThread());
        return context;
    }
}
