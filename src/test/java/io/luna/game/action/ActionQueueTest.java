package io.luna.game.action;

import io.luna.game.model.EntityType;
import io.luna.game.model.mob.Mob;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActionQueueTest {
    private ActionQueue queue(Mob mob) {
        ActionQueue queue = new ActionQueue(mob);
        when(mob.getActions()).thenReturn(queue);
        when(mob.getType()).thenReturn(EntityType.NPC);
        return queue;
    }

    private Action<Mob> action(Mob mob, ActionType type, Runnable callback) {
        return new Action<>(mob, type) {
            @Override public boolean run() {
                callback.run();
                return true;
            }
        };
    }

    @Test void idleQueueStillAcceptsAndCompletesNewActions() {
        Mob mob = mock(Mob.class);
        ActionQueue queue = queue(mob);
        for (int i = 0; i < 10; i++) {
            queue.process();
            queue.normalize();
        }
        List<String> ran = new ArrayList<>();
        Action<Mob> action = action(mob, ActionType.NORMAL, () -> ran.add("ran"));
        queue.submit(action);
        queue.process();
        queue.normalize();
        assertEquals(List.of("ran"), ran);
        assertEquals(ActionState.COMPLETED, action.getState());
        queue.process();
        queue.normalize();
        assertEquals(0, queue.size());
        assertEquals(List.of("ran"), ran);
    }

    @Test void nestedSubmissionIsRetainedForTheNextCycle() {
        Mob mob = mock(Mob.class);
        ActionQueue queue = queue(mob);
        List<String> ran = new ArrayList<>();
        Action<Mob> child = action(mob, ActionType.NORMAL, () -> ran.add("child"));
        queue.submit(action(mob, ActionType.NORMAL, () -> {
            ran.add("parent");
            queue.submit(child);
        }));
        queue.process();
        queue.normalize();
        assertEquals(List.of("parent"), ran);
        assertEquals(ActionState.PROCESSING, child.getState());
        queue.process();
        queue.normalize();
        assertEquals(List.of("parent", "child"), ran);
        assertEquals(ActionState.COMPLETED, child.getState());
    }

    @Test void strongActionsContinueToInterruptWeakActions() {
        Mob mob = mock(Mob.class);
        ActionQueue queue = queue(mob);
        List<String> ran = new ArrayList<>();
        Action<Mob> weak = action(mob, ActionType.WEAK, () -> ran.add("weak"));
        queue.submit(weak);
        queue.submit(action(mob, ActionType.STRONG, () -> ran.add("strong")));
        queue.process();
        queue.normalize();
        assertEquals(ActionState.INTERRUPTED, weak.getState());
        assertEquals(List.of("strong"), ran);
    }
}
