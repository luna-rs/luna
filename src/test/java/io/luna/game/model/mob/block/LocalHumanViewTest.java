package io.luna.game.model.mob.block;

import io.luna.game.model.EntityState;
import io.luna.game.model.World;
import io.luna.game.model.mob.MobReference;
import io.luna.game.model.mob.MobReferenceFixtures;
import io.luna.game.model.mob.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that observer storage contains session identities rather than owning player objects.
 *
 * @author lare96
 */
class LocalHumanViewTest {

    @Test
    void storesTheCachedSessionHandleAndAvoidsDuplicateRelationships() throws ReflectiveOperationException {
        Player player = player(mock(World.class, RETURNS_DEEP_STUBS), 1);
        LocalHumanView view = new LocalHumanView();

        assertTrue(view.add(player));
        assertFalse(view.add(player));

        assertSame(player.getReference(), handles(view).iterator().next());
        assertSame(player, view.iterator().next());
        assertEquals(1, view.size());
        assertTrue(view.isNotEmpty());
    }

    @Test
    void anInactiveSessionDisappearsEvenWithoutExplicitDetachment() throws ReflectiveOperationException {
        Player player = player(mock(World.class, RETURNS_DEEP_STUBS), 1);
        LocalHumanView view = new LocalHumanView();
        view.add(player);

        when(player.getState()).thenReturn(EntityState.INACTIVE);

        assertTrue(view.isEmpty());
        assertFalse(view.iterator().hasNext());
        assertTrue(handles(view).isEmpty());
    }

    @Test
    void slotReuseCannotRedirectAnOldObserverHandleToAnotherSession() {
        World world = mock(World.class, RETURNS_DEEP_STUBS);
        Player oldSession = player(world, 1);
        LocalHumanView view = new LocalHumanView();
        view.add(oldSession);
        Player replacement = player(world, 1);

        assertTrue(view.isEmpty());
        assertTrue(view.add(replacement));
        assertFalse(view.remove(oldSession));
        assertSame(replacement, view.iterator().next());
    }

    @Test
    void removingOneSessionDoesNotRemoveAnotherWithTheSameUsernameHash() {
        World world = mock(World.class, RETURNS_DEEP_STUBS);
        Player first = player(world, 1);
        Player second = player(world, 2);
        when(first.getUsernameHash()).thenReturn(42L);
        when(second.getUsernameHash()).thenReturn(42L);
        LocalHumanView view = new LocalHumanView();
        view.add(first);
        view.add(second);

        assertTrue(view.remove(first));
        assertFalse(view.remove(first));
        assertEquals(1, view.size());
        assertSame(second, view.iterator().next());
    }

    private static Player player(World world, int slot) {
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        MobReferenceFixtures.register(player, slot);
        return player;
    }

    @SuppressWarnings("unchecked")
    private static Set<MobReference> handles(LocalHumanView view) throws ReflectiveOperationException {
        Field field = LocalHumanView.class.getDeclaredField("observers");
        field.setAccessible(true);
        return (Set<MobReference>) field.get(view);
    }
}
