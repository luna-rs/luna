package game.content.sailing

import api.predef.*
import api.predef.ext.*
import game.player.Jingles
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.Varp

/**
 * Sends players on ship journeys.
 *
 * @author TheLining
 */
object Sailing {

    /**
     * The ship journey interface, a map that shows the ship sailing its route.
     */
    const val JOURNEY_INTERFACE = 3281

    /**
     * The varp that picks which route the ship journey interface draws.
     */
    const val JOURNEY_VARP = 75

    /**
     * The fare most ships charge.
     */
    val FARE = Item(995, 30)

    /**
     * The tabs hidden during a journey. Only the friends and ignore lists stay.
     */
    val HIDDEN_TABS = listOf(TabIndex.COMBAT, TabIndex.SKILL, TabIndex.QUEST, TabIndex.INVENTORY,
                             TabIndex.EQUIPMENT, TabIndex.PRAYER, TabIndex.MAGIC, TabIndex.LOGOUT,
                             TabIndex.SETTINGS, TabIndex.EMOTE, TabIndex.MUSIC)

    /**
     * Takes the 30 coin fare from [plr], or says they can't afford it. Returns `true` if they paid.
     */
    fun payFare(plr: Player): Boolean {
        if (plr.inventory.computeAmountForId(FARE.id) < FARE.amount) {
            plr.newDialogue().player(Expression.SAD, "Oh dear, I don't actually seem to have enough money.").open()
            return false
        }
        return plr.inventory.remove(FARE)
    }

    /**
     * Sails [plr] along [route], sending [message] as they board.
     */
    fun sail(plr: Player, route: ShipRoute, message: String) {
        plr.lock()
        val tabs = HIDDEN_TABS.associateWith { plr.tabs.get(it) }
        plr.sendVarp(Varp(JOURNEY_VARP, route.journey))
        plr.overlays.open(StandardInterface(JOURNEY_INTERFACE))
        plr.playJingle(Jingles.SAILING_JOURNEY)
        HIDDEN_TABS.forEach { plr.tabs.clear(it) }
        plr.sendMessage(message)
        world.scheduleOnce(route.ticks) {
            if (plr.state == EntityState.ACTIVE) {
                for ((tab, id) in tabs) {
                    id.ifPresent { plr.tabs.set(tab, it) }
                }
                plr.move(route.destination) // Also closes the ship journey interface.
                plr.unlock()
                plr.newDialogue().text("The ship arrives at ${route.place}.").open()
            }
        }
    }
}
