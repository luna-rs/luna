package game.content.waterbirth

import game.npc.spawn.rellekka.Fremennik
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * Jarvald's shore-to-shore ferry, separate from the deck-and-gangplank ShipRoute model.
 * The NPC identities/placements are cache-verified 377 data; the fare is later corroborated.
 * Luna's existing Fremennik membership check currently accepts everyone pending its quest implementation.
 *
 * @author lare96
 */
object Jarvald {
    const val FARE = 1000
    val RELLEKKA = Position(2621, 3683)
    val WATERBIRTH = Position(2544, 3759)

    /**
     * Source NPC2435 is a client morph parent; 2436/2437 are its talk/travel variants, and2438 is the island ferryman.
     *
     * @author lare96
     */
    enum class Route(val crew: Set<Int>, val departure: Position, val destination: Position, val place: String) {
        TO_ISLAND(setOf(2435, 2436, 2437), RELLEKKA, WATERBIRTH, "Waterbirth Island"),
        TO_RELLEKKA(setOf(2438), WATERBIRTH, RELLEKKA, "Rellekka")
    }

    fun fare(player: Player): Int = if (Fremennik.isFremennik(player)) 0 else FARE
    fun route(npc: Npc): Route? = Route.entries.firstOrNull { npc.id in it.crew && npc.position.isWithinDistance(it.departure, 8) }

    fun talk(player: Player, npc: Npc) {
        val route = route(npc) ?: return
        player.newDialogue().npc(npc.id, "I can take you to ${route.place}.",
            if (fare(player) == 0) "You are welcome aboard, fellow Fremennik." else "The fare is 1,000 coins.")
            .options("Yes, let's go.", { sail(it, npc) }, "Not right now.", { it.overlays.closeWindows() }).open()
    }

    /**
     * Pays once on boarding, locks the normal journey and lands only if it reaches its final execution.
     */
    fun sail(player: Player, npc: Npc): Boolean {
        val route = route(npc) ?: return false
        fun valid(): Boolean = player.state == EntityState.ACTIVE && player.isAlive && npc.state == EntityState.ACTIVE &&
            player.position.isWithinDistance(npc.position, 3) && player.position.isWithinDistance(route.departure, 10)
        if (!valid() || player.isLocked) return false
        val price = fare(player)
        if (price > 0 && !player.inventory.contains(Item(995, price))) {
            player.sendMessage("You need 1,000 coins to pay for the journey.")
            return false
        }
        player.submitAction(object : LockedAction(player) {
            private var paid = false
            private var arrived = false
            override fun beforeLock(): Boolean = valid() && !player.isLocked
            override fun onLock() {
                paid = price == 0 || player.inventory.remove(Item(995, price))
                if (!paid) { complete(); return }
                player.walking.clear()
                player.overlays.closeWindows()
                player.sendMessage("You board Jarvald's boat.")
            }
            override fun run(): Boolean {
                if (!paid || !player.isAlive || player.state != EntityState.ACTIVE) return true
                if (executions < 6) return false
                arrived = true
                return true
            }
            override fun onUnlock() {
                if (arrived && player.isAlive && player.state == EntityState.ACTIVE) {
                    player.move(route.destination)
                    player.sendMessage("Jarvald takes you to ${route.place}.")
                }
            }
        })
        return true
    }
}
