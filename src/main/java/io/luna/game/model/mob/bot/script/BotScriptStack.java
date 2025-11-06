package io.luna.game.model.mob.bot.script;

import api.bot.BotScript;
import io.luna.game.model.mob.bot.Bot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Manages a queue (stack) of {@link BotScript} instances that define this bot’s behavior.
 * <p>
 * This class is responsible for:
 * <ul>
 *     <li>Controlling script scheduling and priority (head vs. tail insertion).</li>
 *     <li>Allowing bots to dynamically change jobs or activities.</li>
 *     <li>Resuming scripts after interruptions or logins using {@link BotScriptSnapshot} types.</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *     <li>Scripts are queued into the stack via {@link #pushHead(BotScript)} or {@link #pushTail(BotScript)}.</li>
 *     <li>Each game tick, {@link #process()} checks the status of the currently active script (we need to do this
 *     because scripts use coroutines).</li>
 *     <li>When the active script finishes, it is removed and the next script in the queue starts automatically.</li>
 * </ul>
 *
 * <h3>Persistence</h3>
 * <ul>
 *     <li>The stack can serialize its full state to a list of {@link BotScriptSnapshot}s using {@link #save()}.</li>
 *     <li>Snapshots can be restored via {@link #load(List)} to resume exactly where the bot left off.</li>
 * </ul>
 * <p>
 * This design allows for fully dynamic, persistent scripting across logins and server restarts.
 *
 * @author lare96
 */
public final class BotScriptStack {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Once there are this many scripts queued, a warning message will be printed.
     */
    private static final int SCRIPT_WARNING_THRESHOLD = 10;

    /**
     * The script manager used to persist scripts.
     */
    private final BotScriptManager scriptManager;

    /**
     * The bot.
     */
    private final Bot bot;

    /**
     * The backing queue of active and pending scripts.
     * <p>
     * The head of the queue represents the currently executing script.
     */
    private final Deque<BotScript<?>> buffer = new ArrayDeque<>();

    /**
     * If this stack has been shutdown.
     */
    private boolean shutdown;

    /**
     * Creates a new {@link BotScriptStack}.
     *
     * @param bot The bot.
     * @param scriptManager The script manager used to persist scripts.
     */
    public BotScriptStack(Bot bot, BotScriptManager scriptManager) {
        this.bot = bot;
        this.scriptManager = scriptManager;
    }

    /**
     * Loads the given list of {@link BotScriptSnapshot}s into this stack.
     *
     * @param loadedBuffer The serialized script snapshots to restore.
     */
    public void load(List<BotScriptSnapshot<?>> loadedBuffer) {
        buffer.clear();
        BotScript<?>[] loadedScripts = new BotScript<?>[loadedBuffer.size()];
        for (BotScriptSnapshot<?> snapshot : loadedBuffer) {
            String scriptClass = snapshot.getScriptClass();
            try {
                loadedScripts[snapshot.getIndex()] = scriptManager.loadScript(scriptClass, bot, snapshot.getData());
            } catch (Exception e) {
                logger.error("Error loading persisted script [{}]", scriptClass, e);
            }
        }
        buffer.addAll(Arrays.asList(loadedScripts));
    }

    /**
     * Serializes all queued {@link BotScript}s into {@link BotScriptSnapshot}s.
     * <p>
     * Used to persist bot state for saving or logout.
     *
     * @return A list of snapshots representing all scripts in this stack.
     */
    public List<BotScriptSnapshot<?>> save() {
        int index = 0;
        List<BotScriptSnapshot<?>> snapshots = new ArrayList<>(buffer.size());
        for (BotScript<?> script : buffer) {
            Object snapshot = script.snapshot();
            snapshots.add(new BotScriptSnapshot<>(index++, script.getClass().getName(), snapshot));
        }
        return snapshots;
    }

    /**
     * Shuts down this stack by interrupting the current script and stopping processing.
     */
    public void shutdown() {
        interrupt();
        shutdown = true;
    }

    /**
     * Interrupts the currently running script.
     */
    public void interrupt() {
        BotScript<?> script = buffer.peek();
        if (script != null) {
            script.stop();
        }
    }

    /**
     * Processes the current script at the head of the stack.
     * <p>
     * If the script has not yet started, it will be started. If the script has finished,
     * it will be removed and the next script (if present) will begin on the next tick.
     * <p>
     * This should be invoked once per game tick.
     */
    public void process() {
        if (shutdown) {
            return;
        }
        BotScript<?> current = buffer.peek();
        if (current != null) {
            if (current.isIdle() || current.isInterrupted()) {
                // Script is idle or interrupted, start it and don't proceed.
                current.start();
            }
            if (current.isFinished()) {
                // Script is done, if buffer is empty proceed, otherwise wait.
                buffer.removeFirst();
            }
        }
    }

    /**
     * Forwards to {@link #pushTail(BotScript)}.
     *
     * @param script The script.
     */
    public void push(BotScript<?> script) {
        pushTail(script);
    }

    /**
     * Pushes a {@link BotScript} to the <strong>head</strong> of the stack, immediately interrupting the current
     * script.
     * <p>
     * The interrupted script is paused (not removed) and will resume once the new script completes. High-priority
     * scripts should use this method.
     *
     * @param script The script to push.
     */
    public void pushHead(BotScript<?> script) {
        BotScript<?> current = buffer.peek();
        if (current != null) {
            // Interrupt current script, but don't remove it. The stack will start it again later.
            current.stop();
            bot.log("Interrupted {" + current.getClass().getName() + "}.");
        }
        script.start();
        buffer.addFirst(script);
        bot.log("Pushed {" + script.getClass().getName() + "} to head of buffer.");
    }

    /**
     * Pushes a {@link BotScript} directly below the currently running script without interrupting it.
     * <p>
     * The new script will run immediately after the current one completes, effectively giving it higher
     * priority than all scripts in the tail.
     * <p>
     * Use this for tasks that should execute next but not override an ongoing activity.
     *
     * @param script The script to soft push to the head.
     */
    public void softPushHead(BotScript<?> script) {
        BotScript<?> current = buffer.peek();
        if (current != null) {
            buffer.removeFirst();
            buffer.addFirst(script);
            buffer.addFirst(current);
            bot.log("Pushed {" + script.getClass().getName() + "} directly after {" +
                    current.getClass().getName() + "}.");
        } else {
            script.start();
            buffer.addFirst(script);
            bot.log("Queued {" + script.getClass().getName() + "} as first script.");
        }
    }

    /**
     * Pushes a {@link BotScript} to the <strong>tail</strong> of the stack.
     * <p>
     * The script will not execute until all scripts before it have completed. Lower-priority scripts should use this
     * method.
     *
     * @param script The script to enqueue.
     */
    public void pushTail(BotScript<?> script) {
        if (buffer.size() >= SCRIPT_WARNING_THRESHOLD) {
            bot.log("Excessive amount of scripts in buffer.");
        }
        buffer.add(script);
        bot.log("Pushed {" + script.getClass().getName() + "} to tail of buffer.");
    }

    /**
     * Returns the currently active {@link BotScript}, or {@code null} if no script is running.
     *
     * @return The current script, or {@code null}.
     */
    public BotScript<?> current() {
        return buffer.peek();
    }

    /**
     * Clears all {@link BotScript}s from the buffer except the currently running one.
     */
    public void clear() {
        BotScript<?> current = buffer.peek();
        buffer.clear();
        if (current != null && current.isRunning()) {
            buffer.add(current);
        }
    }
}
