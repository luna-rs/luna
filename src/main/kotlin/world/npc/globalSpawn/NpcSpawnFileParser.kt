package world.npc.globalSpawn

import api.predef.*
import com.google.common.collect.ImmutableList
import com.google.gson.JsonObject
import io.luna.game.model.Position
import io.luna.game.model.def.NpcDefinition
import io.luna.util.GsonUtils
import io.luna.util.parser.JsonFileParser
import java.nio.file.Paths

/**
 * Loads the global [PersistentNpc] spawn JSON file.
 *
 * @author lare96
 */
internal class NpcSpawnFileParser : JsonFileParser<PersistentNpc>(PATH) {

    companion object {

        /**
         * The path to the file.
         */
        private val PATH = Paths.get("data", "game", "world", "npcSpawns.json")
    }

    override fun convert(token: JsonObject): PersistentNpc {
        val nameOrId = if (token.has("name")) token["name"].asString else token["id"].asInt
        val position = GsonUtils.getAsType(token["position"], Position::class.java)
        val id = if (nameOrId is Int) nameOrId else NpcDefinition.ALL.find {
            it.name.contentEquals(nameOrId as String, true)
        }?.id
        val respawn = if (token.has("respawn_ticks")) token["respawn_ticks"].asInt else 50
        val wander = if (token.has("wander_radius")) token["wander_radius"].asInt else 0
        if (id == null) {
            throw IllegalStateException("No NPC found for ID/name [$nameOrId].")
        }
        return PersistentNpc(id, position, respawn, wander)
    }

    override fun onCompleted(tokenObjects: ImmutableList<PersistentNpc>) {
        if (tokenObjects.isNotEmpty()) {
            game.sync { tokenObjects.forEach { world.npcs.add(it) } }
            logger.debug("Loaded ${tokenObjects.size} global NPC spawns!")
        }
    }
}