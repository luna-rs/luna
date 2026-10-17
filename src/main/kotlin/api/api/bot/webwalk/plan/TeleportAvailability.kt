package api.bot.webwalk.plan

import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.model.TeleportKind
import api.predef.*
import api.predef.ext.*
import game.item.degradable.jewellery.TeleportJewellery
import game.skill.magic.EquipmentRequirement
import game.skill.magic.ItemRequirement
import game.skill.magic.Rune
import game.skill.magic.RuneRequirement
import game.skill.magic.Staff
import game.skill.magic.teleportSpells.TeleportSpell
import io.luna.game.model.mob.bot.Bot

/**
 * Finds the teleports that a bot can use right now.
 *
 * It follows the rules of the game without using the game's own checks, which tell the player off when they fail. In
 * short, a bot can use:
 *
 * - a teleport spell if it is on the right spellbook and has the level and what the spell needs. What the spell needs is
 *   its runes (a staff stands in for the runes it represents) and other items in the inventory, and equipment. Combination
 *   runes are not counted, so a bot that only has those is thought unable to cast, which is the safe way to be wrong. The
 *   requirements are enforced in development mode too, although the game itself lets anyone cast anything for free there,
 *   so that bots plan the way they would on a real server.
 * - a teleport by jewellery if it carries a piece with a charge left, in its inventory or equipped.
 * - the home teleport always.
 *
 * Nothing can be used while teleblocked. Where a teleport can be used from, such as not deep in the wilderness, is the
 * requirements of its definition, which the planner checks.
 *
 * @author Hydrozoa
 */
object TeleportAvailability {

    /**
     * Finds the ids of the teleports that a bot can use. Must be called on the game thread, because it reads the bot as it is.
     *
     * @param bot The bot.
     * @param teleports The teleports to choose from.
     * @return The ids of the ones the bot can use.
     */
    fun usable(bot: Bot, teleports: List<TeleportDefinition>): Set<String> {
        if (teleports.isEmpty() || bot.status.isTeleBlocked()) {
            return emptySet()
        }
        val usable = HashSet<String>()
        for (teleport in teleports) {
            val can = when (teleport.kind) {
                TeleportKind.SPELL -> canCast(bot, teleport.key)
                TeleportKind.JEWELLERY -> canUseJewellery(bot, teleport.key)
                TeleportKind.HOME -> true
            }
            if (can) {
                usable += teleport.id
            }
        }
        return usable
    }

    /**
     * Determines if a bot can cast a teleport spell.
     */
    private fun canCast(bot: Bot, key: String?): Boolean {
        val spell = TeleportSpell.entries.firstOrNull { it.name == key } ?: return false
        if (bot.spellbook != spell.style.spellbook) {
            return false
        }
        if (bot.magic.level < spell.level) {
            return false
        }
        val runes = ArrayList<Pair<Rune, Int>>()
        for (requirement in spell.requirements) {
            when (requirement) {
                is RuneRequirement -> runes += Pair(requirement.rune, requirement.amount)
                is ItemRequirement -> if (bot.inventory.computeAmountForId(requirement.id) < requirement.amount) {
                    return false
                }

                is EquipmentRequirement -> if (!bot.equipment.contains(requirement.id)) {
                    return false
                }
            }
        }
        val staff = bot.equipment.weapon?.id?.let { Staff.ID_TO_STAFF[it] }?.represents ?: emptySet()
        return hasRunes(runes, { bot.inventory.computeAmountForId(it) }, staff)
    }

    /**
     * Determines if there are enough runes.
     *
     * @param required The runes needed and how many of each.
     * @param held Gives how many of the item with an id are held.
     * @param covered The runes that a staff stands in for, which are not needed.
     * @return `true` if every rune that is not covered is held in the amount needed.
     */
    fun hasRunes(required: List<Pair<Rune, Int>>, held: (Int) -> Int, covered: Set<Rune>): Boolean {
        val needed = HashMap<Rune, Int>()
        for ((rune, amount) in required) {
            if (rune !in covered) {
                needed.merge(rune, amount, Int::plus)
            }
        }
        return needed.all { (rune, amount) -> held(rune.id) >= amount }
    }

    /**
     * Determines if a bot carries a piece of teleport jewellery that has a charge.
     */
    private fun canUseJewellery(bot: Bot, key: String?): Boolean {
        val jewellery = TeleportJewellery.entries.firstOrNull { it.name == key } ?: return false
        return chargedIds(jewellery).any { bot.inventory.contains(it) || bot.equipment.contains(it) }
    }

    /**
     * Finds the ids of the pieces of a jewellery that can be used. The empty piece that jewellery which doesn't crumble
     * ends with isn't one of them.
     *
     * @param jewellery The jewellery.
     * @return The ids, from the one with the most charges to the one with the fewest.
     */
    fun chargedIds(jewellery: TeleportJewellery): List<Int> = jewellery.items
}
