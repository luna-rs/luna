package api.bot.webwalk.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import io.luna.game.model.Position

/**
 * Thrown when the web-walker data is invalid. Every problem found is reported together, so that a file can be fixed in one
 * go.
 *
 * @property problems One message for each problem found.
 *
 * @author Hydrozoa
 */
class WebWalkDataException(val problems: List<String>) :
    RuntimeException("Invalid web-walker data (${problems.size} problems):\n" + problems.joinToString("\n") { "  - $it" })

/**
 * Collects the problems found while loading.
 */
internal class DataErrors {

    /**
     * The problems, in the order they were found.
     */
    val messages = ArrayList<String>()

    /**
     * Records a problem.
     *
     * @param where Where the problem is, such as `walk_graph.jsonc[3].pos`.
     * @param message What is wrong.
     */
    fun add(where: String, message: String) {
        messages += "$where: $message"
    }
}

/**
 * Reads the fields of one JSON object, recording a problem for every field that is missing, of the wrong type or out of
 * range. A field that is never read is an unknown field, which [finish] reports so that typos do not go unnoticed.
 *
 * @param json The object to read.
 * @param where Where the object is, for messages.
 * @param errors Where problems are recorded.
 */
internal class FieldReader(private val json: JsonObject, val where: String, private val errors: DataErrors) {

    companion object {

        /**
         * Parses [text], recording a problem and returning `null` if it is not valid JSON. Blank text is not a problem and
         * also returns `null`.
         */
        fun parse(file: String, text: String?, errors: DataErrors): JsonElement? {
            if (text == null || text.isBlank()) {
                return null
            }
            return try {
                JsonParser.parseString(text)
            } catch (e: JsonParseException) {
                errors.add(file, "invalid JSON: ${e.message}")
                null
            }
        }

        /**
         * Reads the elements of an array of objects. An element that is not an object is a problem.
         */
        fun objectsOf(array: JsonArray, where: String, errors: DataErrors): List<FieldReader> {
            val readers = ArrayList<FieldReader>(array.size())
            for ((index, element) in array.withIndex()) {
                if (element.isJsonObject) {
                    readers += FieldReader(element.asJsonObject, "$where[$index]", errors)
                } else {
                    errors.add("$where[$index]", "must be an object")
                }
            }
            return readers
        }
    }

    /**
     * The fields that have been read.
     */
    private val read = HashSet<String>()

    /**
     * The names of the fields of the object.
     */
    fun keys(): List<String> = json.keySet().toList()

    /**
     * @return `true` if the object has the field, with a value other than `null`.
     */
    fun has(key: String): Boolean = json.has(key) && !json.get(key).isJsonNull

    /**
     * Records a problem with a field, and counts the field as read.
     */
    fun reject(key: String, message: String) {
        read += key
        errors.add("$where.$key", message)
    }

    /**
     * Reports every field that was never read.
     */
    fun finish() {
        for (key in json.keySet()) {
            if (key !in read) {
                errors.add("$where.$key", "unknown field")
            }
        }
    }

    /**
     * Looks up a field, or returns `null` if it is missing (which is a problem if it is [required]).
     */
    private fun element(key: String, required: Boolean): JsonElement? {
        read += key
        val element = json.get(key)
        if (element == null || element.isJsonNull) {
            if (required) {
                errors.add("$where.$key", "is required")
            }
            return null
        }
        return element
    }

    /**
     * Reads a string field.
     */
    fun string(key: String, required: Boolean = true): String? {
        val element = element(key, required) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            errors.add("$where.$key", "must be a string")
            return null
        }
        return element.asString
    }

    /**
     * Reads a whole number field that is within [min] and [max].
     */
    fun int(key: String, required: Boolean = true, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): Int? {
        val element = element(key, required) ?: return null
        val value = toInt(element)
        if (value == null) {
            errors.add("$where.$key", "must be a whole number")
            return null
        }
        if (value < min || value > max) {
            errors.add("$where.$key", "must be between $min and $max, but was $value")
            return null
        }
        return value
    }

    /**
     * Reads a true or false field.
     */
    fun bool(key: String): Boolean? {
        val element = element(key, false) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) {
            errors.add("$where.$key", "must be true or false")
            return null
        }
        return element.asBoolean
    }

    /**
     * Reads a position, written as `[x, y]` or `[x, y, z]`.
     */
    fun position(key: String, required: Boolean = true): Position? {
        val element = element(key, required) ?: return null
        if (!element.isJsonArray || element.asJsonArray.size() !in 2..3) {
            errors.add("$where.$key", "must be an array of [x, y] or [x, y, z]")
            return null
        }
        val values = element.asJsonArray.map { toInt(it) }
        if (values.any { it == null }) {
            errors.add("$where.$key", "must contain only whole numbers")
            return null
        }
        val x = values[0]!!
        val y = values[1]!!
        val z = values.getOrNull(2) ?: 0
        if (x < 0 || y < 0 || z !in 0..3) {
            errors.add("$where.$key", "must have x and y of at least 0, and z from 0 to 3, but was $values")
            return null
        }
        return Position(x, y, z)
    }

    /**
     * Reads an object field, or returns `null` if it is missing.
     */
    fun obj(key: String, required: Boolean = false): FieldReader? {
        val element = element(key, required) ?: return null
        if (!element.isJsonObject) {
            errors.add("$where.$key", "must be an object")
            return null
        }
        return FieldReader(element.asJsonObject, "$where.$key", errors)
    }

    /**
     * Reads an array of objects, which is empty if the field is missing.
     */
    fun objects(key: String, required: Boolean = false): List<FieldReader> {
        val element = element(key, required) ?: return emptyList()
        if (!element.isJsonArray) {
            errors.add("$where.$key", "must be an array")
            return emptyList()
        }
        return objectsOf(element.asJsonArray, "$where.$key", errors)
    }

    /**
     * Reads an array of strings, which is empty if the field is missing.
     */
    fun strings(key: String): List<String> {
        val element = element(key, false) ?: return emptyList()
        if (!element.isJsonArray) {
            errors.add("$where.$key", "must be an array")
            return emptyList()
        }
        val strings = ArrayList<String>()
        for ((index, item) in element.asJsonArray.withIndex()) {
            if (item.isJsonPrimitive && item.asJsonPrimitive.isString) {
                strings += item.asString
            } else {
                errors.add("$where.$key[$index]", "must be a string")
            }
        }
        return strings
    }

    /**
     * Converts a JSON number to an int, or returns `null` if it is not a whole number that fits.
     */
    private fun toInt(element: JsonElement): Int? {
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
            return null
        }
        return try {
            element.asBigDecimal.intValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }
}
