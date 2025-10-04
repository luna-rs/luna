package io.luna.game.model.mob.bot.script;

import api.bot.BotScript;

/**
 * A serializable snapshot of a single {@link BotScript} instance.
 * <p>
 * Each snapshot represents the saved state of one script in a {@link BotScriptStack}, including its execution
 * order index, class identifier, and script-specific data payload.
 * <p>
 * Snapshots are created when bots are persisted and later used to reconstruct their
 * script stack using {@link BotScriptManager}.
 *
 * <h3>Usage</h3>
 * <ul>
 *     <li>During save, each script produces its own {@link #getData()} via {@code snapshot()}.</li>
 *     <li>During load, the {@link #getScriptClass()} name is used by the script manager
 *     to reinstantiate the correct script type and restore its state.</li>
 * </ul>
 *
 * @param <T> The type of data representing this script’s serialized state.
 * @author lare96
 */
public final class BotScriptSnapshot<T> {

    /**
     * The index of this script within the stack at the time of saving.
     * <p>
     * Used to preserve the exact ordering of scripts during restoration.
     */
    private final int index;

    /**
     * The fully qualified class name of the serialized script.
     * <p>
     * Used by {@link BotScriptManager} to locate and reconstruct the correct script type.
     */
    private final String scriptClass;

    /**
     * The snapshot data representing the internal state of the script.
     * <p>
     * This is the object returned by {@link BotScript#snapshot()} and may contain any serializable fields
     * necessary to resume execution later.
     */
    private final T data;

    /**
     * Creates a new {@link BotScriptSnapshot}.
     *
     * @param index The script’s position in the stack.
     * @param scriptClass The fully qualified name of the script class.
     * @param data The serialized state data for the script.
     */
    public BotScriptSnapshot(int index, String scriptClass, T data) {
        this.index = index;
        this.scriptClass = scriptClass;
        this.data = data;
    }

    /**
     * @return The script’s index within the stack.
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return The fully qualified name of the serialized script class.
     */
    public String getScriptClass() {
        return scriptClass;
    }

    /**
     * @return The serialized state data for the script.
     */
    public T getData() {
        return data;
    }
}
