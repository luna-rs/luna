package game.skill.magic.superheatItem

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import game.content.randomEvents.RandomEvents
import game.player.Animations
import game.player.Sound
import game.skill.magic.Magic
import game.skill.magic.Rune
import game.skill.magic.RuneRequirement
import game.skill.smithing.BarType
import io.luna.game.action.impl.QueuedAction
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

/**
 * Superheats owned ores through the existing queued spell and two-tick completion delay.
 *
 * The clicked ore, chosen recipe, Smithing level, and current rune costs are rechecked at completion.
 * Steel takes precedence over iron when both iron ore and two coal are present. The selected recipe
 * remains fixed during the delay, so losing coal cannot silently downgrade a steel cast to iron.
 *
 * Replacing the primary ore in place preserves the product slot even in a full inventory. Secondary
 * ores and current rune costs are consumed only after successful replacement. Beta/administrator
 * spell-cost bypasses still require the recipe's ores and Smithing level. Inactive or dead players
 * cannot complete the cast; its lock is released even when completion throws.
 * Successful conversions offer Lost City's general random-event pool one tick later, after
 * the conversion lock has been released. Failed or interrupted casts do not create an offer.
 *
 * @param plr The player casting Superheat Item.
 * @param index Inventory slot of the ore targeted by the existing spell interaction.
 *
 * @author lare96
 */
class SuperheatItemAction(plr: Player, private val index: Int) : QueuedAction<Player>(plr, plr.superheatDelay, 5) {
    companion object {

        /**
         * Existing spellbook identifier shared by the player handler and bot interaction.
         */
        const val SPELL_ID = 1173

        /**
         * The amount of magic XP to give.
         */
        private const val XP = 53.0

        /**
         * The magic level required.
         */
        const val LEVEL = 43

        /**
         * The runes required.
         */
        val RUNES = listOf(
            RuneRequirement(Rune.FIRE, 4),
            RuneRequirement(Rune.NATURE, 1)
        )

        /**
         * The time source attribute for superheating.
         */
        val Player.superheatDelay by Attr.timeSource()
    }

    override fun execute() {
        if (index !in 0 until mob.inventory.capacity() ||
            mob.state != EntityState.ACTIVE || mob.health < 1) return
        if (Magic.checkRequirements(mob, LEVEL, RUNES) == null) return
        val barType = computeBarType() ?: return
        val targetId = mob.inventory.computeIdForIndex(index)
        mob.lock()
        world.scheduleOnce(2) {
            try {
                if (mob.state == EntityState.ACTIVE && mob.health > 0) completeCast(barType, targetId)
            } finally {
                mob.unlock()
            }
        }
    }

    /**
     * Revalidates the original recipe and live spell costs before converting ore in its existing slot.
     */
    private fun completeCast(barType: BarType, targetId: Int) {
        if (mob.inventory.computeIdForIndex(index) != targetId || mob.smithing.level < barType.level ||
            !mob.inventory.containsAll(barType.oreList)) return
        val runeCosts = Magic.checkRequirements(mob, LEVEL, RUNES) ?: return
        if (!mob.inventory.containsAll(runeCosts)) return
        if (!mob.inventory.replace(barType.oreRequired.first.id, barType.id)) return
        mob.inventory.removeAll(listOfNotNull(barType.oreRequired.second) + runeCosts)
        mob.magic.addExperience(XP)
        mob.animation(Animations.SUPERHEAT)
        mob.graphic(Graphic(148, 100))
        mob.playSound(Sound.SUPERHEAT_ALL)
        mob.smithing.addExperience(barType.xp)
        mob.tabs.show(TabIndex.MAGIC)
        world.scheduleOnce(1) { RandomEvents.offerGeneral(mob) }
    }

    /**
     * Selects a complete ore recipe from the clicked item, preferring steel when its coal is present.
     * Other overlapping ore candidates retain their existing table order. Smithing eligibility is checked
     * after selection, so an under-level player cannot bypass the steel requirement by making iron instead.
     */
    private fun computeBarType(): BarType? {
        val itemId = mob.inventory.computeIdForIndex(index)
        val possibleBars = BarType.ORE_TO_BAR[itemId]
        if (possibleBars.isEmpty()) {
            mob.playSound(Sound.SUPERHEAT_FAIL)
            mob.graphic(Graphic(85, 100))
            mob.sendMessage("You cannot use this spell on this item.")
            return null
        }
        val barType = if (BarType.STEEL in possibleBars && mob.inventory.containsAll(BarType.STEEL.oreList)) {
            BarType.STEEL
        } else {
            possibleBars.firstOrNull { mob.inventory.containsAll(it.oreList) }
        }
        if (barType == null) {
            mob.sendMessage("You do not have all the required ores to superheat this.")
            return null
        }
        if (mob.smithing.level < barType.level) {
            mob.sendMessage("Your Smithing level is not high enough to superheat this.")
            return null
        }
        return barType
    }
}
