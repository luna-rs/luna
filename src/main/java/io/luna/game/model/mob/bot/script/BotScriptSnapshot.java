package io.luna.game.model.mob.bot.script;

import api.bot.script.BotScript;
import com.google.gson.JsonElement;

/**
 * A serialized snapshot of a single {@link BotScript} instance.
 * <p>
 * Each snapshot stores enough information for {@link BotScriptManager} to rebuild one script inside a
 * {@link BotScriptStack}. This includes the script's stack index, script class name, script data class name, and
 * serialized state payload.
 * <p>
 * Snapshots are created when bots are saved and consumed again when bots are loaded.
 *
 * @author lare96
 */
public final class BotScriptSnapshot {

    /**
     * The script's position in the stack when the snapshot was created.
     * <p>
     * This is used to restore scripts in the same order they were saved.
     */
    private final int index;

    /**
     * The fully qualified class name of the saved script.
     * <p>
     * This is used by {@link BotScriptManager} to find and recreate the correct {@link BotScript} implementation.
     */
    private final String scriptClass;

    /**
     * The fully qualified class name of the saved script data.
     * <p>
     * This is used by {@link BotScriptManager} to find the correct data type before restoring {@link #data}.
     */
    private final String scriptDataClass;

    /**
     * The serialized script state.
     * <p>
     * This payload is produced by {@link BotScript#snapshot()} and contains the script-specific fields needed to resume
     * execution later.
     */
    private final JsonElement data;

    /**
     * Creates a new {@link BotScriptSnapshot}.
     *
     * @param index The script's position in the stack.
     * @param scriptClass The fully qualified class name of the script.
     * @param scriptDataClass The fully qualified class name of the script data.
     * @param data The serialized state payload for the script.
     */
    public BotScriptSnapshot(int index, String scriptClass, String scriptDataClass, JsonElement data) {
        this.index = index;
        this.scriptClass = scriptClass;
        this.scriptDataClass = scriptDataClass;
        this.data = data;
    }

    /**
     * @return The script's position in the saved stack.
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return The fully qualified class name of the saved script.
     */
    public String getScriptClass() {
        return scriptClass;
    }

    /**
     * @return The fully qualified class name of the saved script data.
     */
    public String getScriptDataClass() {
        return scriptDataClass;
    }

    /**
     * @return The serialized state payload for the script.
     */
    public JsonElement getData() {
        return data;
    }
}