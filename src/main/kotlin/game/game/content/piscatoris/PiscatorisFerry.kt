package game.content.piscatoris

import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * Kathy Corkat's river boat between the mainland and Piscatoris's southern shore.
 * The crew identities use revision 377 data; the fare and landings are corroborated by the retained 2006Scape source.
 *
 * @author lare96
 */
object PiscatorisFerry {

    const val FARE = 50
    const val AGREE = "Yes, please."
    val MAINLAND = Position(2367, 3488)
    val COLONY = Position(2358, 3640)
    val CREW = setOf(3829, 3830, 3831)

    /**
     * The shore journeys, including the client's raw morph-parent NPC on the mainland.
     *
     * @author lare96
     */
    enum class Route(val departure: Position, val destination: Position, val place: String) {
        TO_COLONY(MAINLAND, COLONY, "the Piscatoris Fishing Colony"),
        TO_MAINLAND(COLONY, MAINLAND, "the mainland")
    }

    /**
     * Identifies the shore from the active ferryman's identity and position.
     */
    fun route(npc: Npc): Route? = if (npc.id !in CREW || npc.state != EntityState.ACTIVE) null else
        Route.entries.firstOrNull { npc.position.isWithinDistance(it.departure, 10) }

    /**
     * Offers the river voyage through Kathy's ordinary conversation.
     */
    fun talk(plr: Player, npc: Npc) {
        val route = route(npc) ?: return
        if (plr.isLocked || !canBoard(plr, npc, route)) return
        // TODO Once Swan Song is implemented, completed players receive Kathy's free passage.
        var accepted = false
        plr.newDialogue().npc(npc.id, "I can take you to ${route.place}.", "The journey costs 50 coins.")
            .options(AGREE, {
                if (!accepted) {
                    accepted = true
                    it.overlays.closeWindows()
                    sail(it, npc)
                }
            }, "No, thanks.", { it.overlays.closeWindows() }).open()
    }

    /**
     * Locks one voyage and charges the fare together with its successful opposite-shore landing.
     */
    fun sail(plr: Player, npc: Npc): Boolean {
        val route = route(npc) ?: return false
        if (plr.isLocked || !canBoard(plr, npc, route)) return false
        if (!plr.inventory.contains(Item(995, FARE))) {
            plr.sendMessage("You need 50 coins to pay for the journey.")
            return false
        }
        plr.submitAction(Voyage(plr, npc, route))
        return true
    }

    /**
     * Checks the passenger independently of the driver once the voyage starts.
     */
    private fun canTravel(plr: Player): Boolean = plr.state == EntityState.ACTIVE && plr.isAlive &&
        plr.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

    /**
     * Revalidates the actual shore crew immediately before boarding.
     */
    private fun canBoard(plr: Player, npc: Npc, route: Route): Boolean = canTravel(plr) && PiscatorisFerry.route(npc) == route &&
        plr.position.isWithinDistance(npc.position, 3) && plr.position.isWithinDistance(route.departure, 10)

    /**
     * A short locked voyage that cannot debit or move an interrupted or dead passenger.
     *
     * @author lare96
     */
    private class Voyage(plr: Player, private val npc: Npc, private val route: Route) : LockedAction(plr) {
        override fun beforeLock(): Boolean = !mob.isLocked && canBoard(mob, npc, route) &&
            mob.inventory.contains(Item(995, FARE))

        override fun onLock() {
            mob.walking.clear()
            mob.overlays.closeWindows()
            mob.sendMessage("You board Kathy's boat.")
        }

        override fun run(): Boolean {
            if (!canTravel(mob)) return true
            if (executions < 6) return false
            val payment = Item(995, FARE)
            if (!mob.inventory.contains(payment) || !mob.inventory.remove(payment)) {
                mob.sendMessage("You need 50 coins to pay for the journey.")
                return true
            }
            mob.move(route.destination)
            mob.sendMessage("Kathy takes you to ${route.place}.")
            return true
        }
    }
}
