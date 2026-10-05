package game.obj.doors

import api.predef.*
import com.google.common.collect.ImmutableList
import com.google.gson.JsonObject
import io.luna.util.GsonUtils
import io.luna.util.parser.JsonFileParser
import java.nio.file.Path

/**
 * Loads a door JSON file of a single [Doors.Kind].
 *
 * Doors are added on the game thread, so the listeners registered by [onLoaded] are never touched concurrently.
 *
 * @param path The path to the file.
 * @param kind The kind of door the file contains.
 * @param onLoaded Called on the game thread with the doors once they have been added to [Doors].
 *
 * @author Hydrozoa
 */
internal class DoorFileParser(path: Path,
                              private val kind: Doors.Kind,
                              private val onLoaded: (List<DoorType>) -> Unit) : JsonFileParser<DoorType>(path) {

    override fun convert(token: JsonObject): DoorType = GsonUtils.getAsType(token, DoorType::class.java)

    override fun onCompleted(tokenObjects: ImmutableList<DoorType>) {
        logger.debug("Loaded ${tokenObjects.size} ${kind.name.lowercase()} doors!")
        gameService.sync {
            Doors.add(kind, tokenObjects)
            onLoaded(tokenObjects)
        }
    }
}
