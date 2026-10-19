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
 * The runes and jewellery in the bank of a bot only count when its [BankItems] are given, as if they were carried, after
 * what is carried. A bot has to withdraw them before it can use them, which the web-walker does on a trip that needs them
 * (see [api.bot.webwalk.walk.WebWalker.planTrip]).
 *
 * @author Hydrozoa
 */
object TeleportAvailability {

    /**
     * Finds the ids of the teleports that a bot can use. Must be called on the game thread, because it reads the bot as it is.
     *
     * @param bot The bot.
     * @param teleports The teleports to choose from.
     * @param bank The runes and jewellery in the bank of the bot, which count too, or `null` for what it carries only.
     * @return The ids of the ones the bot can use.
     */
    fun usable(bot: Bot, teleports: List<TeleportDefinition>, bank: BankItems? = null): Set<String> {
        if (teleports.isEmpty() || bot.status.isTeleBlocked()) {
            return emptySet()
        }
        val charges = charges(bot, bank)
        val usable = HashSet<String>()
        for (teleport in teleports) {
            val can = when (teleport.kind) {
                TeleportKind.SPELL -> canCast(bot, teleport.key, bank)
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
    private fun canCast(bot: Bot, key: String?, bank: BankItems?): Boolean {
        val spell = TeleportSpell.named(key) ?: return false
        return bot.spellbook == spell.style.spellbook && cost(bot, spell, bank) is SpellCost.Castable
    }

    /**
     * Works out what casting a teleport spell takes from a bot, with the game's own rules but without telling it anything
     * and without the free casting of beta mode. With the items of its [bank], they are used after the inventory, as if
     * they had been withdrawn.
     */
    private fun cost(bot: Bot, spell: TeleportSpell, bank: BankItems?): SpellCost =
        if (bank != null) {
            Magic.spellCost(bot.magic.level, bot.inventory + bank.items, { bot.equipment.contains(it) },
                            bot.equipment.weapon?.id, spell.level, spell.requirements)
        } else {
            Magic.spellCost(bot, spell.level, spell.requirements)
        }

    /**
     * Finds what using a teleport uses up for a bot. Must be called on the game thread, because it reads the bot as it is.
     *
     * A spell uses up what the game takes for it (see [Magic.spellCost]): its runes, apart from those that the staff of the
     * bot stands in for, the combination runes that stand in for some of them, and the other items it needs. Jewellery uses
     * up one charge. The home teleport uses up nothing.
     *
     * @param bot The bot.
     * @param teleport The teleport.
     * @param bank The runes in the bank of the bot, which count too after those in its inventory, or `null` for what it
     * carries only.
     * @return What it uses up, which is nothing for a spell that the bot can't cast.
     */
    fun spend(bot: Bot, teleport: TeleportDefinition, bank: BankItems? = null): Spend = when (teleport.kind) {
        TeleportKind.SPELL -> {
            val spell = TeleportSpell.named(teleport.key)
            val cost = spell?.let { cost(bot, it, bank) } as? SpellCost.Castable
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
     * @param bank The jewellery in the bank of the bot, which counts too, or `null` for what it carries only.
     * @return The charges, keyed by the name of the jewellery, for the jewellery that has any.
     */
    fun charges(bot: Bot, bank: BankItems? = null): Map<String, Int> {
        val charges = HashMap<String, Int>()
        for (jewellery in TeleportJewellery.entries) {
            // Counted as a long, because a bank may hold enough pieces to overflow an int.
            val total = jewellery.items.sumOf { id ->
                val banked = bank?.amount(id)?.toLong() ?: 0L
                (bot.inventory.computeAmountForId(id) + bot.equipment.computeAmountForId(id) + banked) * jewellery.charges(id)
            }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (total > 0) {
                charges[jewellery.name] = total
            }
        }
        return charges
    }
}
