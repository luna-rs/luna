package api.bot.action

import engine.bot.gear.BotGearPurpose
import api.drops.DropTableHandler
import api.predef.*
import api.predef.ext.*
import game.player.item.consume.potion.Potion
import game.player.item.consume.potion.PotionFamily
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.combat.CombatFormula
import io.luna.game.model.mob.combat.CombatFormula.PhysicalType
import kotlin.math.roundToInt

/**
 * Values optional boosts for the equipped style and a prospective bank-to-bank NPC outing.
 *
 * @author lare96
 */
internal object NpcPotionPolicy {
    fun expectedTicks(bot: Bot, target: Npc?, healing: Int, remainingTicks: Int): Int {
        val incoming = target?.let { estimateCombatThreat(it, bot, capability = false).damagePerTick }
        val foodTicks = if (incoming != null && incoming > 0.0) (healing / (incoming * 0.65)).toInt() else 750
        return minOf(remainingTicks, foodTicks.coerceIn(100, 1000))
    }

    fun boosts(bot: Bot, purpose: BotGearPurpose, target: Npc?, ticks: Int, training: Boolean,
               healingValue: Double, cash: Long, loot: (Int) -> Boolean): List<BotPotionTrip.Need> {
        if (target == null || ticks < 30) return emptyList()
        val melee = purpose == BotGearPurpose.MELEE
        val ranged = purpose == BotGearPurpose.RANGED
        val magic = purpose == BotGearPurpose.MAGIC
        if (!melee && !ranged && !magic) return emptyList()

        val type = when {
            melee -> CombatDamageType.MELEE
            ranged -> CombatDamageType.RANGED
            else -> CombatDamageType.MAGIC
        }
        val attack = when {
            melee -> bot.attack.staticLevel
            ranged -> bot.ranged.staticLevel
            else -> bot.magic.staticLevel
        }
        val strength = if (melee) bot.strength.staticLevel else bot.ranged.staticLevel
        val attackPotion = when {
            melee -> Potion.ATTACK_POTION
            ranged -> Potion.RANGING_POTION
            else -> Potion.MAGIC_POTION
        }
        val chance = CombatFormula.calculateHitChance(bot, target, type)
        val boost = Potion.combatBoost(attack, false)
        val accuracyFactor = (100.0 * (attack + 8 + boost) / (attack + 8)).roundToInt()
        val boostedChance = CombatFormula.calculateHitChance(bot, target, type, accuracyFactor)
        val attackGain = if (chance > 0.0) boostedChance / chance - 1.0 else 0.0
        val selectedType = if (melee) PhysicalType.MELEE else PhysicalType.RANGED
        val nativeHit = if (magic) 0 else CombatFormula.calculatePhysicalMaxHit(bot, selectedType, strength)
        val boostedHit = if (magic) 0 else CombatFormula.calculatePhysicalMaxHit(bot, selectedType,
            strength + Potion.combatBoost(strength, false))
        val strengthGain = if (nativeHit > 0) boostedHit.toDouble() / nativeHit - 1.0 else 0.0
        val outgoing = estimateCombatThreat(bot, target, capability = false).damagePerTick
        val incoming = estimateCombatThreat(target, bot, capability = false).damagePerTick
        val killTicks = (target.totalHealth / outgoing.coerceAtLeast(0.01)).coerceAtLeast(1.0)
        val cycleTicks = killTicks + 12.0
        // A dose is valuable only for the portion of the outing during which the boost retains useful levels.
        val useTicks = minOf(ticks, 200).toDouble()
        val lootValue = if (training) 0.0 else valueOfLoot(bot, target, loot)
        val kills = useTicks / cycleTicks
        val damageSaved = incoming * useTicks * (1.0 - (bot.defence.staticLevel + 8.0) /
            (bot.defence.staticLevel + 8.0 + Potion.combatBoost(bot.defence.staticLevel, false)))
        val defenceValue = damageSaved * healingValue * 0.75
        fun offenseValue(gain: Double) =
            (useTicks / (killTicks / (1.0 + gain) + 12.0) - kills).coerceAtLeast(0.0) * lootValue

        val gains = buildList {
            add(Triple(attackPotion, if (ranged) (1.0 + attackGain) * (1.0 + strengthGain) - 1.0 else attackGain,
                if (ranged) offenseValue((1.0 + attackGain) * (1.0 + strengthGain) - 1.0) else offenseValue(attackGain)))
            if (melee) add(Triple(Potion.STRENGTH_POTION, strengthGain, offenseValue(strengthGain)))
            if (damageSaved >= 2.0) add(Triple(Potion.DEFENCE_POTION, damageSaved / (bot.totalHealth + 1.0), defenceValue))
        }
        return gains.sortedByDescending { if (training) it.second else it.third }.mapNotNull { (potion, gain, value) ->
            if (gain <= 0.015) return@mapNotNull null
            val guide = guidePerDose(bot, potion)
            val slotCost = if (training) 0.0 else lootValue * 0.05
            val ceiling = if (training) minOf(cash, maxOf(guide, 1L) * 2L)
                else minOf(cash, (value - slotCost).toLong())
            if (!training && ceiling < maxOf(1L, guide)) return@mapNotNull null
            BotPotionTrip.Need(listOf(potion), minOf(4, 1 + ticks / 250), ceiling.coerceAtLeast(0L))
        }
    }

    private fun guidePerDose(bot: Bot, potion: Potion): Long {
        val family = PotionFamily.forPotion(potion)
        return family.ids.mapNotNull { id ->
            val price = bot.world.economy.getPrice(id)
            if (price > 0) (price + family.doses(id) - 1L) / family.doses(id) else null
        }.minOrNull() ?: 0L
    }

    private fun valueOfLoot(bot: Bot, target: Npc, loot: (Int) -> Boolean): Double =
        DropTableHandler.getDropTable(target)?.tableList?.sumOf { table ->
            if (!table.canRollOnTable(bot, target)) return@sumOf 0.0
            table.computeTable(bot, target).sumOf item@{ drop ->
                if (drop.chance < 0.02 || drop.isNothing()) return@item 0.0
                val definition = ItemDefinition.ALL.get(drop.id).orElse(null) ?: return@item 0.0
                val id = definition.unnotedId.orElse(drop.id)
                if (id != 995 && (!definition.isTradeable || !loot(id))) return@item 0.0
                val price = if (id == 995) 1.0 else bot.world.economy.getPrice(id).coerceAtLeast(0L) * 0.5
                (drop.amount.first + drop.amount.last.toDouble()) / 2.0 * price * drop.chance.coerceAtMost(1.0)
            } * table.chance
        } ?: 0.0
}
