package game.skill.magic.lowHighAlch

import api.bot.isRealBot
import api.predef.*
import api.predef.ext.*
import game.content.randomEvents.RandomEvents
import game.player.Animations
import game.player.Sound
import game.skill.magic.Magic
import game.skill.magic.Magic.castDelay
import game.bot.scripts.skills.AlchemyBotScript
import engine.bot.economy.BotProductionMetrics
import io.luna.game.action.impl.QueuedAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

/**
 * Casts low or high alchemy through the native queued action. Lost City's general random-event
 * offer follows a successful conversion, so the spell's coins, rune costs and experience apply first.
 *
 * @author lare96
 */
class AlchemyAction(plr: Player, private val type: AlchemyType, private val inventoryIndex: Int) :
    QueuedAction<Player>(plr, plr.castDelay, 5) {

    override fun execute() {
        val removeItems = Magic.checkRequirements(mob, type.level, type.requirements)
        if (removeItems != null) {
            var item = mob.inventory[inventoryIndex] ?: return
            item = item.withAmount(1)
            if (item.id == 995) {
                mob.sendMessage("Coins are already made of gold.")
                return
            }
            val lowAlch = type == AlchemyType.LOW
            val itemValue = item.itemDef.value
            val goldAmount = (itemValue * type.valueMultiplier).toInt()
            if (!item.itemDef.isTradeable || itemValue == 0) {
                mob.sendMessage("You cannot use alchemy on that item.")
                return
            }
            val owner = if (mob is Bot && (mob.isRealBot() || mob.isAutomationActive)) {
                val script = mob.scriptStack.current() as? AlchemyBotScript
                if (script?.canCastNative(item.id, type) != true) return
                script
            } else null
            if (!mob.inventory.remove(inventoryIndex, Item(item.id))) return
            mob.inventory.removeAll(removeItems)
            owner?.recordCast(item.id)
            if (mob.isRealBot()) BotProductionMetrics.recordSink(item.id)
            mob.magic.addExperience(type.xp)
            mob.animation(if (lowAlch) Animations.LOW_ALCHEMY else Animations.HIGH_ALCHEMY)
            mob.graphic(if (lowAlch) Graphic(112, 75) else Graphic(113, 75))
            mob.playSound(if (lowAlch) Sound.LOW_ALCHEMY else Sound.HIGH_ALCHEMY)

            mob.inventory.add(Item(995, if (goldAmount == 0) 1 else goldAmount))
            mob.tabs.show(TabIndex.MAGIC)
            RandomEvents.offerGeneral(mob)
        }
    }
}
