package io.luna.game.action;

import io.luna.game.model.EntityType;
import io.luna.game.model.mob.Mob;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActionQueueTest {
    private static class PersistentAction extends Action<Mob> {
        private int submissions;

        PersistentAction(Mob mob) {
            super(mob, ActionType.SOFT);
        }

        @Override public void onSubmit() { submissions++; }
        @Override public boolean run() { return false; }
    }

    @Test void duplicateSubmissionDoesNotInvokeSetup() {
        Mob actor = mock(Mob.class);
        ActionQueue queue = queue(actor);
        PersistentAction first = new PersistentAction(actor);
        PersistentAction duplicate = new PersistentAction(actor);
        assertTrue(queue.submitIfAbsent(first));
        assertFalse(queue.submitIfAbsent(duplicate));
        assertEquals(0, duplicate.submissions);
        assertEquals(ActionState.NEW, duplicate.getState());
        assertEquals(1, queue.size());
    }

    @Test void finishingOneOfTwoSameClassActionsStillBlocksDuplicates() {
        Mob actor = mock(Mob.class);
        ActionQueue queue = queue(actor);
        PersistentAction first = new PersistentAction(actor);
        PersistentAction second = new PersistentAction(actor);
        queue.submit(first);
        queue.submit(second);
        first.complete();
        assertFalse(queue.submitIfAbsent(new PersistentAction(actor)));
        second.interrupt();
        assertTrue(queue.submitIfAbsent(new PersistentAction(actor)));
        assertEquals(1, queue.size());
    }

    @Test void finishingDuringSubmissionDoesNotUnregisterAnExistingAction() {
        Mob actor = mock(Mob.class);
        ActionQueue queue = queue(actor);
        class ConditionalAction extends PersistentAction {
            private final boolean finishOnSubmit;
            ConditionalAction(boolean finishOnSubmit) {
                super(actor);
                this.finishOnSubmit = finishOnSubmit;
            }
            @Override public void onSubmit() {
                if (finishOnSubmit) complete();
            }
        }
        queue.submit(new ConditionalAction(false));
        queue.submit(new ConditionalAction(true));
        assertFalse(queue.submitIfAbsent(new ConditionalAction(false)));
        assertEquals(1, queue.size());
    }

    @Test void completionHookCanSubmitReplacementBeforeQueueCleanup() {
        Mob actor = mock(Mob.class);
        ActionQueue queue = queue(actor);
        class ReplacingAction extends PersistentAction {
            ReplacingAction() { super(actor); }
            @Override public void onFinished() {
                assertTrue(queue.submitIfAbsent(new ReplacingAction()));
            }
        }
        ReplacingAction original = new ReplacingAction();
        queue.submit(original);
        original.complete();
        assertFalse(queue.submitIfAbsent(new ReplacingAction()));
        assertEquals(1, queue.size());
    }

    @Test void uniquenessUsesExactClassesAndRejectsAnonymousActions() {
        Mob actor = mock(Mob.class);
        ActionQueue queue = queue(actor);
        class OtherAction extends PersistentAction {
            OtherAction() { super(actor); }
        }
        assertTrue(queue.submitIfAbsent(new PersistentAction(actor)));
        assertTrue(queue.submitIfAbsent(new OtherAction()));
        assertThrows(IllegalStateException.class,
                () -> queue.submitIfAbsent(action(actor, ActionType.SOFT, () -> {})));
    }

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
