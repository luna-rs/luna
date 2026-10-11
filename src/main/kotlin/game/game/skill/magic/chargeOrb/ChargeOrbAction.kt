package game.skill.magic.chargeOrb

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import game.player.Animations
import game.skill.magic.Magic
import io.luna.game.action.impl.QueuedAction
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

/**
 * Charges an orb through the existing queued spell action and one-tick completion delay.
 *
 * Requirements are checked both before casting and at completion, including staff and combination-rune
 * substitutions. Normal casts replace the unpowered orb in its existing slot before consuming the verified
 * rune costs, so a full inventory cannot lose the product. Administrator/beta requirement bypasses retain
 * their existing free-cast behavior, but still require output space. Inactive or dead players cannot complete
 * the delayed cast, and the cast lock is released even when completion fails.
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
        if (Magic.checkRequirements(mob, type.level, type.requirements) != null) {
            mob.lock()
            mob.playSound(type.sound)
            world.scheduleOnce(1) {
                try {
                    if (mob.state == EntityState.ACTIVE && mob.health > 0) completeCast()
                } finally {
                    mob.unlock()
                }
            }
        }
    }

    /** Revalidates current spell costs and awards experience only after the product is stored successfully. */
    private fun completeCast() {
        val removeItems = Magic.checkRequirements(mob, type.level, type.requirements) ?: return
        if (!mob.inventory.containsAll(removeItems)) return
        if (removeItems.any { it.id == UNPOWERED_ORB }) {
            if (!mob.inventory.replace(UNPOWERED_ORB, type.chargedOrb)) return
            mob.inventory.removeAll(removeItems.filterNot { it.id == UNPOWERED_ORB })
        } else if (!mob.inventory.add(Item(type.chargedOrb))) {
            return
        }
        mob.magic.addExperience(type.xp)
        mob.animation(Animations.CHARGE_ORB)
        mob.graphic(Graphic(type.graphic, 100))
        mob.sendMessage("You charge the orb and place it in your inventory.")
        mob.tabs.show(TabIndex.MAGIC)
    }
}
