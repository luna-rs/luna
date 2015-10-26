package io.luna.game.plugin;

import io.luna.game.GameService;
import io.luna.game.model.World;
import io.luna.game.model.mobile.PlayerRights;
import io.luna.game.task.Task;

import java.util.concurrent.ThreadLocalRandom;

import scala.Function1;
import scala.Unit;

/**
 * An abstraction model that acts as the base class for every single
 * {@code Plugin}. It holds an abstract function containing the logic executed
 * by the underlying {@link PluginPipeline}. A collection of short functions are
 * used to shortcut to commonly used verbose functions, to keep all
 * {@code Plugin}s as short and as easy to write as possible.
 * 
 * @author lare96 <http://github.org/lare96>
 * @param <E> The underlying event type that this {@code Plugin} is listening
 *        for.
 */
public abstract class Plugin<E> {

    /**
     * The {@link World} instance.
     */
    protected World world;

    /**
     * The {@link PluginManager} instance.
     */
    protected PluginManager plugins;

    /**
     * The {@link GameService} instance.
     */
    protected GameService service;

    /**
     * A shortcut call to the function {@code PlayerRights.PLAYER}.
     */
    protected PlayerRights rightsPlayer() {
        return PlayerRights.PLAYER;
    }

    /**
     * A shortcut call to the function {@code PlayerRights.MODERATOR}.
     */
    protected PlayerRights rightsMod() {
        return PlayerRights.MODERATOR;
    }

    /**
     * A shortcut call to the function {@code PlayerRights.ADMINISTRATOR}.
     */
    protected PlayerRights rightsAdmin() {
        return PlayerRights.ADMINISTRATOR;
    }

    /**
     * A shortcut call to the function {@code ThreadLocalRandom.current()}.
     */
    protected ThreadLocalRandom rand() {
        return ThreadLocalRandom.current();
    }

    /**
     * A shortcut call to the function
     * 
     * <pre>
     * <code>world.schedule(new Task(instant, delay) {
     *     {@literal @}Override
     *     protected void execute() {
     *         ...
     *     }
     * }</code>
     * </pre>
     */
    protected void schedule(boolean instant, int delay, Function1<Task, Unit> action) {
        world.schedule(new Task(instant, delay) {
            @Override
            protected void execute() {
                action.apply(this);
            }
        });
    }

    /**
     * A shortcut call to the function
     * 
     * <pre>
     * <code>world.schedule(new Task(delay) {
     *     {@literal @}Override
     *     protected void execute() {
     *         ...
     *     }
     * }</code>
     * </pre>
     */
    protected void schedule(int delay, Function1<Task, Unit> action) {
        schedule(false, delay, action);
    }

    /**
     * A function containing the logic that will be executed by the underlying
     * {@link PluginPipeline} assigned to this {@code Plugin}.
     * 
     * @param evt The event that will be passed to this {@code Plugin}.
     * @param pipeline The pipeline executing this function. Used to stop
     *        traversal of subsequent {@code Plugin}s.
     */
    protected abstract void handle(E evt, PluginPipeline<E> pipeline);
}
