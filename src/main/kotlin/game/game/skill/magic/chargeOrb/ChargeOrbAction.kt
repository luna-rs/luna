package game.skill.magic.chargeOrb

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import io.luna.game.action.impl.QueuedAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import game.player.Animations
import game.skill.magic.Magic

/**
 * A [QueuedAction] handles charging orbs.
 *
 * @author lare96
 */
class ChargeOrbAction(plr: Player, val type: ChargeOrbType) : QueuedAction<Player>(plr, plr.chargeOrbDelay, 5) {

    companion object {

        /**
         * The unpowered orb item id.
         */
        const val UNPOWERED_ORB = 567

        /**
         * The time source attribute for charging orbs.
         */
        val Player.chargeOrbDelay by Attr.timeSource()
    }

    override fun execute() {
        val removeItems = Magic.checkRequirements(mob, type.level, type.requirements)
        if (removeItems != null) {
            mob.lock()
            mob.playSound(type.sound)
            world.scheduleOnce(1) {
                mob.inventory.add(Item(type.chargedOrb))
                mob.inventory.removeAll(removeItems)
                mob.magic.addExperience(type.xp)
                mob.animation(Animations.CHARGE_ORB)
                mob.graphic(Graphic(type.graphic, 100))
                mob.sendMessage("You charge the orb and place it in your inventory.");
                mob.tabs.show(TabIndex.MAGIC)
                mob.unlock()
            }
        }
    }
}