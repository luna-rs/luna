package io.luna.game.model.mob.bot;

import api.bot.action.BotActionHandler;
import api.bot.action.BotInteractionActionHandler;
import api.predef.PropertiesPredefKt;
import io.luna.LunaContext;
import io.luna.game.model.World;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.movement.NavigationResult;
import io.luna.game.plugin.PluginBootstrap;
import io.luna.game.task.Task;
import io.luna.game.task.TaskState;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.EmptyCoroutineContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs the actual suspend helper with completed navigation and a deterministic game-task fixture. */
class BotNpcInteractionTest {
    private static World world;
    private static Method runTask;
    private Bot bot;
    private Npc target;
    private BotInteractionActionHandler handler;
    private Continuation<Boolean> continuation;

    @BeforeAll static void initializeBindings() throws Exception {
        var bindings = PluginBootstrap.class.getDeclaredMethod("setBindings", LunaContext.class);
        bindings.setAccessible(true);
        bindings.invoke(null, mock(LunaContext.class, RETURNS_DEEP_STUBS));
        world = PropertiesPredefKt.getWorld();
        runTask = Task.class.getDeclaredMethod("runTask");
        runTask.setAccessible(true);
    }

    @BeforeEach void setUp() {
        bot = mock(Bot.class, RETURNS_DEEP_STUBS);
        target = mock(Npc.class);
        when(bot.getWorld()).thenReturn(world);
        when(bot.getNavigator().getCurrentPending()).thenReturn(null);
        when(bot.getCombat().getLastAttackSent()).thenReturn(null);
        when(bot.getNavigator().submit(any())).thenReturn(CompletableFuture.completedFuture(NavigationResult.REACHED));
        doAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            for (int tick = 0; tick < 25 && task.getState() != TaskState.CANCELLED; tick++) runTask.invoke(task);
            return null;
        }).when(world).schedule(any(Task.class));
        handler = new BotInteractionActionHandler(bot, mock(BotActionHandler.class));
        continuation = mock(Continuation.class);
        when(continuation.getContext()).thenReturn(EmptyCoroutineContext.INSTANCE);
    }

    @Test void failedNavigationDoesNotSendAnAttackClick() {
        when(bot.getNavigator().submit(any())).thenReturn(CompletableFuture.completedFuture(NavigationResult.DIDNT_REACH));
        assertEquals(false, handler.interact(3, target, continuation));
        verify(bot.getOutput(), never()).sendNpcInteraction(anyInt(), any());
    }

    @Test void reachingTargetWithoutAnAttackIsNotSuccess() {
        assertEquals(false, handler.interact(3, target, continuation));
        verify(bot.getOutput()).sendNpcInteraction(3, target);
        verify(bot.getCombat(), never()).attack(any());
    }

    @Test void attackAgainstRequestedNpcConfirmsSuccess() {
        var attack = mock(CombatAttack.class);
        when(attack.getVictim()).thenReturn(target);
        when(bot.getOutput().sendNpcInteraction(3, target)).thenAnswer(invocation -> {
            when(bot.getCombat().getLastAttackSent()).thenReturn(attack);
            return true;
        });
        assertEquals(true, handler.interact(3, target, continuation));
        verify(bot.getCombat(), never()).attack(any());
    }

    @Test void oldAttackOrAttackAgainstAnotherNpcDoesNotConfirmSuccess() {
        var oldAttack = mock(CombatAttack.class);
        when(oldAttack.getVictim()).thenReturn(target);
        when(bot.getCombat().getLastAttackSent()).thenReturn(oldAttack);
        assertEquals(false, handler.interact(3, target, continuation));
        var otherAttack = mock(CombatAttack.class);
        when(otherAttack.getVictim()).thenReturn(mock(Npc.class));
        when(bot.getOutput().sendNpcInteraction(3, target)).thenAnswer(invocation -> {
            when(bot.getCombat().getLastAttackSent()).thenReturn(otherAttack);
            return true;
        });
        assertEquals(false, handler.interact(3, target, continuation));
    }
}
