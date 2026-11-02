package io.luna.game.model.item;

import io.luna.game.model.EntityState;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.World;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies content deadlines remove exact ground drops even while tile overflow hides them.
 *
 * @author lare96
 */
final class GroundItemListExpiryTest {

    private GroundItemList items;
    private Task expiration;
    private Position position;
    private ItemDefinition definition;

    @BeforeEach
    void setUp() {
        World world = mock(World.class);
        items = new GroundItemList(world);
        position = new Position(3200, 3200);
        definition = mock(ItemDefinition.class);
        ArgumentCaptor<Task> scheduled = ArgumentCaptor.forClass(Task.class);
        items.startExpirationTask();
        verify(world).schedule(scheduled.capture());
        expiration = scheduled.getValue();
    }

    @Test
    void aPendingDropExpiresAtItsDeadlineAndNeverAppearsWhenTheTileFrees() throws Exception {
        List<GroundItem> visible = new ArrayList<>();
        for (int index = 0; index < 128; index++) {
            GroundItem item = drop();
            assertTrue(items.register(item));
            visible.add(item);
        }
        GroundItem pending = drop();
        assertTrue(items.register(pending));
        assertEquals(EntityState.NEW, pending.getState());
        verify(pending, never()).show();

        assertTrue(items.expire(pending));
        assertEquals(EntityState.INACTIVE, pending.getState());
        verify(pending, never()).hide();
        assertTrue(items.expire(visible.getFirst()));
        processExpiration();

        assertEquals(127, items.size());
        verify(pending, never()).show();
        assertFalse(items.expire(pending));
    }

    @Test
    void anOldDeadlineCannotRemoveAnIdenticalReplacementDrop() {
        GroundItem original = drop();
        assertTrue(items.register(original));
        assertTrue(items.expire(original));
        assertEquals(EntityState.INACTIVE, original.getState());
        verify(original).hide();

        GroundItem replacement = drop();
        assertTrue(items.register(replacement));
        assertFalse(items.expire(original));

        assertEquals(1, items.size());
        assertSame(replacement, items.iterator().next());
        assertEquals(EntityState.ACTIVE, replacement.getState());
        verify(replacement, never()).hide();
    }

    private GroundItem drop() {
        GroundItem item = mock(GroundItem.class);
        AtomicReference<EntityState> state = new AtomicReference<>(EntityState.NEW);
        when(item.getType()).thenReturn(EntityType.ITEM);
        when(item.getState()).thenAnswer(call -> state.get());
        doAnswer(call -> {
            state.set(call.getArgument(0));
            return null;
        }).when(item).setState(any(EntityState.class));
        when(item.getPosition()).thenReturn(position);
        when(item.def()).thenReturn(definition);
        when(item.getAmount()).thenReturn(1);
        return item;
    }

    private void processExpiration() throws Exception {
        Method execute = expiration.getClass().getDeclaredMethod("execute");
        execute.setAccessible(true);
        execute.invoke(expiration);
    }
}
