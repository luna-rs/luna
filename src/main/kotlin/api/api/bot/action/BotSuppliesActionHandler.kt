package api.bot.action

import game.player.item.consume.food.Food
import game.skill.magic.Rune
import game.skill.mining.Ore
import game.skill.prayer.Bone
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.mob.bot.Bot
import kotlin.math.floor

class BotSuppliesActionHandler(private val bot: Bot, private val handler: BotActionHandler) {
    companion object {
        const val DEFAULT_STACKABLE_WANT_AMOUNT = 5000
    }

    enum class SupplyType {
        RESOURCE,

        // TODO figure out how we wanna do this or if we even need it tbh
        RUNES,

    }

    enum class WantedItemType(val amount: (Int) -> Int?, val items: () -> Set<Int>) {
        FOOD(amount = {
            val heal = Food.ID_TO_FOOD[it]?.heal
            if (heal != null) {
                when {
                    heal < 12 -> 5000
                    heal < 3 -> 2500
                    heal < 3 -> 1000
                    else -> null
                }
            } else {
                null
            }
        }, items = { Food.ID_TO_FOOD.keys.toSet() }),
        ELEMENTAL_RUNES(amount = { if (Rune.ID_TO_RUNE[it]?.isElemental() == true) 30_000 else null },
                        items = { Rune.ID_TO_RUNE.values.filter { it.isElemental() }.map { it.id }.toSet() }),
        SECONDARY_RUNES(amount = { if (Rune.ID_TO_RUNE[it]?.isSecondary() == true) 6000 else null },
                        items = { Rune.ID_TO_RUNE.values.filter { it.isSecondary() }.map { it.id }.toSet() }),
        ADVANCED_RUNES(amount = { if (Rune.ID_TO_RUNE[it]?.isAdvanced() == true) 3000 else null },
                       items = { Rune.ID_TO_RUNE.values.filter { it.isAdvanced() }.map { it.id }.toSet() }),
        ESSENCE(amount = {
            when (Ore.ITEM_MAP[it]) {
                Ore.RUNE_ESSENCE, Ore.PURE_ESSENCE -> 50_000
                else -> null
            }
        }, items = { setOf(Ore.RUNE_ESSENCE.item, Ore.PURE_ESSENCE.item) }),
        ORES(amount = {
            when (Ore.ITEM_MAP[it]) {
                Ore.CLAY, Ore.TIN, Ore.COPPER, Ore.IRON -> 10_000
                Ore.SILVER, Ore.COAL, Ore.GOLD, Ore.MITHRIL -> 3500
                Ore.ADAMANT, Ore.RUNE -> 1000
                else -> null
            }
        }, items = {
            setOf(Ore.CLAY, Ore.TIN, Ore.COPPER, Ore.IRON, Ore.SILVER, Ore.COAL, Ore.GOLD, Ore.MITHRIL, Ore.ADAMANT,
                  Ore.RUNE).map { it.item }.toSet()
        }),
        LOGS(amount = {
            when (Tree.ALL[it]) {
                Tree.NORMAL, Tree.OAK, Tree.WILLOW -> 20_000
                Tree.TEAK, Tree.MAPLE, Tree.MAHOGANY -> 10_000
                Tree.YEW, Tree.MAGIC -> 5000
                else -> null
            }
        }, items = { Tree.ALL.keys.toSet() }),

        BONES(amount = {
            when (Bone.ID_TO_BONE[it]) {
                Bone.BONES, Bone.WOLF_BONES, Bone.BAT_BONES, Bone.MONKEY_BONES -> 20_000
                Bone.BIG_BONES -> 10_000
                Bone.BABYDRAGON_BONES, Bone.DRAGON_BONES -> 5000
                else -> null
            }
        }, items = { Bone.ID_TO_BONE.keys.toSet() });
        // todo rethink this??
        // maybe just.. have a file with common amount presets

        fun getBaseSellAmount(id: Int) = amount(id)!! * 2
    }

    private val scale = bot.combatLevel / 126.0

    fun getSellAmount(id: Int, type: WantedItemType) = floor(type.getBaseSellAmount(id) * scale).toInt()

    fun getWantAmount(id: Int, type: WantedItemType) = floor(type.amount(id)!! * scale).toInt()

    fun computeCount(type: WantedItemType): Int {
        var currentAmount = 0
        for (item in bot.bank) {
            if (item == null) {
                continue
            }
            if (type.amount(item.id) != null) {
                currentAmount += item.amount
            }
        }
        return currentAmount
    }

    fun getWantedFood(): List<Food> {
        val all = Food.ID_TO_FOOD.values
        if (bot.personality.isDumb) {
            return all.toList()
        } else {
            return when {
                bot.combatLevel < 25 -> all.filter { it.heal <= 3 }
                bot.combatLevel < 50 -> all.filter { it.heal > 3 && it.heal <= 7 }
                bot.combatLevel < 90 -> all.filter { it.heal > 7 && it.heal <= 12 }
                else -> all.filter { it.heal > 12 && it.heal <= 22 }
            }
        }
    }


}