package world.player.skill.thieving

import api.dropTable.DropTable
import api.dropTable.DropTableHandler
import api.dropTable.DropTableItemList
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.Entity
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.util.RandomUtils
import io.luna.util.Rational
import world.player.skill.Skills
import world.player.skill.thieving.pickpocketNpc.ThievableNpc

/**
 * Contains utility functions related to the thieving skill.
 *
 * @author lare96
 */
object Thieving {

    /**
     * The rogue equipment drop table.
     */
    private val ROGUE_EQUIPMENT_TABLE = DropTableHandler.create {
        "Rogue mask" x 1 chance (1 of 2500)
        "Rogue top" x 1 chance (1 of 2500)
        "Rogue gloves" x 1 chance (1 of 2500)
        "Rogue trousers" x 1 chance (1 of 2500)
        "Rogue boots" x 1 chance (1 of 2500)
    }.table {
        object : DropTable() {
            // Remove from drop list if we already own a set.
            override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList =
                if (mob is Player) table.filterNot { mob.hasItem(it.id) } else table

            override fun computePossibleItems(): DropTableItemList = table
        }
    }

    /**
     * The rogue equipment items.
     */
    private val ROGUE_EQUIPMENT_ITEMS = setOf(Item(5553), Item(5554), Item(5555), Item(5556), Item(5557))


    /**
     * If a pickpocketing action will be successful or not.
     */
    fun canPickpocket(plr: Player, thievable: ThievableNpc): Boolean {
        val level = plr.thieving.level
        if (level >= thievable.master) {
            return true
        }
        val (low, high) = thievable.chance
        return Skills.success(low, high, level)
    }

    /**
     * Attempts to roll for rogue equipment.
     */
    fun rollRogueEquipment(plr: Player, target: Entity, chance: Rational = ALWAYS) {
        if (RandomUtils.roll(chance)) {
            val rogueLoot = ROGUE_EQUIPMENT_TABLE.roll(plr, target)
            for (item in rogueLoot) {
                plr.giveItem(item)
                plr.sendMessage("You also manage to find ${addArticle(item.itemDef.name)}!")
            }
        }
    }

    /**
     * Determines if [plr] will receive double loot based on their equipment.
     */
    fun isDoubleLoot(plr: Player): Boolean {
        val pieces = ROGUE_EQUIPMENT_ITEMS.count { plr.equipment.contains(it.equipDef.index, it.id) }
        return RandomUtils.rollPercent(if (pieces == 5) 1.0 else pieces * 0.15)
    }
}