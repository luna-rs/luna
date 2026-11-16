package game.content.minecart

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import engine.obj.LadderDestination
import game.player.Sound
import io.luna.game.action.DeathInterruptible
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import io.luna.game.model.`object`.GameObject

/**
 * The Ice Mountain and Keldagrim minecart line. LostCityRS/Content 44eab090's revision 377
 * all.npc, all.loc and all.obj supply the conductor, Ride cart and direction-specific tickets.
 * The fare, landings and first-visit admission are reconstructed gameplay.
 *
 * @author lare96
 */
object KeldagrimMinecart {
    const val FARE = 150
    const val ICE_CART = 7029
    private val ICE_CART_POSITION = Position(2995, 9836)
    private val ICE_STATION = Position(2995, 9837)
    private val KELDAGRIM_STATION = Position(2923, 10180)
    private val BOARDING_REACH = InteractionPolicy(InteractionType.LINE_OF_SIGHT, 3)
    private var Player.firstBoatArrival by Attr.boolean().persist("keldagrim_first_boat_arrival")

    /**
     * The native ticket identifies the direction of travel, so the opposite ticket never pays a fare.
     *
     * @author lare96
     */
    enum class Route(val crew: Set<Int>, val departure: Position, val crewRadius: Int,
                     val destination: Position, val ticket: Int, val place: String) {
        TO_KELDAGRIM(setOf(2186), ICE_STATION, 8, Position(2922, 10180), 5021, "Keldagrim"),
        TO_ICE_MOUNTAIN(setOf(2180, 2181, 2182, 2183, 2184), KELDAGRIM_STATION, 20,
            Position(2996, 9837), 5020, "Ice Mountain")
    }

    fun visited(player: Player): Boolean = player.firstBoatArrival

    /**
     * Only the completed entrance-cavern boat journey unlocks repeat minecart travel.
     */
    fun markFirstBoatArrival(player: Player) { player.firstBoatArrival = true }

    fun restore(player: Player) { player.firstBoatArrival }

    fun route(npc: Npc): Route? = Route.entries.firstOrNull { npc.id in it.crew &&
        npc.state == EntityState.ACTIVE && npc.isAlive && npc.position.z == it.departure.z &&
        npc.position.isWithinDistance(it.departure, it.crewRadius) }

    /**
     * Offers a fare or consumes a previously owned ticket through the same journey action.
     */
    fun talk(player: Player, npc: Npc) {
        val route = route(npc) ?: return
        if (!canBoard(player, npc, route)) return
        if (!visited(player)) {
            player.newDialogue().npc(npc.id, "Visit Keldagrim by boat before riding the minecarts.").open()
            return
        }
        var accepted = false
        player.newDialogue().npc(npc.id, "The cart to ${route.place} costs $FARE coins,",
            "or you may use a ticket for this route.").options("Ride to ${route.place}.", {
            if (!accepted) {
                accepted = true
                it.overlays.closeWindows()
                ride(it, npc)
            }
        }, "No, thanks.", { it.overlays.closeWindows() }).open()
    }

    /**
     * The Ice Mountain cart is present in the selected 377 map; the Keldagrim cart is definition-only.
     */
    fun useCart(player: Player, cart: GameObject) {
        if (cart.id != ICE_CART || cart.position != ICE_CART_POSITION) return
        val conductor = player.world.locator.findNpcs(ICE_STATION, 8) {
            it.id == 2186 && route(it) == Route.TO_KELDAGRIM &&
                canBoard(player, it, Route.TO_KELDAGRIM)
        }.firstOrNull()
        if (conductor == null) player.sendMessage("Speak to the cart conductor nearby to ride the minecart.")
        else talk(player, conductor)
    }

    /**
     * Reserves one existing payment type; the locked trip debits it only at a legal arrival.
     */
    fun ride(player: Player, npc: Npc): Boolean {
        val route = route(npc) ?: return false
        if (!visited(player) || !canBoard(player, npc, route)) return false
        val payment = if (player.inventory.contains(route.ticket)) Item(route.ticket) else Item(995, FARE)
        if (!player.inventory.contains(payment)) {
            player.sendMessage("You need $FARE coins or the ticket to ${route.place}.")
            return false
        }
        if (!LadderDestination.canLand(route.destination)) {
            player.sendMessage("The minecart cannot reach a safe stop right now.")
            return false
        }
        player.submitAction(Trip(player, npc, route, payment))
        return true
    }

    private fun ready(player: Player): Boolean = player.state == EntityState.ACTIVE && player.isAlive &&
        !player.isLocked && !player.walking.isLocked && !player.status.isImmobilized() &&
        player.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

    private fun canBoard(player: Player, npc: Npc, route: Route): Boolean = ready(player) &&
        KeldagrimMinecart.route(npc) == route && player.position.z == npc.position.z &&
        player.position.isWithinDistance(npc.position, 3) &&
        player.world.collisionManager.reached(player, npc, BOARDING_REACH)

    /**
     * A cancelled, disconnected or dead passenger neither pays nor moves. Payment is committed on arrival.
     *
     * @author lare96
     */
    private class Trip(player: Player, private val conductor: Npc, private val route: Route,
                       private val payment: Item) : LockedAction(player), DeathInterruptible {
        private val origin = player.position
        private var lockRevision = -1L

        override fun beforeLock(): Boolean = visited(mob) && canBoard(mob, conductor, route) &&
            mob.inventory.contains(payment) && LadderDestination.canLand(route.destination)

        override fun onLock() {
            lockRevision = mob.actionLockRevision
            mob.walking.clear()
            mob.overlays.closeWindows()
            mob.animation(Animation(2148))
            mob.playSound(Sound.CART_AWAY)
            mob.sendMessage("You climb into the minecart.")
        }

        override fun run(): Boolean {
            if (mob.state != EntityState.ACTIVE || !mob.isAlive || !mob.isLocked ||
                mob.actionLockRevision != lockRevision || mob.position != origin ||
                mob.actions.getAll(MobDeathAction::class.java).any { !it.isFinished }) return true
            if (executions < 6) return false
            if (!LadderDestination.canLand(route.destination) || !mob.inventory.remove(payment)) return true
            mob.move(route.destination)
            mob.sendMessage("The cart rattles to ${route.place}.")
            return true
        }

        override fun onUnlock() { mob.animation(Animation.CANCEL) }
    }
}
