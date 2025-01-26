package world.player.skill.magic.superheatItem

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import io.luna.game.action.QueuedAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.inter.GameTabSet.TabIndex
import world.player.Animations
import world.player.Sounds
import world.player.skill.magic.Magic
import world.player.skill.magic.Rune
import world.player.skill.magic.RuneRequirement
import world.player.skill.smithing.BarType

/**
 * A [QueuedAction] that handles the process of players doing low and high alchemy.
 */
class SuperheatItemAction(plr: Player, private val index: Int) : QueuedAction<Player>(plr, plr.superheatDelay, 5) {

    companion object {

        /**
         * The amount of magic XP to give.
         */
        private const val XP = 53.0

        /**
         * The magic level required.
         */
        private const val LEVEL = 43

        /**
         * The runes required.
         */
        private val RUNES = listOf(
            RuneRequirement(Rune.FIRE, 4),
            RuneRequirement(Rune.NATURE, 1)
        )

        /**
         * The time source attribute for superheating.
         */
        val Player.superheatDelay by Attr.timeSource()
    }

    override fun execute() {
        val removeItems = Magic.checkRequirements(mob, LEVEL, RUNES)
        if (removeItems != null) {
            val barType = computeBarType()
            if (barType != null) {
                mob.lock()
                mob.playSound(Sounds.SUPERHEAT)
                world.scheduleOnce(2) {
                    mob.inventory.removeAll(barType.oreRequired)
                    mob.inventory.removeAll(removeItems)
                    mob.inventory.add(Item(barType.id))
                    mob.magic.addExperience(XP)
                    mob.animation(Animations.SUPERHEAT)
                    mob.graphic(Graphic(148, 100))
                    mob.smithing.addExperience(barType.xp)
                    // todo proper message
                    mob.sendMessage("You create a ${itemName(barType.id).toLowerCase()}.");
                    mob.tabs.show(TabIndex.MAGIC)
                    mob.unlock()
                }
            }
        }
    }

    /**
     * Computes the bar type that will be used to superheat.
     */
    private fun computeBarType(): BarType? {
        val itemId = mob.inventory.computeIdForIndex(index).orElse(-1)
        val possibleBars = BarType.ORE_TO_BAR[itemId]
        if (possibleBars.isEmpty()) {
            // todo proper message
            mob.sendMessage("You cannot use this spell on this item.")
            return null
        }
        val barType = run {
            for (bar in possibleBars) {
                if (mob.inventory.containsAll(bar.oreRequired)) {
                    return@run bar
                }
            }
            return@run null
        }
        if (barType == null) {
            // todo proper message
            mob.sendMessage("You do not have all the required ores to superheat this.")
            return null
        }
        if (mob.smithing.level < barType.level) {
            // todo proper message
            mob.sendMessage("You do not have the required level to superheat this.")
            return null
        }
        return barType
    }
}