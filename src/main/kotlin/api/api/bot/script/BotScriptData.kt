package api.bot.script

import com.google.gson.JsonObject
import io.luna.util.GsonUtils

/**
 * Base type for serializable bot script state.
 *
 * Script data objects are used when saving and restoring bot scripts. Each script-specific data class is responsible
 * for reading its fields from JSON in [load] and writing its fields back to JSON in [save].
 *
 * @author lare96
 */
abstract class BotScriptData {

    /**
     * Loads this script data from a JSON object.
     *
     * @param data The JSON object containing saved script state.
     */
    abstract fun load(data: JsonObject)

    /**
     * Saves this script data to a JSON object.
     *
     * @param data The JSON object to write script state into.
     */
    abstract fun save(data: JsonObject)

    /**
     * Loads a saved enum set from a JSON string array.
     *
     * The JSON field is expected to contain enum names, which are converted back into enum values through [mapFunc].
     * This allows callers to support normal enum lookup or custom migration logic for renamed enum entries.
     *
     * @param T The enum type being loaded.
     * @param name The JSON field name containing the saved enum names.
     * @param data The JSON object to read from.
     * @param mapFunc Converts each saved enum name into its enum value.
     *
     * @return The loaded enum values as a set.
     */
    fun <T : Enum<T>> loadEnumSet(name: String, data: JsonObject, mapFunc: (String) -> T): Set<T> {
        return GsonUtils.getAsType(data.get(name), Array<String>::class.java).map { mapFunc(it) }.toSet()
    }

    /**
     * Saves an enum set as a JSON string array.
     *
     * Each enum value is saved by [Enum.name], making the result stable and readable in script save data.
     *
     * @param T The enum type being saved.
     * @param name The JSON field name to write.
     * @param data The JSON object to write into.
     * @param types The enum values to save.
     */
    fun <T : Enum<T>> saveEnumSet(name: String, data: JsonObject, types: Set<T>) {
        data.add(name, GsonUtils.toJsonTree(types.map { it.name }))
    }
}