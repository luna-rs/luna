package world.player.skill.magic.bonesToItems

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import io.luna.game.action.QueuedAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import world.player.Animations
import world.player.Sounds
import world.player.skill.magic.Magic

/**
 * A [QueuedAction] that converts bones into either bananas or peaches.
 */
class BonesToItemsAction(plr: Player, val type: BonesToItemsType) :
    QueuedAction<Player>(plr, plr.bonesToItemsDelay, 3) {

    companion object {

        /**
         * The item id of bones.
         */
        val BONES = 526

        /**
         * The attribute representing the time source for an action.
         */
        val Player.bonesToItemsDelay by Attr.timeSource()
    }

    override fun execute() {
        val removeItems = Magic.checkRequirements(mob, type.level, type.requirements)
        if (removeItems != null) {
            val count = mob.inventory.computeAmountForId(BONES)
            if (count == 0) {
                // todo proper message
                mob.sendMessage("You do not have any bones to convert.")
                return
            }
            mob.lock()
            mob.playSound(Sounds.BONES_TO_ITEMS)
            world.scheduleOnce(1) {
                val name = if (type == BonesToItemsType.BANANAS) "bananas" else "peaches"
                mob.inventory.removeAll(removeItems)
                mob.inventory.replaceAll(BONES, type.id)
                mob.animation(Animations.BONES_TO_ITEMS)
                mob.graphic(Graphic(141, 100))
                mob.magic.addExperience(type.xp)
                // todo proper message
                mob.sendMessage("You turn the bones in your inventory into $name.")
                mob.unlock()
            }
        }
    }
}