package api.bot.zone

import api.attr.Attr
import api.bot.Suspendable.waitFor
import api.bot.action.BotActionHandler
import api.predef.*
import game.skill.magic.ItemRequirement
import game.skill.magic.RuneRequirement
import game.skill.magic.teleportSpells.TeleportSpell
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot

/**
 * A [TravelStrategy] that uses a teleport spell before walking to the final destination.
 *
 * This strategy checks that the bot has the correct Magic level, is using the correct spellbook, and can supply all
 * item or rune requirements for the configured teleport spell. When travelling, it retrieves any required items, casts
 * the teleport, waits for the position change, then delegates the remaining path to [WalkingTravelStrategy].
 *
 * @property spell The teleport spell used as the initial travel method.
 *
 * @author lare96
 */
class TeleportTravelStrategy(private val spell: TeleportSpell) : TravelStrategy {

    companion object {

        /**
         * The temporary list of item and rune requirements needed for the bot's pending teleport.
         *
         * This list is rebuilt during [canTravel] and reused by [travel] so the strategy does not need to resolve spell
         * requirements twice.
         */
        private val Bot.teleportItems by Attr.list<Item>()
    }

    override fun canTravel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean {
        // The bot must meet the spell level requirement and be on the spellbook that owns this teleport.
        if (bot.magic.staticLevel < spell.level || bot.spellbook != spell.style.spellbook) {
            return false
        }

        // Rebuild the requirement list for this check, since the same bot attribute may have been used before.
        bot.teleportItems.clear()

        // Convert the spell's supported requirement types into concrete item stacks that the handler can check.
        for (req in spell.requirements) {
            if (req is ItemRequirement) {
                bot.teleportItems.add(Item(req.id, req.amount))
            } else if (req is RuneRequirement) {
                bot.teleportItems.add(Item(req.rune.id, req.amount))
            }
        }

        // The bot can only use this strategy if every required item or rune is currently available.
        return handler.hasAll(bot.teleportItems)
    }

    override suspend fun travel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean {
        val count = bot.teleportItems.size

        // Pull the teleport requirements into the bot's inventory before casting.
        if (count > 0 && !handler.retrieveAll(bot.teleportItems)) {
            bot.log("Cannot teleport: missing requirements.")
            return false
        }

        // Cast the teleport and wait until the bot's position changes, which signals that the teleport completed.
        val prev = bot.position
        bot.output.clickButton(spell.button)
        waitFor { prev != bot.position }

        // Finish the route by walking from the teleport landing area to the requested destination.
        return WalkingTravelStrategy.travel(bot, handler, dest)
    }
}