package game.skill.magic.bonesToItems

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import game.player.Animations
import game.player.Sound
import game.skill.magic.Magic
import io.luna.game.action.impl.QueuedAction
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic

/**
 * Converts every carried normal bone into the selected food through the existing queued spell.
 *
 * The bone requirement validates that there is an input; it is converted in place rather than consumed
 * as an additional spell cost. Current level, bones, staff/combination substitutions, and rune supplies
 * are rechecked after the one-tick cast delay. Experience and effects require a successful conversion.
 * Beta/administrator rune bypasses still require bones. Inactive/dead players cannot complete the cast,
 * and the cast lock is released even if completion fails.
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
        if (Magic.checkRequirements(mob, type.level, type.requirements) == null || !mob.inventory.contains(BONES)) return
        mob.lock()
        mob.playSound(Sound.BONES_TO_BANANAS_ALL)
        world.scheduleOnce(1) {
            try {
                if (mob.state == EntityState.ACTIVE && mob.health > 0) completeCast()
            } finally {
                mob.unlock()
            }
        }
    }

    /** Revalidates the delayed conversion, keeping the bone input separate from the actual rune cost. */
    private fun completeCast() {
        val removeItems = Magic.checkRequirements(mob, type.level, type.requirements) ?: return
        if (!mob.inventory.contains(BONES) || !mob.inventory.containsAll(removeItems)) return
        if (mob.inventory.replaceAll(BONES, type.id) < 1) return
        mob.inventory.removeAll(removeItems.filterNot { it.id == BONES })
        mob.animation(Animations.BONES_TO_ITEMS)
        mob.graphic(Graphic(141, 100))
        mob.magic.addExperience(type.xp)
    }
}
