package io.luna.game.model.mob.movement;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.path.GamePathfinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that finished navigation releases target sessions without detaching replacement requests.
 *
 * @author lare96
 */
class WalkingNavigatorLifecycleTest {

    private Player player;
    private WalkingNavigator navigator;

    @BeforeEach
    void setup() {
        player = mock(Player.class, RETURNS_DEEP_STUBS);
        navigator = spy(new WalkingNavigator(player));
        doReturn(mock(GamePathfinder.class)).when(navigator).getDefaultPathfinder();
        when(player.getNavigator()).thenReturn(navigator);
    }

    @Test
    void completedActionReleasesTargetBeforeNotifyingTheCaller() {
        NavigationRequest request = request(new Position(101, 100));
        navigator.submit(request);
        NavigationAction action = submittedAction();
        CompletableFuture<Void> callback = request.getPending().thenRun(() -> assertNull(active()));

        action.onFinished();
        callback.join();

        assertNull(active());
        assertEquals(NavigationResult.DIDNT_REACH, request.getPending().join());
        assertNull(navigator.getCurrentPending());
    }

    @Test
    void oldActionFinishingAfterReplacementDoesNotDetachTheNewRequest() {
        NavigationRequest old = request(new Position(101, 100));
        NavigationRequest replacement = request(new Position(102, 100));
        navigator.submit(old);
        NavigationAction oldAction = submittedAction();
        navigator.submit(replacement);

        oldAction.onFinished();

        assertSame(replacement, active());
        assertSame(replacement.getPending(), navigator.getCurrentPending());
        assertFalse(replacement.getPending().isDone());
    }

    @Test
    void cancellationDetachesBeforeCallbacksSubmitAnotherRequest() {
        NavigationRequest old = request(new Position(101, 100));
        NavigationRequest replacement = request(new Position(102, 100));
        navigator.submit(old);
        AtomicBoolean detachedDuringCallback = new AtomicBoolean();
        old.getPending().whenComplete((result, error) -> {
            detachedDuringCallback.set(active() == null);
            navigator.submit(replacement);
        });

        navigator.cancel();

        assertTrue(old.getPending().isCancelled());
        assertTrue(detachedDuringCallback.get());
        assertSame(replacement, active());
        assertSame(replacement.getPending(), navigator.getCurrentPending());
    }

    @Test
    void cancelAlsoReleasesARequestWhoseFutureWasAlreadyCompleted() {
        NavigationRequest request = request(mock(Player.class));
        navigator.submit(request);
        request.getPending().complete(NavigationResult.REACHED);

        navigator.cancel();

        assertNull(active());
        assertEquals(NavigationResult.REACHED, request.getPending().join());
    }

    @Test
    void completionCallbackCanStartAnEquivalentNewAttempt() {
        Position destination = new Position(101, 100);
        NavigationRequest old = request(destination);
        NavigationRequest retry = request(destination);
        assertEquals(old, retry);
        navigator.submit(old);
        NavigationAction action = submittedAction();
        CompletableFuture<Void> callback = old.getPending().thenRun(() -> navigator.submit(retry));

        action.onFinished();
        callback.join();

        assertSame(retry, active());
        assertSame(retry.getPending(), navigator.getCurrentPending());
        assertFalse(retry.getPending().isDone());
    }

    @Test
    void reachingAPlayerReleasesTheFinishedNavigationTarget() {
        Player target = mock(Player.class);
        when(player.getPosition()).thenReturn(new Position(100, 100));
        when(target.abs()).thenReturn(new Position(101, 100));
        when(target.isAlive()).thenReturn(true);
        when(player.getWorld().getCollisionManager().reached(eq(player), eq(target), any())).thenReturn(true);
        NavigationRequest request = request(target);
        navigator.submit(request);
        NavigationAction action = submittedAction();

        assertTrue(action.run());
        action.onFinished();

        assertEquals(NavigationResult.REACHED, request.getPending().join());
        assertNull(active());
    }

    private NavigationRequest request(io.luna.game.model.Locatable target) {
        return NavigationRequest.builder(player)
                .target(target)
                .policy(InteractionPolicy.STANDARD_SIZE)
                .build();
    }

    private NavigationAction submittedAction() {
        ArgumentCaptor<NavigationAction> action = ArgumentCaptor.forClass(NavigationAction.class);
        verify(player).submitAction(action.capture());
        return action.getValue();
    }

    private NavigationRequest active() {
        try {
            Field field = WalkingNavigator.class.getDeclaredField("active");
            field.setAccessible(true);
            return (NavigationRequest) field.get(navigator);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
