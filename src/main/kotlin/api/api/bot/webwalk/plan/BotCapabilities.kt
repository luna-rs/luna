package api.bot.webwalk.plan

import api.bot.webwalk.model.TeleportDefinition
import engine.controllers.WildernessLocatableController.wildernessLevel
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot

/**
 * Copies what a bot can do into a [CapabilitySnapshot].
 *
 * @author Hydrozoa
 */
object BotCapabilities {

    /**
     * Takes a snapshot of a bot. Must be called on the game thread, because it reads the bot as it is.
     *
     * The items are those in the inventory and equipment of the bot. Its bank is left out, because a trip can't use what
     * a bot has to go and get first.
     *
     * @param bot The bot.
     * @param teleports The teleports that the web has, to find which of them the bot can use right now.
     * @param flags The unlocks of the bot.
     * @return The snapshot.
     */
    fun snapshot(bot: Bot, teleports: List<TeleportDefinition> = emptyList(), flags: Set<String> = emptySet()):
            CapabilitySnapshot {
        val skills = Skill.IDS.associateWith { bot.skills.getSkill(it).staticLevel }
        val items = HashMap<Int, Int>()
        count(bot.inventory, items)
        count(bot.equipment, items)
        return CapabilitySnapshot(skills, items, flags, bot.wildernessLevel, TeleportAvailability.usable(bot, teleports))
    }

    /**
     * Adds the amount of every item of a container to a total.
     */
    private fun count(container: ItemContainer, totals: MutableMap<Int, Int>) {
        for (item in container) {
            if (item != null) {
                totals.merge(item.id, item.amount, Int::plus)
            }
        }
    }
}
