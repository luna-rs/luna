package game.skill.smithing.makeCannonball

import api.predef.*
import api.predef.ext.*
import game.player.Animations
import game.player.Sound
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * A [LockedAction] that pours one steel bar into an ammo mould to make cannonballs.
 *
 * @author TheLining
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

    override fun run(): Boolean {
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
                mob.animation(POUR_ANIMATION)
                mob.sendMessage("You remove the cannonballs from the mould.")
                if (mob.inventory.remove(Item(STEEL_BAR))) {
                    mob.inventory.add(Item(CANNONBALL, AMOUNT))
                    mob.smithing.addExperience(EXPERIENCE)
                }
                return true
            }
        }
        return false
    }
}
