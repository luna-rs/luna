package world.player.skill.magic.bonesToItems

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import io.luna.game.action.impl.QueuedAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import world.player.Animations
import world.player.Sounds
import world.player.skill.magic.Magic

/**
 * A [QueuedAction] that converts bones into either bananas or peaches.
 *
 * @author lare96
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
                return
            }
            mob.lock()
            mob.playSound(Sounds.BONES_TO_ITEMS)
            world.scheduleOnce(1) {
                mob.inventory.removeAll(removeItems)
                mob.inventory.replaceAll(BONES, type.id)
                mob.animation(Animations.BONES_TO_ITEMS)
                mob.graphic(Graphic(141, 100))
                mob.magic.addExperience(type.xp)
                mob.unlock()
            }
        }
    }
}