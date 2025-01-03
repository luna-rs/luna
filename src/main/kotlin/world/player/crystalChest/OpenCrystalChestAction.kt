package world.player.crystalChest

import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.action.RepeatingAction
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import world.player.Animations

/**
 * A [RepeatingAction] that opens and loots a crystal chest [GameObject].
 *
 * @author lare96
 */
class OpenCrystalChestAction(plr: Player, val gameObject: GameObject, val runOnce: Boolean) : RepeatingAction<Player>(plr, true, 2) {

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

    override fun ignoreIf(other: Action<*>?): Boolean = when (other) {
        is OpenCrystalChestAction -> true
        else -> false
    }

    override fun start(): Boolean {
        mob.interact(gameObject)
        return true
    }

    override fun stop() {
        mob.walking.isLocked = false
    }

    override fun repeat() {
        when (state) {
            State.OPENING -> openChest()
            State.SEARCHING -> searchChest()
        }
    }

    /**
     * Replaces the chest object for [mob] and removes the key from the inventory.
     */
    private fun openChest() {
        if (mob.inventory.remove(989)) {
            mob.sendMessage("You unlock the chest with your key.")
            mob.animation(Animations.PICKPOCKET)
            world.addObject(173, gameObject.position, gameObject.objectType, gameObject.direction, mob)
            mob.walking.isLocked = true
            state = State.SEARCHING
        } else {
            interrupt()
            mob.sendMessage("You do not have any keys to open this chest with.")
        }
    }

    /**
     * Closes the chest object for [mob] and adds chest loot to inventory.
     */
    private fun searchChest() {
        CrystalChestDropTable.roll(mob, gameObject).forEach(mob::giveItem)
        mob.sendMessage("You find some treasure in the chest!")
        world.addObject(172, gameObject.position, gameObject.objectType, gameObject.direction, mob)
        mob.walking.isLocked = false
        state = State.OPENING
        if(runOnce) {
            interrupt()
        }
    }
}