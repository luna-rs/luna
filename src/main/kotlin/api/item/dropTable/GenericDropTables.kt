package api.item.dropTable

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.Entity
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.util.RandomUtils.rollSuccess
import io.luna.util.Rational

/**
 * Provides standardized drop tables for common loot types, heavily inspired by the
 * [OSRS Drop Tables](https://oldschool.runescape.wiki/w/Drop_table). Designed to reduce boilerplate.
 *
 * Includes gem, rare, seed, and herb tables with special handling for Ring of Wealth (RoW) bonuses.
 *
 * @author lare96
 */
object GenericDropTables {

    /**
     * The gem drop table. May optionally roll on [megaRareDropTable].
     * If [rowBonus] is enabled or the player wears a Ring of Wealth (ID 2572), empty slots are removed and
     * drop chances are improved.
     *
     * @param chance Chance to roll on this table.
     * @param rowBonus Enables RoW effects programmatically.
     */
    fun gemDropTable(chance: Rational = ALWAYS, rowBonus: Boolean = false): DropTable {
        return DropTableHandler.create {
            nothing(1 of 2)
            "Uncut sapphire" x 1 chance (1 of 4)
            "Uncut emerald" x 1 chance (1 of 8)
            "Uncut ruby" x 1 chance (1 of 16)
            "Chaos talisman" x 1 chance (1 of 42)
            "Nature talisman" x 1 chance (1 of 42)
            "Uncut diamond" x 1 chance (1 of 64)
            "Rune javelin" x 5..15 chance (1 of 128)
            985 x 1 chance (1 of 128) // Crystal key half.
            987 x 1 chance (1 of 128) // Crystal key half.
        }.table {
            object : DropTable() {
                override fun canRollOnTable(mob: Mob?, source: Entity?): Boolean {
                    return rollSuccess(chance)
                }

                override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList {
                    var table: DropTableItemList = items
                    var wearingRow = false
                    if ((mob is Player && mob.equipment.contains(2572)) || rowBonus) {
                        wearingRow = true
                    }

                    // Increased drop rate for mega rare table if wearing RoW.
                    val megaRareChance = if (wearingRow) 1 of 65 else 1 of 128
                    if (rollSuccess(megaRareChance)) {
                        table = megaRareDropTable().computeTable(mob, source)
                    }

                    // Filter empty slots if needed and build the table.
                    return if (wearingRow) table.filterNot { it.isNothing() } else table
                }

                override fun computePossibleItems(): DropTableItemList = items +
                        megaRareDropTable().computePossibleItems()
            }
        }
    }

    /**
     * The rare drop table. Has a chance to roll on [megaRareDropTable] or [gemDropTable], followed by the rare loot list.
     *
     * @param chance Chance to roll on this table.
     * @param rowBonus Enables RoW effects programmatically.
     */
    fun rareDropTable(chance: Rational = ALWAYS, rowBonus: Boolean = false): DropTable {
        return DropTableHandler.create {
            "Nature rune" x 40..70 chance (1 of 42)
            "Adamant javelin" x 10..20 chance (1 of 64)
            "Death rune" x 20..45 chance (1 of 64)
            "Law rune" x 20..45 chance (1 of 64)
            "Rune arrow" x 20..45 chance (1 of 64)
            "Steel arrow" x 100..150 chance (1 of 64)
            "Rune 2h sword" x 1 chance (1 of 42)
            "Rune battleaxe" x 1 chance (1 of 42)
            "Rune sq shield" x 1 chance (1 of 64)
            "Dragon med helm" x 1 chance (1 of 128)
            "Rune kiteshield" x 1 chance (1 of 128)
            "Coins" x 5000..15_000 chance (1 of 6) // Coins
            985 x 1 chance (1 of 6) // Crystal key half.
            987 x 1 chance (1 of 6) // Crystal key half.

            noted {
                "Runite bar" x 1..10 chance (1 of 25)
                "Dragonstone" x 1..10 chance (1 of 64)
                "Silver ore" x 100..200 chance (1 of 64)
            }
        }.table {
            object : DropTable() {
                override fun canRollOnTable(mob: Mob?, source: Entity?): Boolean {
                    return rollSuccess(chance)
                }

                override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList {
                    val roll = rand().nextInt(24)
                    return when {
                        roll < 3 -> megaRareDropTable(ALWAYS, rowBonus).computeTable(mob, source) // 1 of 8 chance
                        roll < 7 -> gemDropTable(ALWAYS, rowBonus).computeTable(mob, source) // 1 of 6 chance
                        else -> items
                    }
                }

                override fun computePossibleItems(): DropTableItemList = items +
                        gemDropTable().computePossibleItems() + megaRareDropTable().computePossibleItems()
            }
        }
    }

    /**
     * The mega rare drop table. Mostly empty slots; a Ring of Wealth removes these and increases useful drops.
     *
     * @param chance Chance to roll on this table.
     * @param rowBonus Enables RoW effects programmatically.
     */
    fun megaRareDropTable(chance: Rational = ALWAYS, rowBonus: Boolean = false): DropTable {
        return DropTableHandler.create {
            nothing(22 of 25)
            "Rune spear" x 1..5 chance (1 of 16)
            "Shield left half" x 1 chance (1 of 32)
            "Dragon spear" x 1 chance (1 of 42)
        }.table {
            object : DropTable() {
                override fun canRollOnTable(mob: Mob?, source: Entity?): Boolean {
                    return rollSuccess(chance)
                }

                override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList {
                    // Filter empty slots if needed and build the table.
                    if ((mob is Player && mob.equipment.contains(2572)) || rowBonus) {
                        return items.filterNot { it.isNothing() }
                    }
                    return items
                }

                override fun computePossibleItems(): DropTableItemList = items
            }
        }
    }

    /**
     * A general-purpose seed drop table that adjusts outcomes based on the combat level of the NPC or player.
     *
     * @param combatLevelFactor If true, seed tier is scaled by the mob's combat level.
     * @param chance Chance to roll on this table.
     */
    fun generalSeedDropTable(combatLevelFactor: Boolean = true, chance: Rational = ALWAYS): DropTable {
        return DropTableHandler.create {}.table {
            object : DropTable(chance) {
                override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList {
                    val roll =
                        if (combatLevelFactor) {
                            val combatLevel =
                                if (source is Npc)
                                    source.combatLevel
                                else if (mob is Mob)
                                    mob.combatLevel
                                else
                                    throw Exception("unexpected")
                            rand().nextInt(combatLevel * 10)
                        } else {
                            rand().nextInt(1000)
                        }
                    return when {
                        roll >= 995 -> generalSeedDropList6()
                        roll in 947..995 -> generalSeedDropList5()
                        roll in 850..947 -> generalSeedDropList4()
                        roll in 728..850 -> generalSeedDropList3()
                        roll in 485..728 -> generalSeedDropList2()
                        else -> generalSeedDropList1()
                    }
                }

                override fun computePossibleItems(): DropTableItemList = generalSeedDropList1() +
                        generalSeedDropList2() + generalSeedDropList3() + generalSeedDropList4() +
                        generalSeedDropList5() + generalSeedDropList6()
            }
        }
    }

    /** Tier 1 general seed drops: basic allotment seeds. */
    fun generalSeedDropList1(): DropTableItemList {
        return DropTableHandler.createList {
            "Potato seed" x 1..4 chance (1 of 2)
            "Onion seed" x 1..3 chance (1 of 4)
            "Cabbage seed" x 1..3 chance (1 of 8)
            "Tomato seed" x 1..3 chance (1 of 16)
            "Sweetcorn seed" x 1..2 chance (1 of 32)
            "Strawberry seed" x 1 chance (1 of 64)
            "Watermelon seed" x 1 chance (1 of 128)
        }
    }

    /** Tier 2 general seed drops: early hops and niche crops. */
    fun generalSeedDropList2(): DropTableItemList {
        return DropTableHandler.createList {
            "Barley seed" x 1..4 chance (1 of 4)
            "Hammerstone seed" x 1..3 chance (1 of 4)
            "Asgarnian seed" x 1..3 chance (1 of 6)
            "Jute seed" x 1..2 chance (1 of 6)
            "Yanillian seed" x 1..2 chance (1 of 9)
            "Krandorian seed" x 1..2 chance (1 of 17)
            "Wildblood seed" x 1 chance (1 of 34)
        }
    }

    /** Tier 3 general seed drops: low-level flower and herb seeds. */
    fun generalSeedDropList3(): DropTableItemList {
        return DropTableHandler.createList {
            "Marigold seed" x 1 chance (1 of 2)
            "Nasturtium seed" x 1 chance (1 of 4)
            "Rosemary seed" x 1 chance (1 of 6)
            "Woad seed" x 1 chance (1 of 8)
            "Limpwurt seed" x 1 chance (1 of 10)
        }
    }

    /** Tier 4 general seed drops: common berry and bush seeds. */
    fun generalSeedDropList4(): DropTableItemList {
        return DropTableHandler.createList {
            "Redberry seed" x 1 chance (1 of 2)
            "Cadavaberry seed" x 1 chance (1 of 3)
            "Dwellberry seed" x 1 chance (1 of 5)
            "Jangerberry seed" x 1 chance (1 of 12)
            "Whiteberry seed" x 1 chance (1 of 34)
            "Poison ivy seed" x 1 chance (1 of 90)
        }
    }

    /** Tier 5 general seed drops: herb seeds from Guam to Torstol. */
    fun generalSeedDropList5(): DropTableItemList {
        return DropTableHandler.createList {
            "Guam seed" x 1 chance (1 of 3)
            "Marrentill seed" x 1 chance (1 of 4)
            "Tarromin seed" x 1 chance (1 of 6)
            "Harralander seed" x 1 chance (1 of 9)
            "Ranarr seed" x 1 chance (1 of 14)
            "Toadflax seed" x 1 chance (1 of 21)
            "Irit seed" x 1 chance (1 of 31)
            "Avantoe seed" x 1 chance (1 of 45)
            "Kwuarm seed" x 1 chance (1 of 66)
            "Snapdragon seed" x 1 chance (1 of 100)
            "Cadantine seed" x 1 chance (1 of 142)
            "Lantadyme seed" x 1 chance (1 of 200)
            "Dwarf weed seed" x 1 chance (1 of 333)
            "Torstol seed" x 1 chance (1 of 500)
        }
    }

    /** Tier 6 general seed drops: tree, cactus, and mushroom spores. */
    fun generalSeedDropList6(): DropTableItemList {
        return DropTableHandler.createList {
            "Mushroom spore" x 1 chance (1 of 2)
            "Belladonna seed" x 1 chance (1 of 3)
            "Cactus seed" x 1 chance (1 of 5)
        }
    }

    /**
     * Rare herb and tree seed drops with progressively rarer items.
     *
     * @param chance Chance to roll on this table.
     */
    fun rareSeedDropTable(chance: Rational = ALWAYS): SimpleDropTable {
        return DropTableHandler.create {
            "Toadflax seed" x 1 chance (1 of 5)
            "Irit seed" x 1 chance (1 of 7)
            "Belladonna seed" x 1 chance (1 of 7)
            "Avantoe seed" x 1 chance (1 of 10)
            "Poison ivy seed" x 1 chance (1 of 10)
            "Cactus seed" x 1 chance (1 of 11)
            "Kwuarm seed" x 1 chance (1 of 15)
            "Snapdragon seed" x 1 chance (1 of 23)
            "Cadantine seed" x 1 chance (1 of 34)
            "Lantadyme seed" x 1 chance (1 of 47)
            "Dwarf weed seed" x 1 chance (1 of 79)
            "Torstol seed" x 1 chance (1 of 119)
        }.table { SimpleDropTable(items, chance) }
    }

    /**
     * High-tier herb and tree seed drops including spirit, magic, and palm tree seeds.
     *
     * @param chance Chance to roll on this table.
     */
    fun treeHerbSeedDropTable(chance: Rational): SimpleDropTable {
        return DropTableHandler.create {
            "Ranarr seed" x 1 chance (1 of 8)
            "Snapdragon seed" x 1 chance (1 of 8)
            "Torstol seed" x 1 chance (1 of 11)
            "Watermelon seed" x 5..25 chance (1 of 11)
            "Willow seed" x 1 chance (1 of 12)
            "Mahogany seed" x 1 chance (1 of 14)
            "Maple seed" x 1 chance (1 of 14)
            "Teak seed" x 1 chance (1 of 14)
            "Yew seed" x 1 chance (1 of 14)
            "Papaya tree seed" x 1 chance (1 of 17)
            "Magic seed" x 1 chance (1 of 22)
            "Palm tree seed" x 1 chance (1 of 25)
            "Spirit seed" x 1 chance (1 of 62)
        }.table { SimpleDropTable(items, chance) }
    }

    /**
     * A combined drop table of mid-to-high tier seeds and [rareSeedDropTable] contents.
     *
     * @param chance Chance to roll on this table.
     */
    fun uncommonSeedDropTable(chance: Rational = ALWAYS): DropTable {
        return DropTableHandler.createSimple(chance) {
            items += generalSeedDropList3()
            items += generalSeedDropList4()
            items += generalSeedDropList5()
            items += generalSeedDropList6()
            items += rareSeedDropTable().computePossibleItems()
        }
    }

    /**
     * Drops noted useful high-level herbs (e.g. Ranarr, Torstol).
     *
     * @param chance Chance to roll on this table.
     */
    fun usefulHerbDropTable(chance: Rational = ALWAYS): DropTable {
        return DropTableHandler.createSimple(chance) {
            noted {
                "Grimy avantoe" x 1..3 chance (1 of 3)
                "Grimy snapdragon" x 1..3 chance (1 of 4)
                "Grimy ranarr weed" x 1..3 chance (1 of 4)
                "Grimy torstol" x 1..3 chance (1 of 5)
            }
        }
    }

    /**
     * Drops a wide range of grimy herbs, including lower and higher-tier varieties.
     *
     * @param chance Chance to roll on this table.
     */
    fun herbDropTable(chance: Rational = ALWAYS): DropTable {
        return DropTableHandler.createSimple(chance) {
            "Grimy guam leaf" x 1 chance (1 of 4)
            "Grimy marrentill" x 1 chance (1 of 5)
            "Grimy tarromin" x 1 chance (1 of 7)
            "Grimy harralander" x 1 chance (1 of 9)
            "Grimy ranarr weed" x 1 chance (1 of 11)
            "Grimy irit leaf" x 1 chance (1 of 16)
            "Grimy avantoe" x 1 chance (1 of 21)
            "Grimy kwuarm" x 1 chance (1 of 25)
            "Grimy cadantine" x 1 chance (1 of 32)
            "Grimy lantadyme" x 1 chance (1 of 42)
            "Grimy dwarf weed" x 1 chance (1 of 42)
        }
    }
}