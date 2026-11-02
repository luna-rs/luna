package game.content.shiloFurnace

import api.predef.*
import game.obj.doors.DoorPassageAction
import game.obj.doors.DoorType
import game.obj.doors.Doors
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import java.util.concurrent.CompletableFuture

/**
 * Yohnus's paid furnace admission from Lost City's area_shilo/yohnus script.
 * Each entry costs twenty coins; leaving is free. NPC and door clicks offer the same native conversation.
 * The accepted passenger walks to the source's outside door tile before the ordinary three-tick passage.
 * The fee is committed once the successful passage queues its step, with no deposit or permanent unlock.
 *
 * @author lare96
 */
object ShiloFurnace {

    const val DOOR = 2266
    const val YOHNUS = 513
    const val FARE = 20
    const val ACCEPT = "Use Furnace - 20 Gold"
    val POSITION = Position(2856, 2963)
    private val TYPE = DoorType(DOOR, 1532, null, null, null, 3)

    /**
     * Finds the real owner near the furnace; no substitute NPC or direct placement is used.
     */
    fun useDoor(player: Player, door: GameObject) {
        if (door.id != DOOR || door.position != POSITION) return
        if (!Doors.isEntering(door.position, door.direction, player.position)) {
            player.submitAction(Passage(player, door, false))
            return
        }
        val owner = world.locator.findNpcs(player.position, 5) { it.id == YOHNUS && it.state == EntityState.ACTIVE }
            .firstOrNull() ?: return
        offer(player, owner)
    }

    /**
     * Quotes the source fee and binds acceptance to one nearby, living native furnace owner.
     */
    fun offer(player: Player, owner: Npc) {
        if (!canTalk(player, owner)) return
        var accepted = false
        player.newDialogue().npc(owner.id, "Sorry but the blacksmiths is closed.",
            "But I can let you use the furnace at the cost", "of 20 gold pieces.")
            .options(ACCEPT, { passenger ->
                if (!accepted) {
                    accepted = true
                    if (canTalk(passenger, owner)) {
                        if (passenger.inventory.contains(Item(995, FARE))) {
                            passenger.overlays.closeWindows()
                            passenger.submitAction(Admission(passenger))
                        } else passenger.newDialogue().npc(owner.id,
                            "Sorry Bwana, it seems that you are short of funds.").open()
                    }
                }
            }, "No thanks!", { it.overlays.closeWindows() }).open()
    }

    private fun canTravel(player: Player): Boolean = player.state == EntityState.ACTIVE && player.isAlive &&
        !player.isLocked && player.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

    private fun canTalk(player: Player, owner: Npc): Boolean = canTravel(player) && owner.id == YOHNUS &&
        owner.state == EntityState.ACTIVE && owner.isAlive && owner.position.isWithinDistance(POSITION, 5) &&
        player.position.isWithinDistance(owner.position, 5)

    /**
     * Retains normal navigation and weak-action cancellation while aligning a paid NPC conversation with the door.
     *
     * @author lare96
     */
    private class Admission(player: Player) : Action<Player>(player, ActionType.WEAK) {
        private var walking: CompletableFuture<NavigationResult>? = null
        private var ticks = 0

        override fun run(): Boolean {
            if (!canTravel(mob) || ++ticks > 30 || !mob.inventory.contains(Item(995, FARE))) return true
            if (mob.position != POSITION) {
                val pending = walking
                if (pending == null) walking = mob.navigator.navigate(POSITION, false)
                else if (pending.isDone) return true
                return false
            }
            val door = world.objects.findAll(POSITION).filter { it.id == DOOR }.findFirst().orElse(null) ?: return true
            mob.submitAction(Passage(mob, door, true))
            return true
        }
    }

    /**
     * Rechecks admission at the actual crossing and charges only the successfully queued entry step.
     * The inventory check and passage run synchronously on the game thread.
     *
     * @author lare96
     */
    private class Passage(player: Player, door: GameObject, private val admitted: Boolean) :
        DoorPassageAction(player, door, TYPE) {

        override fun checkAccess(entering: Boolean): Boolean = canTravel(mob) &&
            (!entering || admitted && mob.inventory.contains(Item(995, FARE)))

        override fun onPassage(entering: Boolean) {
            if (entering) {
                check(mob.inventory.remove(Item(995, FARE))) { "The verified furnace fare changed during its passage." }
                mob.sendMessage("Thanks Bwana! Enjoy the facilities!")
            }
        }
    }
}
