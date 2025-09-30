package api.bot.zone

import api.attr.Attr
import api.bot.Suspendable.waitFor
import api.bot.action.BotActionHandler
import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import world.player.skill.magic.ItemRequirement
import world.player.skill.magic.RuneRequirement
import world.player.skill.magic.teleportSpells.TeleportSpell

/**
 * A [TravelStrategy] implementation that forces a [Bot] to teleport to its destination.
 *
 * @author lare96
 */
class TeleportTravelStrategy(private val spell: TeleportSpell, private val walking: Boolean = false) : TravelStrategy {

    companion object {

        /**
         * An attribute containing our items to look for before travelling.
         */
        private val Bot.teleportItems by Attr.list<Item>()
    }

    override fun canTravel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean {
        if (bot.magic.staticLevel >= spell.level || bot.spellbook != spell.style.spellbook) {
            return false
        }
        bot.teleportItems.clear()
        for (req in spell.requirements) {
            if (req is ItemRequirement) {
                bot.teleportItems.add(Item(req.id, req.amount))
            } else if (req is RuneRequirement) {
                bot.teleportItems.add(Item(req.rune.id, req.amount))
            }
        }
        return handler.hasAll(bot.teleportItems)
    }

    override suspend fun travel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean {
        val count = bot.teleportItems.size
        if (count > 0) {
            if (handler.retrieveAll(bot.teleportItems)) {
                val prev = bot.position
                bot.output.clickButton(spell.button)
                waitFor { prev != bot.position }
                return if (dest.isViewable(dest)) true else WalkingTravelStrategy.travel(bot, handler, dest)
            }
        }
        return false
    }
}