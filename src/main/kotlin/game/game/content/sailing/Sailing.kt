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
     * The id of coins, which fares are paid in.
     */
    const val COINS = 995

    /**
     * The fare most ships charge, in coins.
     */
    const val FARE = 30

    /**
     * Captain Tobias, Seaman Lorris and Seaman Thresnor, who sail from Port Sarim to Karamja.
     */
    val PORT_SARIM_SAILORS = setOf(376, 377, 378)

    /**
     * Captain Barnaby, who sails from Ardougne to Brimhaven.
     */
    const val CAPTAIN_BARNABY = 381

    /**
     * The customs officers at Musa Point and Brimhaven.
     */
    const val CUSTOMS_OFFICER = 380

    /**
     * The Monks of Entrana at Port Sarim, who sail to Entrana.
     */
    val PORT_SARIM_MONKS = setOf(657, 2728, 2729)

    /**
     * The Monks of Entrana on Entrana, who sail back to Port Sarim.
     */
    val ENTRANA_MONKS = setOf(658, 2730, 2731)

    /**
     * Karamjan rum, which customs officers confiscate.
     */
    const val KARAMJAN_RUM = 431

    /**
     * The option that accepts a sailor's offer of a trip.
     */
    const val YES_PLEASE = "Yes please."

    /**
     * The option that asks a customs officer to board.
     */
    const val CAN_I_JOURNEY = "Can I journey on this ship?"

    /**
     * The option that lets a customs officer search the player.
     */
    const val SEARCH_AWAY = "Search away, I have nothing to hide."

    /**
     * The option that pays a customs officer's boarding charge.
     */
    const val OK = "Ok."

    /**
     * The option that accepts the passage to Entrana.
     */
    const val READY_TO_GO = "Yes, okay, I'm ready to go."

    /**
     * The option that accepts the passage back from Entrana.
     */
    const val READY_TO_LEAVE = "Yes, I'm ready to go."

    /**
     * The tabs hidden during a journey. Only the friends and ignore lists stay.
     */
    val HIDDEN_TABS = listOf(TabIndex.COMBAT, TabIndex.SKILL, TabIndex.QUEST, TabIndex.INVENTORY,
                             TabIndex.EQUIPMENT, TabIndex.PRAYER, TabIndex.MAGIC, TabIndex.LOGOUT,
                             TabIndex.SETTINGS, TabIndex.EMOTE, TabIndex.MUSIC)

    /**
     * Takes the fare of [route] from [plr], or says they can't afford it. Returns `true` if they paid, or if the route
     * is free.
     */
    fun payFare(plr: Player, route: ShipRoute): Boolean {
        if (route.fare == 0) {
            return true
        }
        if (plr.inventory.computeAmountForId(COINS) < route.fare) {
            plr.newDialogue().player(Expression.SAD, "Oh dear, I don't actually seem to have enough money.").open()
            return false
        }
        return plr.inventory.remove(Item(COINS, route.fare))
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
