package api.bot.webwalk.generate

import api.predef.*
import com.google.common.collect.ImmutableList
import com.google.gson.JsonObject
import io.luna.game.model.Position
import io.luna.game.model.def.NpcDefinition
import io.luna.util.GsonUtils
import io.luna.util.parser.JsonFileParser
import java.nio.file.Path

/**
 * Finds the NPCs that the web is built around, such as the crew of a ship, for [WebWalkLiveGenerator.gather].
 *
 * The web is gathered before the global NPC spawns are in the world: they are read during the launch, but only added on
 * the game thread's next tick, which comes after the server waits for the web. So the NPCs of the world are looked at
 * first, for those that plugins spawn themselves, and then the spawns in the file that will be added.
 *
 * @param spawns Where each NPC of the spawn file stands, as an NPC id and its position.
 * @author TheLining
 */
class NpcSpawns(private val spawns: List<Pair<Int, Position>>) {

    companion object {

        /**
         * The file of the global NPC spawns.
         */
        private val PATH = Path.of("data", "game", "world", "npc_spawns.jsonc")

        /**
         * Reads the spawns in the file. Entries that can't be read are logged and left out, as they are when the server
         * spawns them.
         */
        fun load(): NpcSpawns {
            var spawns: List<Pair<Int, Position>> = emptyList()
            object : JsonFileParser<Pair<Int, Position>>(PATH) {
                override fun convert(token: JsonObject): Pair<Int, Position> {
                    val id = if (token.has("name")) {
                        val name = token["name"].asString
                        NpcDefinition.ALL.find { it.name.contentEquals(name, true) }?.id()
                            ?: throw IllegalStateException("No NPC found for name [$name].")
                    } else {
                        token["id"].asInt
                    }
                    return id to GsonUtils.getAsType(token["position"], Position::class.java)
                }

                override fun onCompleted(tokenObjects: ImmutableList<Pair<Int, Position>>) {
                    spawns = tokenObjects
                }
            }.run()
            return NpcSpawns(spawns)
        }
    }

    /**
     * Finds where the nearest NPC with one of [ids] stands, within [radius] tiles of [center] on its plane. NPCs in the
     * world come before those of the spawn file.
     *
     * @return Its position, or `null` if there is none.
     */
    fun near(center: Position, radius: Int, ids: Set<Int>): Position? {
        val live = world.locator.findNpcs(center, radius) { it.id in ids }
            .minByOrNull { it.position.computeLongestDistance(center) }
        if (live != null) {
            return live.position
        }
        return spawns.filter { (id, position) ->
            id in ids && position.z == center.z && position.computeLongestDistance(center) <= radius
        }.minByOrNull { it.second.computeLongestDistance(center) }?.second
    }
}
