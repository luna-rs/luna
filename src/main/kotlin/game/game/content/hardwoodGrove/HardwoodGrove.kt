package game.content.hardwoodGrove

import api.predef.*
import api.predef.ext.*
import game.obj.doors.DoorPassageAction
import game.obj.doors.DoorType
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import java.util.concurrent.CompletableFuture

/**
 * Paid admission to the native Tai Bwo Wannai hardwood enclosure. The fee is charged for each
 * successful entry, while either leaf always permits a free exit. Gabooty's ordinary gem trade
 * supplies the same currency without manufacturing rewards for the unimplemented Clean-Up game.
 *
 * @author lare96
 */
object HardwoodGrove {
    const val STICKS = 6306
    const val FEE = 100
    const val COOPERATIVE = "Gabooty's Tai Bwo Wannai Cooperative."
    const val DRINKS = "Gabooty's Tai Bwo Wannai Drinky Store."
    val MURCAILY = Position(2816, 3083)
    val GABOOTY = Position(2794, 3065)
    private val leaves = mapOf(9038 to Position(2817, 3083), 9039 to Position(2817, 3084))
    private val types = mapOf(9038 to DoorType(9038, 9040, null, null, null, 3),
                              9039 to DoorType(9039, 9041, null, null, null, 3))

    internal fun ready(player: Player): Boolean = player.state == EntityState.ACTIVE && player.isAlive &&
        !player.isLocked && !player.walking.isLocked && !player.status.isImmobilized() &&
        player.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

    internal fun nativeDoor(door: GameObject): Boolean = leaves[door.id] == door.position &&
        door.direction == ObjectDirection.WEST && door.objectType == ObjectType.STRAIGHT_WALL &&
        door.state == EntityState.ACTIVE

    /**
     * Quotes entry on Open, skips the quote on Quick-Pay, and leaves the enclosure without payment.
     */
    fun useDoor(player: Player, door: GameObject, quick: Boolean) {
        if (!ready(player) || !nativeDoor(door) || player.position.z != door.position.z ||
            !player.position.isWithinDistance(door.position, 3)) return
        if (player.x >= door.x || quick) {
            player.overlays.closeWindows()
            player.submitAction(Approach(player, door))
        } else {
            var accepted = false
            player.newDialogue().npc(2530, "Entry to the hardwood grove costs 100 trading sticks.",
                "You must pay each time you enter.").options("Pay 100 trading sticks.", { passenger ->
                if (!accepted) {
                    accepted = true
                    if (ready(passenger) && nativeDoor(door) && passenger.position.z == door.position.z &&
                        passenger.position.isWithinDistance(door.position, 3)) {
                        passenger.overlays.closeWindows()
                        passenger.submitAction(Approach(passenger, door))
                    }
                }
            }, "No thanks.", { it.overlays.closeWindows() }).open()
        }
    }

    /**
     * Offers admission through the real nearby gatekeeper, including the ordinary source of trading sticks.
     */
    fun talkToMurcaily(player: Player, npc: Npc) {
        if (!near(player, npc, 2528, 2529, 2530)) return
        player.newDialogue().npc(if (npc.id == 2528) 2530 else npc.id, "The grove contains teak and mahogany trees.",
            "Entry costs 100 trading sticks each time.", "Gabooty trades local gems for trading sticks.")
            .options("I'd like to enter the grove.", { passenger ->
                if (near(passenger, npc, 2528, 2529, 2530)) {
                    val door = world.objects.findAll(Position(2817, 3083)).filter { nativeDoor(it) }
                        .findFirst().orElse(null)
                    if (door != null) useDoor(passenger, door, false)
                }
            }, "No thanks.", { it.overlays.closeWindows() }).open()
    }

    /**
     * Opens only the named native shop while its merchant remains nearby.
     */
    fun openShop(player: Player, npc: Npc, name: String) {
        if (name !in setOf(COOPERATIVE, DRINKS) || !near(player, npc, 2519, 2520, 2521)) return
        player.overlays.open(ShopInterface(world.shops.get(name)))
    }

    fun talkToGabooty(player: Player, npc: Npc) {
        if (!near(player, npc, 2519, 2520, 2521)) return
        player.newDialogue().npc(if (npc.id == 2519) 2521 else npc.id, "I trade in trading sticks, the local currency.",
            "Bring me opals, jade or red topaz to earn some.")
            .options("Trade cooperative.", { openShop(it, npc, COOPERATIVE) },
                "Trade drinks.", { openShop(it, npc, DRINKS) },
                "No thanks.", { it.overlays.closeWindows() }).open()
    }

    private fun near(player: Player, npc: Npc, vararg ids: Int): Boolean = ready(player) && npc.id in ids &&
        npc.state == EntityState.ACTIVE && npc.isAlive && player.position.z == npc.position.z &&
        player.position.isWithinDistance(npc.position, 5)

    /**
     * Selects visible native merchant forms for the configured multinpc roots. Clean-Up's per-player
     * chat varbits are not active, so keeping the raw roots would leave the default client morph hidden.
     */
    fun initializeMerchants() {
        initializeMerchant(MURCAILY, 2528, 2530)
        initializeMerchant(GABOOTY, 2519, 2521)
    }

    private fun initializeMerchant(anchor: Position, root: Int, visible: Int) {
        val merchants = world.locator.findNpcs(anchor, 5) {
            it.position.z == anchor.z && it.id in root..visible
        }
        if (merchants.isEmpty()) world.addNpc(visible, anchor.x, anchor.y)
        else merchants.filter { it.id == root }.forEach { it.transform(visible) }
    }

    /**
     * Aligns the passenger on the current side before using the ordinary one-tile conditional passage.
     * Choosing the side by half-plane also handles clicks made more than one tile inside the enclosure.
     *
     * @author lare96
     */
    internal class Approach(player: Player, private val door: GameObject) : Action<Player>(player, ActionType.WEAK) {
        private val enteringGrove = player.x < door.x
        private val start = if (enteringGrove) door.position.translate(-1, 0) else door.position
        private var walking: CompletableFuture<NavigationResult>? = null
        private var ticks = 0

        override fun run(): Boolean {
            if (++ticks > 30 || !ready(mob) || !nativeDoor(door) ||
                mob.position.z != door.position.z ||
                !mob.position.isWithinDistance(door.position, 4)) return true
            if (enteringGrove && !mob.inventory.contains(Item(STICKS, FEE))) {
                mob.sendMessage("You need 100 trading sticks to enter the hardwood grove.")
                return true
            }
            if (walking?.isDone == false) return false
            if (mob.position != start) {
                if (walking != null) return true
                walking = mob.navigator.navigate(start, false)
                return false
            }
            mob.submitAction(Passage(mob, door))
            return true
        }
    }

    /**
     * The native walls face west, so the door helper's entering side is the enclosure's inside.
     * The ordinary conditional passage keeps a blocker in place so another player cannot follow for free.
     *
     * @author lare96
     */
    internal class Passage(player: Player, private val door: GameObject) :
        DoorPassageAction(player, door, types.getValue(door.id)) {

        override fun checkAccess(entering: Boolean): Boolean {
            if (!ready(mob) || !nativeDoor(door) || mob.position.z != door.position.z ||
                !world.objects.findAll(door.position).anyMatch { it === door }) return false
            if (!entering && !mob.inventory.contains(Item(STICKS, FEE))) {
                mob.sendMessage("You need 100 trading sticks to enter the hardwood grove.")
                return false
            }
            return true
        }

        override fun onPassage(entering: Boolean) {
            if (!entering) {
                check(mob.inventory.remove(Item(STICKS, FEE))) { "The verified grove fee changed during admission." }
                mob.sendMessage("You pay 100 trading sticks and enter the hardwood grove.")
            }
        }
    }
}
