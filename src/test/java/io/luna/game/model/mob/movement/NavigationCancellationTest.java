package io.luna.game.model.mob.movement;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.mob.interact.InteractionType;
import io.luna.game.model.path.GamePathfinder;
import io.luna.game.model.path.PathResult;
import io.luna.game.model.path.PathResultType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NavigationCancellationTest {
    private final Mob mob = mock(Mob.class, RETURNS_DEEP_STUBS);
    private final Deque<Runnable> gameTasks = new ArrayDeque<>();
    private ControlledNavigator navigator;

    /** Models a search that has already started and cannot be stopped. */
    private static class RunningSearch extends CompletableFuture<Deque<Position>> {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }
    }

    private static class ControlledNavigator extends WalkingNavigator {
        final List<CompletableFuture<Deque<Position>>> searches = new ArrayList<>();

        ControlledNavigator(Mob mob) {
            super(mob);
        }

        @Override
        public CompletableFuture<Deque<Position>> findPath(Position start, Position target,
                                                          GamePathfinder<Position> pathfinder, boolean async) {
            CompletableFuture<Deque<Position>> search = new RunningSearch();
            searches.add(search);
            return search;
        }
    }

    @BeforeEach
    void setup() {
        when(mob.getPosition()).thenReturn(new Position(3200, 3200));
        when(mob.getWalking().isEmpty()).thenReturn(true);
        when(mob.getService().getGameExecutor()).thenReturn((Executor) gameTasks::addLast);
        navigator = new ControlledNavigator(mob);
        when(mob.getNavigator()).thenReturn(navigator);
    }

    private NavigationRequest request(Position target, boolean continuous) {
        NavigationRequest request = mock(NavigationRequest.class);
        when(request.getTarget()).thenReturn(target);
        when(request.getPending()).thenReturn(new CompletableFuture<>());
        when(request.getPolicy()).thenReturn(new InteractionPolicy(InteractionType.SIZE, 0));
        when(request.isContinuous()).thenReturn(continuous);
        return request;
    }

    private Deque<Position> path(Position target) {
        return new ArrayDeque<>(List.of(target));
    }

    private void runGameTasks() {
        while (!gameTasks.isEmpty()) {
            gameTasks.removeFirst().run();
        }
    }

    @Test
    void currentPathAppliesOnGameExecutor() {
        Position target = new Position(3202, 3200);
        NavigationRequest request = request(target, false);
        navigator.submit(request);
        CompletableFuture<Void> pending = navigator.walk(request, target);
        Deque<Position> path = path(target);
        navigator.searches.getFirst().complete(path);
        verify(mob.getWalking(), never()).replacePath(any());
        runGameTasks();
        verify(mob.getWalking()).replacePath(path);
        assertTrue(pending.isDone());
        assertFalse(pending.isCancelled());
    }

    @Test
    void cancellationDiscardsAlreadyQueuedPath() {
        Position target = new Position(3202, 3200);
        NavigationRequest request = request(target, false);
        navigator.submit(request);
        CompletableFuture<Void> pending = navigator.walk(request, target);
        navigator.searches.getFirst().complete(path(target));
        assertFalse(gameTasks.isEmpty());
        pending.cancel(false);
        runGameTasks();
        verify(mob.getWalking(), never()).replacePath(any());
        assertTrue(pending.isCancelled());
    }

    @Test
    void cancelledRequestDiscardsRunningSearchResult() {
        Position target = new Position(3202, 3200);
        NavigationRequest request = request(target, false);
        navigator.submit(request);
        navigator.walk(request, target);
        navigator.cancel();
        navigator.searches.getFirst().complete(path(target));
        runGameTasks();
        verify(mob.getWalking(), never()).replacePath(any());
    }

    @Test
    void replacementCannotBeOverwrittenByOlderResult() {
        Position oldTarget = new Position(3202, 3200);
        NavigationRequest old = request(oldTarget, false);
        navigator.submit(old);
        navigator.walk(old, oldTarget);
        Position newTarget = new Position(3203, 3200);
        NavigationRequest latest = request(newTarget, false);
        navigator.submit(latest);
        navigator.walk(latest, newTarget);
        Deque<Position> latestPath = path(newTarget);
        navigator.searches.get(1).complete(latestPath);
        runGameTasks();
        navigator.searches.get(0).complete(path(oldTarget));
        runGameTasks();
        verify(mob.getWalking(), times(1)).replacePath(latestPath);
        verify(mob.getWalking(), times(1)).replacePath(any());
    }

    @Test
    void movingTargetRepathDiscardsOlderSearchWithinSameRequest() {
        Position first = new Position(3202, 3200);
        NavigationRequest request = request(first, true);
        navigator.submit(request);
        NavigationAction action = new NavigationAction(mob, request);
        assertFalse(action.run());
        Position moved = new Position(3203, 3200);
        when(request.getTarget()).thenReturn(moved);
        assertFalse(action.run());
        assertEquals(2, navigator.searches.size());
        Deque<Position> latestPath = path(moved);
        navigator.searches.get(1).complete(latestPath);
        runGameTasks();
        navigator.searches.get(0).complete(path(first));
        runGameTasks();
        verify(mob.getWalking(), times(1)).replacePath(any());
        verify(mob.getWalking()).replacePath(latestPath);
    }

    @Test
    void unchangedTargetKeepsPendingSearchAcrossTicks() {
        Position target = new Position(3202, 3200);
        NavigationRequest request = request(target, true);
        navigator.submit(request);
        NavigationAction action = new NavigationAction(mob, request);
        assertFalse(action.run());
        assertFalse(action.run());
        assertEquals(1, navigator.searches.size());
        Deque<Position> path = path(target);
        navigator.searches.getFirst().complete(path);
        runGameTasks();
        verify(mob.getWalking()).replacePath(path);
    }

    @Test
    void finishedActionDiscardsResultAfterInterruption() {
        Position target = new Position(3202, 3200);
        NavigationRequest request = request(target, false);
        navigator.submit(request);
        NavigationAction action = new NavigationAction(mob, request);
        assertFalse(action.run());
        action.onFinished();
        assertEquals(NavigationResult.DIDNT_REACH, request.getPending().join());
        navigator.searches.getFirst().complete(path(target));
        runGameTasks();
        verify(mob.getWalking(), never()).replacePath(any());
    }

    @Test
    void cancellingExceptionWrapperCancelsUnderlyingFuture() {
        CompletableFuture<Void> raw = new CompletableFuture<>();
        CompletableFuture<Void> handled = navigator.handleExceptions(new Position(3202, 3200), raw);
        handled.cancel(false);
        assertTrue(raw.isCancelled());
    }

    @Test
    @SuppressWarnings("unchecked")
    void synchronousPathfindingStillAppliesCurrentResult() {
        WalkingNavigator real = new WalkingNavigator(mob);
        when(mob.getNavigator()).thenReturn(real);
        Position target = new Position(3202, 3200);
        NavigationRequest request = request(target, false);
        GamePathfinder<Position> finder = mock(GamePathfinder.class);
        Deque<Position> path = path(target);
        when(request.getPathfinder()).thenReturn(finder);
        when(finder.find(mob.getPosition(), target)).thenReturn(new PathResult<>(PathResultType.COMPLETE, path));
        real.submit(request);
        CompletableFuture<Void> pending = real.walk(request, target);
        runGameTasks();
        verify(mob.getWalking()).replacePath(path);
        assertTrue(pending.isDone());
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelledQueuedSearchDoesNotInvokePathfinder() throws Exception {
        WalkingNavigator real = new WalkingNavigator(mob);
        Position start = mob.getPosition();
        Position target = new Position(3202, 3200);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        GamePathfinder<Position> blocker = mock(GamePathfinder.class);
        when(blocker.find(start, target)).thenAnswer(invocation -> {
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new PathResult<>(PathResultType.COMPLETE, path(target));
        });
        CompletableFuture<Deque<Position>> first = real.findPath(start, target, blocker, true);
        CompletableFuture<Deque<Position>> second = real.findPath(start, target, blocker, true);
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            GamePathfinder<Position> queued = mock(GamePathfinder.class);
            when(queued.find(start, target)).thenReturn(new PathResult<>(PathResultType.COMPLETE, path(target)));
            CompletableFuture<Deque<Position>> cancelled = real.findPath(start, target, queued, true);
            assertTrue(cancelled.cancel(false));
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            real.findPath(start, target, blocker, true).get(5, TimeUnit.SECONDS);
            verify(queued, never()).find(any(), any());
        } finally {
            release.countDown();
            first.cancel(false);
            second.cancel(false);
        }
    }
}
