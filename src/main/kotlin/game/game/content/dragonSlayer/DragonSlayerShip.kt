/*
 * Purchase and repair mechanics adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, klarense.rs2 and lady_lumbridge.rs2.
 */
package game.content.dragonSlayer

import game.content.sailing.Sailing
import game.content.sailing.ShipRoute
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.varp.PersistentVarp.DRAGON_SLAYER_NED_HIRED

/**
 * Buys the Lady Lumbridge from Klarense and records each completed hull repair.
 *
 * @author lare96
 */
object DragonSlayerShip {
    const val KLARENSE = 744
    const val NED_IN_DRAYNOR = 918
    const val NED_ON_SHIP = 743
    const val HOLE = 2589
    const val DECK_LADDER = 2590
    const val HOLD_LADDER = 2592
    const val BOARD_PLANK = 2593
    const val LEAVE_PLANK = 2594
    const val PLANK = 960
    const val STEEL_NAILS = 1539
    const val HAMMER = 2347
    const val BUY_OPTION = "I'll buy her for 2,000 coins."
    const val HIRE_OPTION = "Will you captain my ship to Crandor?"
    const val MAP_OPTION = "I have the complete Crandor map."
    const val SAIL_OPTION = "Let's sail to Crandor."
    const val REPAIR_HOLD_OPTION = "Go down to the damaged hull."
    const val NED_HOLD_OPTION = "Go down to Ned."

    val dock = Position(3047, 3204)
    val boardingPlank = Position(3047, 3205, 1)
    val leavingPlank = Position(3047, 3206, 1)
    val deckLadder = Position(3049, 3208, 1)
    val repairHold = Position(3047, 9640, 1)
    val nedHold = Position(3047, 9640, 3)
    val crandor = Position(2851, 3235)

    private val price = Item(995, 2_000)
    private val materials = listOf(Item(PLANK), Item(STEEL_NAILS, 30))
    private const val REPAIRS_REQUIRED = 3

    fun klarense(player: Player, npc: Npc) {
        val stage = DragonSlayer.stage(player)
        val dialogue = player.newDialogue()
        when (stage) {
            DragonSlayer.OZIACH_SPOKEN_TO -> dialogue.npc(KLARENSE,
                "That's the Lady Lumbridge behind me. She's for sale,",
                "but you'll have to patch up the hull before she sails.")
                .npc(KLARENSE, "You can have the ship for 2,000 coins.")
                .options(BUY_OPTION, { buy(it, npc) }, "I'll leave it for now.", { })
            in DragonSlayer.SHIP_BOUGHT until DragonSlayer.SHIP_REPAIRED -> dialogue.npc(KLARENSE,
                "The hull has a hole. You'll find it below the deck.")
            in DragonSlayer.SHIP_REPAIRED until DragonSlayer.COMPLETE -> dialogue.npc(KLARENSE,
                "You've patched her up. All she needs now is a captain.")
            else -> dialogue.npc(KLARENSE, "I'm looking after the Lady Lumbridge.")
        }
        dialogue.open()
    }

    private fun buy(player: Player, npc: Npc) {
        if (DragonSlayer.stage(player) != DragonSlayer.OZIACH_SPOKEN_TO || !DragonSlayer.nearby(player, npc)) return
        if (!player.inventory.containsAll(listOf(price)) || !player.inventory.remove(price)) {
            player.newDialogue().npc(KLARENSE, "Bring 2,000 coins if you still want the ship.").open()
            return
        }
        DragonSlayer.advance(player, DragonSlayer.SHIP_BOUGHT)
        player.newDialogue().npc(KLARENSE, "She's yours. Best of luck getting her seaworthy again.").open()
    }

    fun needsRepairs(player: Player): Boolean = DragonSlayer.stage(player) in DragonSlayer.SHIP_BOUGHT until DragonSlayer.SHIP_REPAIRED

    fun hired(player: Player): Boolean = player.varpManager.getValue(DRAGON_SLAYER_NED_HIRED) != 0

    fun underway(player: Player): Boolean = player.isLocked &&
        player.overlays.getOverlay(StandardInterface::class.java)?.id == Sailing.JOURNEY_INTERFACE

    fun nedInDraynor(player: Player, npc: Npc) {
        val dialogue = player.newDialogue().npc(npc.id, "I've been looking for a reason to go back to sea.")
        val stage = DragonSlayer.stage(player)
        when {
            stage == DragonSlayer.SHIP_REPAIRED && hired(player) -> dialogue.options(MAP_OPTION, {
                giveMap(it, npc)
            }, "I'll come back later.", { })
            stage in DragonSlayer.OZIACH_SPOKEN_TO until DragonSlayer.MAP_GIVEN_TO_NED && !hired(player) ->
                dialogue.options(HIRE_OPTION, { p ->
                    if (DragonSlayer.nearby(p, npc) && DragonSlayer.collectingMap(p)) {
                        p.varpManager.setAndSendValue(DRAGON_SLAYER_NED_HIRED, 1)
                        p.newDialogue().npc(npc.id, "Bring me the chart once you have a seaworthy ship.").open()
                    }
                }, "I'll come back later.", { })
            stage == DragonSlayer.MAP_GIVEN_TO_NED -> dialogue.npc(npc.id, "Meet me below deck at Port Sarim.")
        }
        dialogue.open()
    }

    fun giveMap(player: Player, npc: Npc) {
        if (!DragonSlayer.nearby(player, npc) || !hired(player) || DragonSlayer.stage(player) != DragonSlayer.SHIP_REPAIRED) return
        val amount = player.inventory.computeAmountForId(DragonSlayer.CRANDOR_MAP)
        if (amount == 0) {
            player.newDialogue().npc(npc.id, "Bring all three pieces fitted together into one chart.").open()
            return
        }
        player.inventory.remove(Item(DragonSlayer.CRANDOR_MAP, amount))
        DragonSlayer.advance(player, DragonSlayer.MAP_GIVEN_TO_NED)
        player.newDialogue().npc(npc.id, "I'll meet you below deck aboard the Lady Lumbridge.").open()
    }

    /**
     * Offers the ordinary crossing. Quest credit is awarded at the destination after a successful crossing.
     */
    fun nedOnShip(player: Player, npc: Npc) {
        if (npc.z != nedHold.z || !npc.position.isWithinDistance(nedHold, 8)) {
            player.newDialogue().npc(npc.id, "The ship won't be sailing back. Look for a way through the caves.").open()
            return
        }
        player.newDialogue().npc(npc.id, "Ready to cross to Crandor?")
            .options(SAIL_OPTION, { p ->
                if (DragonSlayer.nearby(p, npc)) {
                    Sailing.sail(p, ShipRoute.PORT_SARIM_TO_CRANDOR, "The Lady Lumbridge puts out to sea.") { arrived ->
                        if (DragonSlayer.stage(arrived) == DragonSlayer.MAP_GIVEN_TO_NED) {
                            DragonSlayer.advance(arrived, DragonSlayer.SAILED_TO_CRANDOR)
                            arrived.varpManager.setAndSendValue(DRAGON_SLAYER_NED_HIRED, 0)
                            arrived.varpManager.setAndSendValue(PersistentVarp.DRAGON_SLAYER_SHIP_REPAIRS, 0)
                        }
                    }
                }
            }, "I'll wait here for now.", { }).open()
    }

    fun inspectHole(player: Player) {
        player.sendMessage("You'll need planks, steel nails and a hammer to repair the hull.")
    }

    /**
     * Uses a plank and 30 steel nails per repair. Moves up to the repaired hold after the third repair.
     */
    fun repair(player: Player) {
        if (!needsRepairs(player)) {
            player.sendMessage("You can only repair the hull after buying the ship.")
            return
        }
        val varps = player.varpManager
        val repairs = varps.getValue(PersistentVarp.DRAGON_SLAYER_SHIP_REPAIRS).coerceIn(0, REPAIRS_REQUIRED)
        if (repairs == REPAIRS_REQUIRED) return
        if (!player.inventory.contains(HAMMER) || !player.inventory.containsAll(materials)) {
            player.sendMessage("Each repair needs one plank, 30 steel nails and a hammer.")
            return
        }
        player.inventory.removeAll(materials)
        val repaired = repairs + 1
        varps.setAndSendValue(PersistentVarp.DRAGON_SLAYER_SHIP_REPAIRS, repaired)
        if (repaired < REPAIRS_REQUIRED) {
            player.sendMessage("You fasten a plank to the hull. Some of the hole is still open.")
            return
        }
        DragonSlayer.advance(player, DragonSlayer.SHIP_REPAIRED)
        player.move(player.position.translate(0, 0, 1))
        player.sendMessage("You finish patching the hole in the Lady Lumbridge.")
    }
}
