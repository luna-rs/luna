/**
 * All globally accessible attributes go in this file. Niche attributes can be put in the files they're related to.
 */
package api.predef

import api.attr.Attr
import com.google.gson.stream.JsonReader
import io.luna.game.model.mob.Player
import java.lang.IllegalArgumentException

/**
 * The current trading partner.
 */
var Player.tradingWith by Attr.int(-1)

/**
 * If it's a player's first login.
 */
var Player.firstLogin by Attr.boolean(true).persist("first_login")

/**
 * The player's current wilderness level. Will be `0` if not in the wilderness.
 */
var Player.wildernessLevel by Attr.int()

/**
 * Validates and reads a json member. Intended to be used within [TypeAdapter]s.
 */
fun <E> readJsonMember(reader: JsonReader, expected: String, valueProducer: (JsonReader) -> E): E {
    return if (reader.nextName().equals(expected)) valueProducer(reader) else
        throw IllegalArgumentException("Expected [$expected]")
}