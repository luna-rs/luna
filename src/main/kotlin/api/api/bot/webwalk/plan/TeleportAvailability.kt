package api.bot.webwalk.plan

import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.model.TeleportKind
import api.predef.*
import api.predef.ext.*
import game.item.degradable.jewellery.TeleportJewellery
import game.skill.magic.Magic
import game.skill.magic.SpellCost
import game.skill.magic.teleportSpells.TeleportSpell
import io.luna.game.model.mob.bot.Bot

/**
 * Finds the teleports that a bot can use right now.
 *
 * In short, a bot can use:
 *
 * - a teleport spell if it is on the right spellbook and has the level and what the spell needs, by the game's own rules
 *   (see [Magic.spellCost]). What the spell needs is its runes, for which a staff and combination runes stand in, other
 *   items in the inventory, and equipment. The requirements are enforced in development mode too, although the game itself
 *   lets anyone cast anything for free there, so that bots plan the way they would on a real server.
 * - a teleport by jewellery if it carries a piece with a charge left, in its inventory or equipped (see [charges]).
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
        val charges = charges(bot)
        val usable = HashSet<String>()
        for (teleport in teleports) {
            val can = when (teleport.kind) {
                TeleportKind.SPELL -> canCast(bot, teleport.key)
                TeleportKind.JEWELLERY -> (charges[teleport.key] ?: 0) > 0
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
        val spell = TeleportSpell.named(key) ?: return false
        return bot.spellbook == spell.style.spellbook && cost(bot, spell) is SpellCost.Castable
    }

    /**
     * Works out what casting a teleport spell takes from a bot, with the game's own rules but without telling it anything
     * and without the free casting of beta mode.
     */
    private fun cost(bot: Bot, spell: TeleportSpell): SpellCost = Magic.spellCost(bot, spell.level, spell.requirements)

    /**
     * Finds what using a teleport uses up for a bot. Must be called on the game thread, because it reads the bot as it is.
     *
     * A spell uses up what the game takes for it (see [Magic.spellCost]): its runes, apart from those that the staff of the
     * bot stands in for, the combination runes that stand in for some of them, and the other items it needs. Jewellery uses
     * up one charge. The home teleport uses up nothing.
     *
     * @param bot The bot.
     * @param teleport The teleport.
     * @return What it uses up, which is nothing for a spell that the bot can't cast.
     */
    fun spend(bot: Bot, teleport: TeleportDefinition): Spend = when (teleport.kind) {
        TeleportKind.SPELL -> {
            val spell = TeleportSpell.named(teleport.key)
            val cost = spell?.let { cost(bot, it) } as? SpellCost.Castable
            if (cost == null) Spend.NONE else {
                val items = HashMap<Int, Int>()
                cost.items.forEach { items.merge(it.id, it.amount, Int::plus) }
                Spend(items = items)
            }
        }

        TeleportKind.JEWELLERY -> teleport.key?.let { Spend(charges = mapOf(it to 1)) } ?: Spend.NONE
        TeleportKind.HOME -> Spend.NONE
    }

    /**
     * Counts the charges of teleport jewellery that a bot carries, in its inventory and equipped, with the charges of each
     * piece as the game counts them (see [TeleportJewellery.charges]). Must be called on the game thread, because it reads
     * the bot as it is.
     *
     * @param bot The bot.
     * @return The charges, keyed by the name of the jewellery, for the jewellery that has any.
     */
    fun charges(bot: Bot): Map<String, Int> {
        val charges = HashMap<String, Int>()
        for (jewellery in TeleportJewellery.entries) {
            val total = jewellery.items.sumOf { id ->
                (bot.inventory.computeAmountForId(id) + bot.equipment.computeAmountForId(id)) * jewellery.charges(id)
            }
            if (total > 0) {
                charges[jewellery.name] = total
            }
        }
        return charges
    }
}
