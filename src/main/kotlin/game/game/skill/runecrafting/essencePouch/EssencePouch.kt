package game.skill.runecrafting.essencePouch

import api.attr.Attr
import api.predef.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.attr.Attribute

/**
 * The essence pouches dropped in the Abyss. Each one holds rune essence or pure essence, but not both at once. The
 * three larger pouches wear as they are filled, hold less once damaged, and fall apart unless the Dark mage repairs
 * them.
 *
 * What a pouch holds is kept on the player, not the item, since a player can only own one pouch of each size.
 *
 * @author TheLining
 */
enum class EssencePouch(val id: Int,
                        val damagedId: Int?,
                        val level: Int,
                        private val capacities: List<Pair<Int, Int>>) {

    SMALL(id = 5509,
          damagedId = null,
          level = 1,
          capacities = listOf(0 to 3)),
    MEDIUM(id = 5510,
           damagedId = 5511,
           level = 25,
           capacities = listOf(800 to 0, 400 to 3, 0 to 6)),
    LARGE(id = 5512,
          damagedId = 5513,
          level = 50,
          capacities = listOf(1000 to 0, 800 to 3, 600 to 5, 400 to 7, 0 to 9)),
    GIANT(id = 5514,
          damagedId = 5515,
          level = 75,
          capacities = listOf(1200 to 0, 1000 to 3, 800 to 5, 600 to 6, 400 to 7, 300 to 8, 200 to 9, 0 to 12));

    companion object {

        /**
         * The rune essence item id.
         */
        const val RUNE_ESSENCE = 1436

        /**
         * The pure essence item id.
         */
        const val PURE_ESSENCE = 7936

        /**
         * Every pouch id, damaged ones included, mapped to its pouch.
         */
        val ID_TO_POUCH = entries.flatMap { pouch -> pouch.ids.map { it to pouch } }.toMap()

        /**
         * The number words used when checking a pouch.
         */
        private val COUNT_WORDS = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
                                         "ten", "eleven", "twelve")

        /**
         * Returns the next pouch an Abyss creature can drop for [plr], the smallest size they don't own yet, or
         * `null` once they own all four. The pouch starts empty and unworn.
         */
        fun nextDrop(plr: Player): EssencePouch? {
            val pouch = entries.firstOrNull { !it.isOwnedBy(plr) } ?: return null
            pouch.discardContents(plr)
            plr.attributes()[pouch.wear] = 0
            return pouch
        }

        /**
         * Removes every pouch [plr] carries, and the essence in them. Pouches are never kept on death.
         */
        fun loseOnDeath(plr: Player) {
            for (pouch in entries) {
                var lost = false
                for (id in pouch.ids) {
                    while (plr.inventory.remove(Item(id))) {
                        lost = true
                    }
                }
                if (lost) {
                    pouch.discardContents(plr)
                    plr.attributes()[pouch.wear] = 0
                }
            }
        }

        /**
         * Returns `true` if any pouch [plr] owns has wear for the Dark mage to repair.
         */
        fun needsRepair(plr: Player) =
            entries.any { it.degrades && plr.attributes()[it.wear] > 0 && it.isOwnedBy(plr) }

        /**
         * Restores all of [plr]'s pouches to their full size, wherever they are kept.
         */
        fun repairAll(plr: Player) {
            for (pouch in entries) {
                if (pouch.damagedId != null) {
                    plr.attributes()[pouch.wear] = 0
                    plr.inventory.replaceAll(pouch.damagedId, pouch.id)
                    plr.bank.replaceAll(pouch.damagedId, pouch.id)
                }
            }
        }
    }

    /**
     * The pouch's name in messages.
     */
    val displayName = name.lowercase()

    /**
     * This pouch's item ids, damaged one included.
     */
    val ids = listOfNotNull(id, damagedId)

    /**
     * If this pouch wears with use.
     */
    val degrades = damagedId != null

    /**
     * How much essence the pouch holds.
     */
    val essence: Attribute<Int> = Attr.int().persist("${displayName}_pouch_essence")

    /**
     * The id of the essence the pouch holds, or 0 when it's empty.
     */
    val essenceId: Attribute<Int> = Attr.int().persist("${displayName}_pouch_essence_id")

    /**
     * How worn the pouch is. Filling adds 0 to 3 for each essence put in, and repairs reset it.
     */
    val wear: Attribute<Int> = Attr.int().persist("${displayName}_pouch_wear")

    /**
     * Returns how much essence the pouch holds at [wear].
     */
    fun capacity(wear: Int) = capacities.first { wear >= it.first }.second

    /**
     * Returns `true` if [plr] has this pouch in their inventory or bank.
     */
    fun isOwnedBy(plr: Player) = ids.any { plr.inventory.contains(it) || plr.bank.contains(it) }

    /**
     * Loses whatever essence the pouch held.
     */
    fun discardContents(plr: Player) {
        plr.attributes()[essence] = 0
        plr.attributes()[essenceId] = 0
    }

    /**
     * Fills the pouch in inventory slot [index] from [plr]'s inventory. Pure essence is used before rune essence.
     */
    fun fill(plr: Player, index: Int) {
        val inv = plr.inventory
        val pureAmount = inv.computeAmountForId(PURE_ESSENCE)
        val fillId = if (pureAmount > 0) PURE_ESSENCE else RUNE_ESSENCE
        val available = if (pureAmount > 0) pureAmount else inv.computeAmountForId(RUNE_ESSENCE)
        if (available == 0) {
            plr.sendMessage("You do not have any essence to fill your pouch with.")
            return
        }
        if (plr.runecrafting.staticLevel < level) {
            plr.sendMessage("You need level $level Runecrafting to fill a $displayName pouch.")
            return
        }
        val held = plr.attributes()[essence]
        val heldId = plr.attributes()[essenceId]
        if (held > 0 && heldId != fillId) {
            val kind = if (heldId == PURE_ESSENCE) "pure" else "normal"
            plr.sendMessage("The pouch contains $kind essence, so you can fill it only with more $kind essence.")
            return
        }
        val capacity = capacity(plr.attributes()[wear])
        val adding = minOf(available, capacity - held)
        if (adding <= 0) {
            plr.sendMessage("You cannot add any more essence to the pouch.")
            return
        }

        var newCapacity = capacity
        if (degrades) {
            var newWear = plr.attributes()[wear]
            repeat(adding) { newWear += rand(0, 3) }
            plr.attributes()[wear] = newWear
            newCapacity = capacity(newWear)
        }
        if (newCapacity == 0) {
            inv[index] = null
            discardContents(plr)
            plr.attributes()[wear] = 0
            plr.sendMessage("Your pouch has decayed beyond any further use.")
            return
        }

        val stored = minOf(held + adding, newCapacity)
        if (newCapacity < capacity) {
            inv[index] = Item(damagedId!!)
            plr.sendMessage("Your pouch has decayed through use.")
        }
        if (stored > held) {
            inv.remove(Item(fillId, stored - held))
        } else if (stored < held) {
            // A pouch that shrinks below what it held gives the rest back, but only if all of it fits.
            val excess = held - stored
            if (inv.computeRemainingSize() >= excess) {
                inv.add(Item(fillId, excess))
            }
        }
        plr.attributes()[essence] = stored
        plr.attributes()[essenceId] = fillId
        if (stored == newCapacity) {
            plr.sendMessage("Your pouch is full.")
        }
    }

    /**
     * Empties as much of the pouch into [plr]'s inventory as fits.
     */
    fun empty(plr: Player) {
        val held = plr.attributes()[essence]
        if (held == 0) {
            plr.sendMessage("There is no essence in this pouch.")
            return
        }
        val inv = plr.inventory
        val amount = minOf(held, inv.computeRemainingSize())
        if (amount == 0) {
            plr.sendMessage("You do not have any free space in your inventory.")
            return
        }
        inv.add(Item(plr.attributes()[essenceId], amount))
        if (amount == held) {
            discardContents(plr)
            plr.sendMessage("Your pouch has no essence left in it.")
        } else {
            plr.attributes()[essence] = held - amount
        }
    }

    /**
     * Tells [plr] how much essence the pouch holds.
     */
    fun check(plr: Player) {
        val held = plr.attributes()[essence]
        if (held == 0) {
            plr.sendMessage("There is no essence in this pouch.")
            return
        }
        val kind = if (plr.attributes()[essenceId] == PURE_ESSENCE) "pure" else "normal"
        val count = COUNT_WORDS.getOrElse(held - 1) { "many" }
        plr.sendMessage(if (held == 1) "There is one $kind essence in this pouch." else
                            "There are $count $kind essences in this pouch.")
    }
}
