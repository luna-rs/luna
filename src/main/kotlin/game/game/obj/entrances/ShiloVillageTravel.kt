package game.obj.entrances

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Hajedy and Vigroy's cart journeys between Brimhaven and Shilo Village.
 *
 * @author lare96
 */
object ShiloVillageTravel {

    const val BRIMHAVEN_CART = 2230
    const val SHILO_CART = 2265
    const val HAJEDY = 510
    const val VIGROY = 511
    const val MAX_FARE = 200
    val BRIMHAVEN = Position(2776, 3214)
    val SHILO = Position(2834, 2951)

    /**
     * Charges five percent of carried coins, between ten and two hundred, without overflowing large coin stacks.
     */
    fun fare(coins: Int): Int = (coins.toLong() * 5L / 100L).coerceIn(10L, MAX_FARE.toLong()).toInt()

    /**
     * Quotes one fare and offers a journey with the nearby, active driver.
     */
    fun offer(plr: Player, driver: Npc) {
        if (!canBoard(plr, driver) || plr.isLocked) return
        // TODO Once the Shilo Village quest is implemented, Hajedy requires its completion before offering passage.
        val destination = if (driver.id == HAJEDY) SHILO else BRIMHAVEN
        val place = if (driver.id == HAJEDY) "Shilo Village" else "Brimhaven"
        val quoted = fare(plr.inventory.computeAmountForId(995))
        var accepted = false
        plr.newDialogue()
            .npc(driver.id, "I am offering a cart ride to $place.", "It will cost $quoted gold coins. Is that okay?")
            .options("Yes, take me to $place.", { passenger ->
                if (!accepted) {
                    accepted = true
                    passenger.overlays.closeWindows()
                    passenger.submitAction(Journey(passenger, driver, destination, quoted))
                }
            }, "No, thank you.", { it.overlays.closeWindows() })
            .open()
    }

    /**
     * Boarding the native cart asks its matching driver about the journey.
     */
    fun board(plr: Player, cart: GameObject) {
        val id = when (cart.id) {
            BRIMHAVEN_CART -> HAJEDY
            SHILO_CART -> VIGROY
            else -> return
        }
        val driver = world.locator.findNpcs(plr.position, 7, true) {
            it.id == id && it.state == EntityState.ACTIVE
        }.firstOrNull()
        if (driver == null) {
            plr.sendMessage("There is no cart driver nearby.")
            return
        }
        offer(plr, driver)
    }

    /**
     * Checks the passenger independently of the driver once a journey is underway.
     */
    private fun canTravel(plr: Player): Boolean = plr.state == EntityState.ACTIVE && plr.isAlive &&
        plr.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

    /**
     * Revalidates the driver when a quoted journey is accepted.
     */
    private fun canBoard(plr: Player, driver: Npc): Boolean = canTravel(plr) &&
        (driver.id == HAJEDY || driver.id == VIGROY) && driver.state == EntityState.ACTIVE &&
        plr.position.isWithinDistance(driver.position, 7)

    /**
     * Locks one journey and charges its quoted fare together with the successful landing.
     *
     * @author lare96
     */
    private class Journey(plr: Player, private val driver: Npc, private val destination: Position,
                          private val quoted: Int) : LockedAction(plr) {
        override fun beforeLock(): Boolean {
            if (mob.isLocked || !canBoard(mob, driver)) return false
            if (!mob.inventory.contains(Item(995, quoted))) {
                mob.sendMessage("You don't have enough coins for the cart ride.")
                return false
            }
            return true
        }

        override fun onLock() {
            mob.walking.clear()
            mob.sendMessage("You hop into the cart for the journey.")
        }

        override fun run(): Boolean {
            if (!canTravel(mob)) return true
            if (executions < 3) return false
            val payment = Item(995, quoted)
            if (!mob.inventory.contains(payment) || !mob.inventory.remove(payment)) {
                mob.sendMessage("You don't have enough coins for the cart ride.")
                return true
            }
            mob.move(destination)
            mob.sendMessage("You pay the $quoted coin fare. You feel tired from the journey.")
            return true
        }
    }
}
