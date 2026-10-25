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

    @Test void clearInterruptsActiveActionsAndDoesNotRepeatFinishedCallbacks() {
        Mob mob = mock(Mob.class);
        ActionQueue queue = queue(mob);
        List<String> finished = new ArrayList<>();
        Action<Mob> active = trackedAction(mob, ActionType.SOFT, () -> finished.add("active"));
        Action<Mob> completed = trackedAction(mob, ActionType.NORMAL, () -> finished.add("completed"));
        Action<Mob> interrupted = trackedAction(mob, ActionType.WEAK, () -> finished.add("interrupted"));
        queue.submit(active);
        queue.submit(completed);
        queue.submit(interrupted);
        completed.complete();
        interrupted.interrupt();

        queue.clear();
        queue.clear();

        assertEquals(ActionState.INTERRUPTED, active.getState());
        assertEquals(ActionState.COMPLETED, completed.getState());
        assertEquals(ActionState.INTERRUPTED, interrupted.getState());
        assertEquals(List.of("completed", "interrupted", "active"), finished);
        assertEquals(0, queue.size());
        for (ActionType type : ActionType.values()) assertEquals(0, queue.size(type));
        assertNull(queue.first(Action.class));
    }

    @Test void clearDuringExecutionDiscardsPendingActionsAndAcceptsReplacement() {
        Mob mob = mock(Mob.class);
        ActionQueue queue = queue(mob);
        List<String> ran = new ArrayList<>();
        Action<Mob> replacement = action(mob, ActionType.SOFT, () -> ran.add("replacement"));
        Action<Mob> clearing = action(mob, ActionType.SOFT, () -> {
            ran.add("clear");
            queue.clear();
            assertEquals(0, queue.size());
            queue.submit(replacement);
        });
        Action<Mob> pending = action(mob, ActionType.SOFT, () -> ran.add("pending"));
        queue.submit(clearing);
        queue.submit(pending);

        queue.process();
        queue.normalize();

        assertEquals(List.of("clear"), ran);
        assertEquals(ActionState.INTERRUPTED, clearing.getState());
        assertEquals(ActionState.INTERRUPTED, pending.getState());
        assertSame(replacement, queue.first(Action.class));
        assertEquals(1, queue.size());
        queue.process();
        queue.normalize();
        assertEquals(List.of("clear", "replacement"), ran);
        assertEquals(0, queue.size());
    }

    @Test void countsExcludeFinishedActionsAndDoNotDoubleCountExecutionQueue() {
        Mob mob = mock(Mob.class);
        ActionQueue queue = queue(mob);
        Action<Mob> completed = action(mob, ActionType.SOFT, () -> {});
        Action<Mob> interrupted = action(mob, ActionType.NORMAL, () -> {});
        queue.submit(completed);
        queue.submit(interrupted);
        completed.complete();
        interrupted.interrupt();
        queue.submit(action(mob, ActionType.SOFT, () -> {
            assertEquals(3, queue.size());
            assertEquals(2, queue.size(ActionType.SOFT));
            assertEquals(1, queue.size(ActionType.NORMAL));
        }));
        queue.submit(action(mob, ActionType.SOFT, () -> {}));
        Action<Mob> normal = trackedAction(mob, ActionType.NORMAL, () -> {});
        queue.submit(normal);

        assertEquals(3, queue.size());
        assertEquals(2, queue.size(ActionType.SOFT));
        assertEquals(1, queue.size(ActionType.NORMAL));
        assertEquals(0, queue.size(ActionType.WEAK));
        assertEquals(0, queue.size(ActionType.STRONG));
        queue.process();
        assertEquals(1, queue.size());
        assertEquals(1, queue.size(ActionType.NORMAL));
        normal.interrupt();
        assertEquals(0, queue.size());
        for (ActionType type : ActionType.values()) assertEquals(0, queue.size(type));
    }

    private Action<Mob> trackedAction(Mob mob, ActionType type, Runnable finished) {
        return new Action<>(mob, type) {
            @Override public boolean run() { return false; }
            @Override public void onFinished() { finished.run(); }
        };
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
