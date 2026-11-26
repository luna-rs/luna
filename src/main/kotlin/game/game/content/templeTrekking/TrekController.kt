package game.content.templeTrekking

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.content.templeTrekking.TempleTrekking.trek
import game.player.item.consume.food.Food
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.chunk.Chunk
import io.luna.game.model.item.Item
import io.luna.game.model.map.DynamicMap
import io.luna.game.model.map.DynamicMapController
import io.luna.game.model.map.builder.DynamicMapBuilder
import io.luna.game.model.map.builder.DynamicMapPalette
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import java.util.function.Function

/**
 * Starts, moves on and ends the events of a trek.
 *
 * @author TheLining
 */
object TrekEvents {

    /**
     * The foods a villager takes for the trek.
     */
    val FOODS = listOf(3369, 3371, 3373, 329, 2003, 3381, 2142, 361, 7530, 379, 365, 2325, 2323, 7178, 7188, 1891,
                       1897, 5988, 6703)

    /**
     * How much food a villager's backpack holds.
     */
    const val BACKPACK = 15

    /**
     * The food in the villagers' shared backpack, as item ids.
     */
    var Player.trekFood by Attr.string().persist("temple_trek_food")

    /**
     * The food in the villagers' backpack.
     */
    fun food(plr: Player): MutableList<Int> =
        plr.trekFood.split(',').mapNotNull { it.toIntOrNull() }.toMutableList()

    fun setFood(plr: Player, food: List<Int>) {
        plr.trekFood = food.joinToString(",")
    }

    /**
     * Moves [plr] and their villager into a copy of the room for an event of [type].
     */
    fun enter(plr: Player, trek: Trek, type: TrekEventType) {
        trek.events++
        val room = when (type) {
            TrekEventType.SHADES -> TrekRoom.MORTTON
            TrekEventType.TENTACLES -> TrekRoom.BOAT
            TrekEventType.BOG -> TrekRoom.BOG
            TrekEventType.RIVER -> TrekRoom.RIVER
            TrekEventType.BRIDGE -> TrekRoom.BRIDGE
            TrekEventType.ABIDOR_CRANK -> TrekRoom.SWAMP.random()
            // Fights on the hard route are in the rooms with no way round.
            else -> TrekRoom.SWAMP.filter { it.evadable == (trek.route.evade != EvadeRule.NEVER) }.random()
        }
        val palette = DynamicMapPalette()
        // The bridge deck is on plane 1 and the boat on plane 2.
        for (plane in 0..2) {
            palette.setRegion(4, plane, room.region, plane)
        }
        val map = DynamicMapBuilder(ctx)
            .setPalette(palette.getChunk(6, 6, 0).chunk, palette)
            .setController(Function<Player, DynamicMapController> { TrekController(it, trek, type, room) })
            .build()
        map.create()
        trek.moving = true
        try {
            map.add(plr, map.getInstancePosition(room.start))
        } finally {
            trek.moving = false
        }
    }

    /**
     * Brings a trek that reached Paterdomus out of its last event.
     */
    fun arrive(plr: Player, trek: Trek) {
        val controller = plr.controllers.primary as? TrekController
        if (controller != null && controller.trek === trek) {
            controller.arrived()
        } else {
            plr.move(TempleTrekking.ARRIVAL)
        }
        TempleTrekking.arrive(plr, trek)
    }

    /**
     * The trek controller [plr] is in, if it's for [trek]'s current event.
     */
    fun controller(plr: Player): TrekController? = plr.controllers.primary as? TrekController
}

/**
 * Looks after a player in one event of a trek, until they continue, evade, escape or fail.
 *
 * @author TheLining
 */
class TrekController(plr: Player, val trek: Trek, val type: TrekEventType, val room: TrekRoom) :
    DynamicMapController(plr) {

    private enum class Ending { ARRIVED, ESCAPED }

    /**
     * The villager in the room.
     */
    var follower: TrekFollower? = null
        private set

    /**
     * What the event is about.
     */
    lateinit var encounter: TrekEncounter
        private set

    /**
     * Set once the player has moved on and the trek is back on the map.
     */
    var travelling = false
        private set

    /**
     * The leg the event ended, so an escape leaves the player near it.
     */
    private val leg = trek.leg - 1

    private var ending: Ending? = null

    /**
     * The copy's chunks, where its NPCs are looked for.
     */
    private var chunks: List<Chunk> = emptyList()

    /**
     * Turns a tile of the real room into its tile in this copy.
     */
    fun tile(real: Position): Position = player.dynamicMap.getInstancePosition(real)

    override fun enter(player: Player) {
        val map = player.dynamicMap
        chunks = DynamicMapPalette.getAllChunksInRegion(room.region.id)
            .map { map.getInstancePosition(it.absPosition).chunk }
        val villager = TrekFollower(player, trek, tile(room.villager))
        if (trek.villagerHealth > 0) villager.health = trek.villagerHealth
        world.npcs.add(villager)
        villager.navigator.follow(player)
        follower = villager
        encounter = TrekEncounter.create(type, this)
        encounter.start()
    }

    override fun process() {
        val map = player.dynamicMap
        if (ending != null || travelling || map == null || !map.contains(player.position)) return
        val villager = follower ?: return
        if (!villager.isAlive || villager.state != EntityState.ACTIVE) {
            fail()
            return
        }
        feed(villager)
        if (villager.combat.target == null && !villager.isLocked && villager.navigator.currentTarget !== player &&
            !encounter.holdsVillager) {
            villager.navigator.follow(player)
        }
        encounter.process()
    }

    /**
     * The villager eats from its backpack once it is ten or more hitpoints down.
     */
    private fun feed(villager: TrekFollower) {
        if (villager.totalHealth - villager.health < 10) return
        val food = TrekEvents.food(player)
        if (food.isEmpty()) return
        val id = food.removeAt(0)
        TrekEvents.setFood(player, food)
        val heal = Food.ID_TO_FOOD[id]?.heal ?: 10
        villager.health = (villager.health + heal).coerceAtMost(villager.totalHealth)
        villager.speak("Urgh, just had to eat some food, I only have ${food.size} left.")
    }

    /**
     * Every NPC in the copy.
     */
    fun npcs(): List<Npc> = chunks.flatMap { world.chunks.load(it).getAll<Npc>(EntityType.NPC) }

    /**
     * Moves on along the trek once the event is done.
     */
    fun continueTrek() {
        if (travelling || ending != null) return
        if (!encounter.complete) {
            player.sendMessage(encounter.unfinished)
            return
        }
        encounter.completed()
        moveOn()
    }

    /**
     * Leaves a fight before it's over, if the route allows it.
     */
    fun evade() {
        if (travelling || ending != null) return
        if (!room.evadable || !type.fight) {
            player.sendMessage("You can't evade this event.")
            return
        }
        when (trek.route.evade) {
            EvadeRule.NEVER -> {
                player.sendMessage("You can't evade this event.")
                return
            }

            EvadeRule.HALF -> if (!encounter.halfDefeated) {
                player.sendMessage("You need to defeat at least half of the enemies before you can evade them.")
                return
            }

            EvadeRule.ANY -> {}
        }
        trek.evaded(leg)
        moveOn()
    }

    private fun moveOn() {
        travelling = true
        val villager = follower
        if (villager != null) {
            trek.villagerHealth = villager.health
            villager.combat.target = null
        }
        player.combat.target = null
        encounter.clear()
        TrekJourney.start(player, trek)
    }

    /**
     * Abandons the trek by the escape path.
     */
    fun escape() {
        if (travelling || ending != null) return
        ending = Ending.ESCAPED
        player.trek = null
        player.dynamicMap.remove(player)
        player.sendMessage("You abandon the trek and run for it.")
    }

    /**
     * Ends the trek because the villager was killed.
     */
    private fun fail() {
        ending = Ending.ESCAPED
        player.trek = null
        player.combat.target = null
        player.dynamicMap.remove(player)
        player.sendMessage("Your companion has been killed. You've failed to get them to Paterdomus.")
    }

    /**
     * Takes the player out to Paterdomus at the end of the trek.
     */
    fun arrived() {
        ending = Ending.ARRIVED
        player.dynamicMap.remove(player)
    }

    override fun exit(player: Player): Position {
        val map: DynamicMap = player.dynamicMap
        // The map still holds the player until this returns, so it can't be deleted yet.
        world.scheduleOnce(1) { map.delete() }
        TrekPuzzles.clearItems(player)
        if (ending == null && !trek.moving && player.trek === trek) {
            // Logging out, teleporting or dying ends the trek.
            player.trek = null
        }
        return when {
            ending == Ending.ARRIVED -> TempleTrekking.ARRIVAL
            map.contains(player.position) -> trek.route.escape(leg)
            else -> player.position
        }
    }

    override fun recovery(player: Player): Position = trek.route.escape(leg)
}
