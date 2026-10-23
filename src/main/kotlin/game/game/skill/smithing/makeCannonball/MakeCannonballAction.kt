package game.skill.smithing.makeCannonball

import api.predef.*
import api.predef.ext.*
import game.player.Animations
import game.player.Sound
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * Pours one steel bar into a reusable ammo mould through the existing locked furnace action.
 *
 * Current Smithing level, mould, steel bar, and living/active state are validated before every stage
 * and during queue processing. Losing a prerequisite interrupts the action and uses [LockedAction]
 * cleanup to release the player lock. Existing heating, pouring, and cooling delays remain unchanged.
 *
 * Completion checks output capacity, including stack overflow, before consuming the bar. One removed
 * bar frees the slot needed for a new cannonball stack in a full inventory. If addition fails without
 * storing output, the bar is restored to its original slot. Experience and the completion message
 * require successful storage of all four cannonballs.
 *
 * @param plr The player making cannonballs.
 *
 * @author TheLining
 * @author lare96
 */
class MakeCannonballAction(plr: Player) : LockedAction(plr, true, 1) {

    companion object {

        /**
         * The steel bar item ID.
         */
        const val STEEL_BAR = 2353

        /**
         * The ammo mould item ID.
         */
        const val AMMO_MOULD = 4

        /**
         * The cannonball item ID.
         */
        const val CANNONBALL = 2

        /**
         * The Smithing level required.
         */
        const val LEVEL = 35

        /**
         * The cannonballs made from one steel bar.
         */
        const val AMOUNT = 4

        /**
         * The Smithing experience for one steel bar.
         */
        const val EXPERIENCE = 25.6

        /**
         * The animation for pouring and removing the cannonballs.
         */
        private val POUR_ANIMATION = Animation(827)
    }

    /** Live prerequisites shared by queue processing and each timed action stage. */
    private fun canContinue(): Boolean = mob.state == EntityState.ACTIVE && mob.health > 0 &&
        mob.smithing.level >= LEVEL && mob.inventory.containsAll(STEEL_BAR, AMMO_MOULD)

    override fun onProcess() { if (!canContinue()) interrupt() }

    override fun run(): Boolean {
        if (!canContinue()) return true
        when (executions) {
            0 -> {
                mob.sendMessage("You heat the steel bar into a liquid state.")
                mob.animation(Animations.SMELT)
                mob.playSound(Sound.FURNACE)
                delay = 5
            }

            1 -> {
                mob.sendMessage("You pour the molten metal into your cannonball mould.")
                mob.animation(POUR_ANIMATION)
                delay = 1
            }

            2 -> {
                mob.sendMessage("The molten metal cools slowly to form $AMOUNT cannonballs.")
                delay = 3
            }

            else -> {
                completeProduction()
                return true
            }
        }
        return false
    }

    /** Stores four cannonballs before awarding experience, restoring an unconverted bar if addition fails. */
    private fun completeProduction() {
        val inventory = mob.inventory
        val product = Item(CANNONBALL, AMOUNT)
        if (inventory.computeSpaceFor(product) > inventory.computeRemainingSize() + 1) return
        val index = inventory.computeIndexForId(STEEL_BAR)
        val bar = inventory.get(index)
        val previousAmount = inventory.computeAmountForId(CANNONBALL)
        if (!inventory.remove(index, bar)) return
        var stored = false
        try {
            stored = inventory.add(product)
        } finally {
            if (!stored && inventory.computeAmountForId(CANNONBALL) == previousAmount) inventory.add(index, bar)
        }
        if (!stored) return
        mob.animation(POUR_ANIMATION)
        mob.sendMessage("You remove the cannonballs from the mould.")
        mob.smithing.addExperience(EXPERIENCE)
    }

}
