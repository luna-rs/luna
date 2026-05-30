package io.luna.game.model.mob.bot.script;

import api.bot.script.BotScript;
import api.bot.script.BotScriptData;
import api.bot.script.VoidBotScript;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.luna.game.model.mob.bot.Bot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the script buffer that controls a bot's queued behavior.
 * <p>
 * The buffer is backed by a {@link Deque}. The head of the buffer represents the active or next-to-run script, while
 * the tail contains lower-priority scripts that should run later.
 * <p>
 * This class is responsible for:
 * <ul>
 *     <li>Starting, pausing, and removing scripts as they progress through their lifecycle.</li>
 *     <li>Supporting priority insertion through {@link #pushHead(BotScript)} and {@link #softPushHead(BotScript)}.</li>
 *     <li>Supporting normal queued insertion through {@link #pushTail(BotScript)}.</li>
 *     <li>Saving and restoring queued scripts through {@link BotScriptSnapshot}s.</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *     <li>Scripts are inserted into the buffer with {@link #pushHead(BotScript)}, {@link #softPushHead(BotScript)},
 *     {@link #pushTail(BotScript)}, or {@link #push(BotScript)}.</li>
 *     <li>Each game tick, {@link #process()} checks the script at the head of the buffer.</li>
 *     <li>If the current script is idle or paused, it is started.</li>
 *     <li>If the current script is finished, it is removed and the next script becomes eligible to run.</li>
 * </ul>
 *
 * <h3>Persistence</h3>
 * <ul>
 *     <li>{@link #save()} serializes the buffer into ordered {@link BotScriptSnapshot}s.</li>
 *     <li>{@link #load(List)} restores the buffer from previously saved snapshots.</li>
 *     <li>Scripts without snapshot data are temporarily restored as {@link VoidBotScript}s and then removed to preserve
 *     ordering while loading.</li>
 * </ul>
 * <p>
 * This design allows bots to maintain script state across logouts, server restarts, and behavior interruptions.
 *
 * @author lare96
 */
public final class BotScriptStack {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The number of queued scripts required before this stack starts warning about excessive buffer growth.
     */
    private static final int SCRIPT_WARNING_THRESHOLD = 10;

    /**
     * The manager used to restore persisted bot scripts from saved snapshot data.
     */
    private final BotScriptManager scriptManager;

    /**
     * The bot that owns this script stack.
     */
    private final Bot bot;

    /**
     * The backing script buffer.
     * <p>
     * The first element is the currently active or next-to-run script. Scripts after the first element are pending
     * scripts that will run later.
     */
    private final Deque<BotScript> buffer = new ArrayDeque<>();

    /**
     * Whether this script stack has been shut down.
     * <p>
     * Once shutdown, {@link #process()} will stop processing scripts and report completion.
     */
    private final AtomicBoolean shutdown = new AtomicBoolean();

    /**
     * Creates a new script stack for a bot.
     *
     * @param bot The bot that owns this script stack.
     * @param scriptManager The manager used to restore persisted scripts.
     */
    public BotScriptStack(Bot bot, BotScriptManager scriptManager) {
        this.bot = bot;
        this.scriptManager = scriptManager;
    }

    /**
     * Restores this script buffer from saved script snapshots.
     * <p>
     * Snapshots are restored by their saved index so the original buffer order is preserved. Snapshots with a script
     * class of {@code "null"} represent dynamic or non-persistent scripts. These are temporarily restored as
     * {@link VoidBotScript}s to preserve ordering during load, then removed after all snapshots are processed.
     *
     * @param loadedBuffer The saved script snapshots to restore.
     */
    public void load(List<BotScriptSnapshot> loadedBuffer) {
        buffer.clear();
        BotScript[] loadedScripts = new BotScript[loadedBuffer.size()];
        for (BotScriptSnapshot snapshot : loadedBuffer) {
            String scriptClass = snapshot.getScriptClass();
            JsonElement scriptData = snapshot.getData();
            if (scriptData == null || scriptData.isJsonNull()) {
                // Dynamic script was here, temporarily hold a void script in its place.
                loadedScripts[snapshot.getIndex()] = new VoidBotScript(bot);
                continue;
            }
            try {
                BotScriptData dataClass =
                        (BotScriptData) Class.forName(snapshot.getScriptDataClass()).getConstructor().newInstance();
                dataClass.load(scriptData.getAsJsonObject());
                loadedScripts[snapshot.getIndex()] = scriptManager.loadScript(scriptClass, bot, dataClass);
            } catch (Exception e) {
                logger.error("Error loading persisted script [{}]", scriptClass, e);
            }
        }
        buffer.addAll(Arrays.asList(loadedScripts));
        buffer.removeIf(script -> script instanceof VoidBotScript); // Remove all void scripts after to maintain order.
    }

    /**
     * Saves every script currently in the buffer.
     * <p>
     * Each script is serialized into a {@link BotScriptSnapshot} with its current buffer index. If a script does not
     * provide snapshot data, a placeholder snapshot is written so the relative order of persistent scripts can still be
     * maintained during load.
     *
     * @return An ordered list of snapshots representing this script buffer.
     */
    public List<BotScriptSnapshot> save() {
        int index = 0;
        List<BotScriptSnapshot> snapshots = new ArrayList<>(buffer.size());
        for (BotScript script : buffer) {
            BotScriptData snapshot = script.snapshot();
            if (snapshot == null) {
                snapshots.add(new BotScriptSnapshot(index++, "null", "null", JsonNull.INSTANCE));
                continue;
            }
            JsonObject data = new JsonObject();
            snapshot.save(data);
            snapshots.add(new BotScriptSnapshot(index++, script.getClass().getName(),
                    snapshot.getClass().getName(), data));
        }
        return snapshots;
    }

    /**
     * Shuts this stack down.
     * <p>
     * The current script is paused once, and future calls to {@link #process()} will return {@code true} without
     * starting or advancing any scripts.
     */
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            pause();
        }
    }

    /**
     * Pauses the current script at the head of the buffer, if one exists.
     * <p>
     * The script remains in the buffer and may be restarted later by {@link #process()}.
     */
    public void pause() {
        BotScript script = buffer.peek();
        if (script != null) {
            script.pause();
        }
    }

    /**
     * Processes the script at the head of the buffer.
     * <p>
     * If the stack has been shut down, this method immediately returns {@code true}. Otherwise, the current script is
     * started if it is idle or paused. Finished scripts are removed from the head of the buffer so the next queued script
     * can run on a later tick.
     * <p>
     * This should be called once per game tick.
     *
     * @return {@code true} if the stack is shut down or empty, otherwise {@code false}.
     */
    public boolean process() {
        if (shutdown.get()) {
            return true;
        }
        BotScript current = buffer.peek();
        if (current != null) {
            if (current.isIdle() || current.isPaused()) {
                // Script is idle or paused, start it.
                current.start();
            }
            if (current.isFinished()) {
                // Script is done, remove it from memory.
                buffer.removeFirst();
            }
            return false;
        }
        return true;
    }

    /**
     * Pushes a script to the tail of the buffer.
     * <p>
     * This is a convenience alias for {@link #pushTail(BotScript)}.
     *
     * @param script The script to enqueue.
     */
    public void push(BotScript script) {
        pushTail(script);
    }

    /**
     * Pushes a new script to the head of the buffer and pauses the current script.
     * <p>
     * Use this for urgent scripts that should override the bot's current activity, such as emergency banking, fleeing,
     * or anti-PK behavior.
     *
     * @param script The high-priority script to run immediately.
     */
    public void pushHead(BotScript script) {
        BotScript current = buffer.peek();
        if (current != null) {
            // Pause current script, but don't remove it. The stack will start it again later.
            current.pause();
            bot.log("Paused {" + current.getClass().getName() + "}.");
        }
        buffer.addFirst(script);
        bot.log("Pushed {" + script.getClass().getName() + "} to head of buffer.");
    }

    /**
     * Pushes a script directly behind the current script.
     * <p>
     * Unlike {@link #pushHead(BotScript)}, this does not pause the current script. The inserted script becomes the
     * next script to run after the current script finishes, giving it priority over scripts already waiting in the tail.
     * <p>
     * If the buffer is empty, the script is started immediately and inserted as the first script.
     * <p>
     * Use this for follow-up work that should happen soon but should not break the currently running activity.
     *
     * @param script The script to run after the current script.
     */
    public void softPushHead(BotScript script) {
        String scriptName = script.getClass().getName();
        BotScript current = buffer.peek();
        if (current != null) {
            buffer.removeFirst();
            buffer.addFirst(script);
            buffer.addFirst(current);
            bot.log("Pushed {" + scriptName + "} directly after {" + current.getClass().getName() + "}.");
        } else {
            script.start();
            buffer.addFirst(script);
            bot.log("Pushed {" + scriptName + "} to head of buffer.");
        }
    }

    /**
     * Pushes a script to the tail of the buffer.
     * <p>
     * Tail scripts run only after all scripts before them have completed. This is the normal insertion method for
     * low-priority or long-term bot behavior.
     * <p>
     * A warning is logged if the buffer grows beyond {@link #SCRIPT_WARNING_THRESHOLD}, which may indicate scripts are
     * being added faster than they complete.
     *
     * @param script The script to enqueue at the tail.
     */
    public void pushTail(BotScript script) {
        String scriptName = script.getClass().getName();
        if (buffer.size() >= SCRIPT_WARNING_THRESHOLD) {
            logger.warn("Excessive amount of scripts in buffer bot={}, script={}.", bot, scriptName);
            bot.log("Excessive amount of scripts in buffer.");
        }
        buffer.add(script);
        bot.log("Pushed {" + scriptName + "} to tail of buffer.");
    }

    /**
     * Returns the current script at the head of the buffer.
     *
     * @return The current script, or {@code null} if the buffer is empty.
     */
    public BotScript current() {
        return buffer.peek();
    }

    /**
     * Clears all queued scripts except the currently running script.
     * <p>
     * If the current script is running, it is preserved at the head of the buffer. Idle, paused, finished, and pending
     * scripts are removed.
     */
    public void clear() {
        BotScript current = buffer.peek();
        buffer.clear();
        if (current != null && current.isRunning()) {
            buffer.add(current);
        }
    }

    /**
     * Returns the number of scripts currently in the buffer.
     * <p>
     * This includes the active script at the head of the buffer and all pending scripts behind it.
     *
     * @return The number of scripts in the buffer.
     */
    public int size() {
        return buffer.size();
    }
}