package world.player.crystalChest

import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import world.player.Animations

/**
 * A [RepeatingAction] that opens and loots a crystal chest [GameObject].
 *
 * @author lare96
 */
class OpenCrystalChestAction(plr: Player, val gameObject: GameObject, private val runOnce: Boolean) :
    Action<Player>(plr, ActionType.WEAK, false, 2) {

    /**
     * All possible states this action can be in.
     */
    private enum class State {
        OPENING,
        SEARCHING
    }

    /**
     * The state of this action.
     */
    private var state = State.OPENING

    override fun run(): Boolean {
        if (executions == 0) {
            mob.interact(gameObject)
        }
        return when (state) {
            State.OPENING -> openChest()
            State.SEARCHING -> searchChest()
        }
    }

    override fun onFinished() {
        mob.unlock()
    }

    /**
     * Replaces the chest object for [mob] and removes the key from the inventory.
     */
    private fun openChest(): Boolean {
        if (mob.inventory.contains(989)) {
            mob.lock()
            mob.sendMessage("You unlock the chest with your key.")
            mob.animation(Animations.PICKPOCKET)
            world.addObject(173, gameObject.position, gameObject.objectType, gameObject.direction, mob)
            state = State.SEARCHING
            return false
        } else {
            mob.sendMessage("You do not have any keys to open this chest with.")
            return true
        }
    }

    /**
     * Closes the chest object for [mob] and adds chest loot to inventory.
     */
    private fun searchChest(): Boolean {
        if (mob.inventory.remove(989)) {
            mob.unlock()
            CrystalChestDropTable.roll(mob, gameObject).forEach(mob::giveItem)
            mob.sendMessage("You find some treasure in the chest!")
            world.addObject(172, gameObject.position, gameObject.objectType, gameObject.direction, mob)
            state = State.OPENING
            return runOnce
        }
        mob.sendMessage("You do not have any keys to open this chest with.")
        return true
    }
}