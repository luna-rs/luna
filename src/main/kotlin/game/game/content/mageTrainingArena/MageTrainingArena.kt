package game.content.mageTrainingArena

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.WalkableInterface

/**
 * The Mage Training Arena north of the Duel Arena: the entrance hall, the four training rooms behind its portals,
 * the Pizazz Points each room pays out, and the progress hat that keeps them.
 *
 * @author TheLining
 */
object MageTrainingArena {

    /**
     * The NPC ids of the arena's guardians.
     */
    const val ENTRANCE_GUARDIAN = 3097
    const val TELEKINETIC_GUARDIAN = 3098
    const val ALCHEMY_GUARDIAN = 3099
    const val ENCHANTMENT_GUARDIAN = 3100
    const val GRAVEYARD_GUARDIAN = 3101
    const val MAZE_GUARDIAN = 3102
    const val REWARDS_GUARDIAN = 3103

    /**
     * The NPC whose chathead the progress hat talks with.
     */
    const val PIZAZZ_HAT = 3096

    /**
     * The progress hat when dull, energised and full.
     */
    const val DULL_HAT = 6885
    const val ENERGISED_HAT = 6886
    const val FULL_HAT = 6887
    val HATS = listOf(DULL_HAT, ENERGISED_HAT, FULL_HAT)

    /**
     * The Arena book sold by the Rewards Guardian.
     */
    const val ARENA_BOOK = 6891

    /**
     * The exit teleport found in every training room.
     */
    const val EXIT_TELEPORT = 10782

    /**
     * The doorway of light at the front of the entrance hall, and the tiles either side of it.
     */
    const val DOORWAY = 10721
    val OUTSIDE_DOORWAY = Position(3363, 3298)
    val INSIDE_DOORWAY = Position(3363, 3300)

    /**
     * Clue scrolls and caskets may not be taken into any training room.
     */
    private val CLUE_NAMES = listOf("Clue scroll", "Casket")

    /**
     * Where a player stands in the entrance hall after leaving a room by any way but its exit teleport.
     */
    val ENTRANCE = Position(3363, 3302)

    /**
     * The four kinds of Pizazz Points, with the most of each a progress hat can hold.
     */
    enum class Points(val label: String, val cap: Int) {
        TELEKINETIC("Telekinetic", 4000),
        ALCHEMIST("Alchemist", 8000),
        ENCHANTMENT("Enchantment", 16000),
        GRAVEYARD("Graveyard", 4000)
    }

    var Player.telekineticPizazz by Attr.int().persist("mta_telekinetic_points")
    var Player.alchemistPizazz by Attr.int().persist("mta_alchemist_points")
    var Player.enchantmentPizazz by Attr.int().persist("mta_enchantment_points")
    var Player.graveyardPizazz by Attr.int().persist("mta_graveyard_points")

    /**
     * Whether the Entrance Guardian has given this player a progress hat.
     */
    var Player.mtaStarted by Attr.boolean().persist("mta_started")

    /**
     * Whether the player has bought the Bones to Peaches spell from the Rewards Guardian.
     */
    var Player.bonesToPeachesUnlocked by Attr.boolean().persist("mta_bones_to_peaches")

    /**
     * The room the player is training in, if any.
     */
    var Player.arenaRoom by Attr.nullableObj(ArenaRoom::class)

    /**
     * Returns [plr]'s Pizazz Points of [type].
     */
    fun points(plr: Player, type: Points): Int = when (type) {
        Points.TELEKINETIC -> plr.telekineticPizazz
        Points.ALCHEMIST -> plr.alchemistPizazz
        Points.ENCHANTMENT -> plr.enchantmentPizazz
        Points.GRAVEYARD -> plr.graveyardPizazz
    }

    private fun setPoints(plr: Player, type: Points, value: Int) {
        val clamped = value.coerceIn(0, type.cap)
        when (type) {
            Points.TELEKINETIC -> plr.telekineticPizazz = clamped
            Points.ALCHEMIST -> plr.alchemistPizazz = clamped
            Points.ENCHANTMENT -> plr.enchantmentPizazz = clamped
            Points.GRAVEYARD -> plr.graveyardPizazz = clamped
        }
    }

    /**
     * Gives [plr] up to [amount] points of [type]; a hat that is already full of that type takes no more.
     */
    fun addPoints(plr: Player, type: Points, amount: Int) {
        if (amount <= 0) return
        setPoints(plr, type, points(plr, type) + amount)
        syncHat(plr)
        plr.arenaRoom?.refreshOverlay(plr)
    }

    /**
     * Takes up to [amount] points of [type] from [plr], never below zero.
     */
    fun removePoints(plr: Player, type: Points, amount: Int) {
        setPoints(plr, type, points(plr, type) - amount)
        syncHat(plr)
        plr.arenaRoom?.refreshOverlay(plr)
    }

    /**
     * Destroying a progress hat loses every Pizazz Point it held.
     */
    fun wipePoints(plr: Player) {
        Points.values().forEach { setPoints(plr, it, 0) }
    }

    /**
     * Whether [plr] carries or wears a progress hat.
     */
    fun hasHat(plr: Player): Boolean =
        plr.inventory.containsAny(*HATS.toIntArray()) || plr.equipment[Equipment.HEAD]?.id in HATS

    /**
     * The hat [plr]'s points call for: energised at 300 or more of every kind, full at 600 or more.
     */
    fun hatFor(plr: Player): Int {
        val lowest = Points.values().minOf { points(plr, it) }
        return when {
            lowest >= 600 -> FULL_HAT
            lowest >= 300 -> ENERGISED_HAT
            else -> DULL_HAT
        }
    }

    /**
     * Changes [plr]'s progress hats, carried or worn, to the look their points call for.
     */
    fun syncHat(plr: Player) {
        val wanted = hatFor(plr)
        for (index in 0 until plr.inventory.capacity()) {
            val id = plr.inventory.computeIdForIndex(index)
            if (id in HATS && id != wanted) plr.inventory[index] = Item(wanted)
        }
        val worn = plr.equipment[Equipment.HEAD]?.id
        if (worn != null && worn in HATS && worn != wanted) plr.equipment[Equipment.HEAD] = Item(wanted)
    }

    /**
     * Whether [plr] is carrying a clue scroll or casket.
     */
    fun carriesClue(plr: Player): Boolean = plr.inventory.any { item ->
        item != null && CLUE_NAMES.any { item.itemDef.name == it }
    }

    /**
     * Sends [plr] through the portal into [room], if they may go.
     */
    fun enter(plr: Player, room: ArenaRoom) {
        if (plr.arenaRoom != null || plr.state != EntityState.ACTIVE) return
        if (!hasHat(plr)) {
            plr.sendMessage("You need a Pizazz Progress Hat to enter. Speak to the Entrance Guardian.")
            return
        }
        if (plr.magic.level < room.level) {
            plr.sendMessage("You need a Magic level of ${room.level} to enter the ${room.title}.")
            return
        }
        if (carriesClue(plr)) {
            plr.sendMessage("You can't take clue scrolls or caskets into the training rooms.")
            return
        }
        if (!room.canEnter(plr)) return
        plr.overlays.closeWindows()
        plr.arenaRoom = room
        plr.controllers.register(ArenaController(plr, room))
        room.enter(plr)
        room.openOverlay(plr)
    }

    /**
     * Takes [plr] out of their room. Through the exit teleport they arrive by the room's portal; any other way
     * (logging out, teleporting, dying) puts them at the entrance. Either way, everything they picked up in the
     * room is taken from them.
     */
    fun leave(plr: Player, throughPortal: Boolean) {
        val room = plr.arenaRoom ?: return
        plr.arenaRoom = null
        room.leave(plr, throughPortal)
        removeRoomItems(plr, room)
        plr.overlays.closeWalkable()
        if (plr.controllers.primary is ArenaController) plr.controllers.unregister()
        plr.move(if (throughPortal) room.exitTile else ENTRANCE)
    }

    /**
     * Removes every item that belongs to [room] from [plr]'s inventory and equipment.
     */
    fun removeRoomItems(plr: Player, room: ArenaRoom) {
        for (index in 0 until plr.inventory.capacity()) {
            if (plr.inventory.computeIdForIndex(index) in room.roomItems) plr.inventory[index] = null
        }
        for (index in 0 until plr.equipment.capacity()) {
            if (plr.equipment[index]?.id in room.roomItems) plr.equipment[index] = null
        }
    }

    /**
     * Shows [overlay] for [plr].
     */
    fun openOverlay(plr: Player, overlay: Int) {
        plr.overlays.open(WalkableInterface(overlay))
    }
}
