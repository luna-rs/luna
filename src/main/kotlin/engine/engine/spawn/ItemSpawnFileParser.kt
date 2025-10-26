package engine.spawn

import api.predef.*
import api.predef.ext.*
import com.google.common.collect.ImmutableList
import com.google.gson.JsonObject
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.util.GsonUtils
import io.luna.util.parser.JsonFileParser
import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Loads the global item spawn JSON file.
 *
 * @author lare96
 */
internal class ItemSpawnFileParser : JsonFileParser<PersistentGroundItem>(PATH) {

    /**
     * Additional non-stackable items to be added.
     */
    private val items = ConcurrentLinkedQueue<PersistentGroundItem>()

    companion object {

        /**
         * The path to the file.
         */
        private val PATH = Paths.get("data", "game", "world", "itemSpawns.json")
    }

    override fun convert(token: JsonObject): PersistentGroundItem {
        val nameOrId = if (token.has("name")) token["name"].asString else token["id"].asInt
        var amount = token["amount"].asInt
        val position = GsonUtils.getAsType(token["position"], Position::class.java)
        val id = if (nameOrId is Int) nameOrId else Item.byName(nameOrId as String).id
        val respawn = if (token.has("respawn_ticks")) token["respawn_ticks"].asInt else
            PersistentGroundItem.DEFAULT_RESPAWN_TICKS
        val def = itemDef(id)
        if (!def.isStackable && amount > 1) {
            // Handle non-stackable items with an amount > 1.
            amount--
            repeat(amount) {
                items += PersistentGroundItem(id, 1, position, respawn)
            }
            amount = 1
        }
        return PersistentGroundItem(id, amount, position, respawn)
    }

    override fun onCompleted(tokenObjects: ImmutableList<PersistentGroundItem>) {
        logger.debug("Loaded ${tokenObjects.size} global item spawns!")
        gameThread.sync {
            world.scheduleOnce(1) {
                tokenObjects.forEach { world.addItem(it) }
                while(true) { world.addItem(items.poll() ?: break) }
            }
        }
    }

    /**
     * Manually adds a persistent item to be spawned.
     */
    internal fun add(item: PersistentGroundItem) {
        items.add(item)
    }
}