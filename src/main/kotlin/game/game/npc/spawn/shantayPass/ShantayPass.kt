package game.npc.spawn.shantayPass

import api.predef.*
import api.attr.Attr
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Shantay's native passage: entering consumes one ticket after the warning; returning is free.
 *
 * @author lare96
 */
object ShantayPass {
    const val DOORWAY = 4031
    const val GUARD = 838
    const val PASS = 1854
    const val DISCLAIMER = 1848
    const val AGREE = "Yeah, that poster doesn't scare me!"
    val POSITION = Position(3302, 3116)
    val NORTH = Position(3304, 3118)
    val SOUTH = Position(3304, 3115)

    private var Player.admission by Attr.nullableObj(Any::class)

    /**
     * Identifies the active conversation so an interrupted bot trip can invalidate its own delayed callbacks.
     */
    internal fun admission(plr: Player): Any? = plr.admission

    /**
     * Invalidates only the conversation that belonged to the interrupted trip.
     */
    internal fun cancelAdmission(plr: Player, attempt: Any?) {
        if (attempt != null && plr.admission === attempt) plr.admission = null
    }

    /**
     * Uses the mapped doorway from its immediate north or south approach.
     */
    fun use(plr: Player, doorway: GameObject) {
        if (doorway.id != DOORWAY || doorway.position != POSITION || !canTravel(plr)) return
        if (plr.position.y <= POSITION.y) {
            if (plr.position.isWithinDistance(SOUTH, 3)) plr.move(NORTH)
            return
        }
        if (!plr.position.isWithinDistance(NORTH, 3)) return
        val guard = world.locator.findNpcs(NORTH, 5) { canEnter(plr, it) }.firstOrNull() ?: return
        enterDesert(plr, guard)
    }

    /**
     * Displays the owned ticket and consumes it only when the nearby passenger completes the conversation.
     */
    fun passGate(plr: Player, npc: Npc) {
        if (!canEnter(plr, npc)) return
        val attempt = Any()
        plr.admission = attempt
        passGate(plr, npc, attempt)
    }

    /**
     * Retains the warning's permission while the ticket display and final player line are continued.
     */
    private fun passGate(plr: Player, npc: Npc, attempt: Any) {
        if (plr.admission !== attempt || !canEnter(plr, npc)) return
        var completed = false
        plr.newDialogue()
            .npc(npc.id, "Can I see your Shantay Desert Pass please.")
            .give(Item(PASS), "You hand over a Shantay Pass.", false)
            .player("Sure, here you go!")
            .then {
                if (!completed) {
                    completed = true
                    val ticket = Item(PASS)
                    if (it.admission === attempt && canEnter(it, npc) && it.inventory.contains(ticket) && it.inventory.remove(ticket)) {
                        it.admission = null
                        it.move(SOUTH)
                        it.sendMessage("You go through the gate.")
                        if (!it.inventory.contains(DISCLAIMER)) {
                            it.giveItem(Item(DISCLAIMER))
                            it.newDialogue()
                                .npc(npc.id, "Here, have a disclaimer... It means that Shantay isn't",
                                    "responsible if you die in the desert.")
                                .open()
                        }
                    }
                }
            }
            .open()
    }

    /**
     * Preserves the guard's ticket refusal and first-visit danger acknowledgement.
     */
    fun enterDesert(plr: Player, npc: Npc) {
        if (!canEnter(plr, npc)) return
        val attempt = Any()
        plr.admission = attempt
        var offered = false
        plr.newDialogue()
            .player("I'd like to go into the desert please.")
            .then {
                if (!offered && it.admission === attempt && canEnter(it, npc)) {
                    offered = true
                    if (!it.inventory.contains(PASS)) {
                        it.newDialogue()
                            .npc(npc.id, "You need a Shantay pass to get through this gate. See",
                                "Shantay, he will sell you one for a very reasonable", "price.")
                            .open()
                    } else if (it.inventory.contains(DISCLAIMER)) {
                        passGate(it, npc, attempt)
                    } else {
                        var answered = false
                        it.newDialogue()
                            .text("There is a large poster on the wall near the gateway.", "It reads:")
                            .text("The Desert is a VERY Dangerous place. Do not enter if",
                                "you are scared of dying. Beware of high temperatures,", "sand storms, robbers, and slavers.")
                            .text("That seems pretty scary!", "Are you sure you want to go through?")
                            .options(AGREE, { passenger ->
                                if (!answered) {
                                    answered = true
                                    passGate(passenger, npc, attempt)
                                }
                            }, "No, I'm having serious second thoughts now.", { passenger ->
                                if (!answered) {
                                    answered = true
                                    cancelAdmission(passenger, attempt)
                                    passenger.overlays.closeWindows()
                                }
                            })
                            .title("Go into Desert?")
                            .open()
                    }
                }
            }
            .open()
    }

    /**
     * Rechecks delayed callbacks against both the passenger and the actual north-side gate guard.
     */
    private fun canEnter(plr: Player, npc: Npc): Boolean = canTravel(plr) && plr.position.y > POSITION.y &&
        plr.position.isWithinDistance(NORTH, 3) && npc.id == GUARD && npc.state == EntityState.ACTIVE &&
        npc.position.isWithinDistance(NORTH, 5) && plr.position.isWithinDistance(npc.position, 3)

    /**
     * Prevents a doorway or delayed dialogue from moving a dead, locked or disconnected player.
     */
    internal fun canTravel(plr: Player): Boolean = plr.state == EntityState.ACTIVE && plr.isAlive && !plr.isLocked &&
        plr.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }
}
