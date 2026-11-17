package api.bot.boss

import api.attr.Attr
import api.attr.getValue
import api.bot.isRealBot
import api.predef.hitpoints
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.damage.CombatDamage
import kotlin.math.ceil

/**
 * Bounded personal boss experience used only to decide whether an unsupported bot can finish its current opponent.
 * Delivered personal hits, including misses, teach offence; other participants' damage never contributes to it.
 * No live NPC, party or player reference is retained in the persisted four-entry memory.
 *
 * @author lare96
 */
object BossEncounterLearning {
    /**
     * Aggregates at most sixty-four attack samples before ageing older observations.
     * Weapon identity invalidates offence after a gear change without erasing incoming boss experience.
     *
     * @author lare96
     */
    class Memory {
        var weapon = -1
        var attacks = 0
        var damage = 0.0
        var attackTicks = 0.0
        var incomingAttacks = 0
        var incomingDamage = 0.0
        var incomingTicks = 0.0
        var largestIncomingHit = 0
    }

    /**
     * Fixed-size saved record, indexed only by the four supported destinations.
     *
     * @author lare96
     */
    class Progress {
        var memories = Array(4) { Memory() }
    }

    private val Bot.bossExperience by Attr.obj { Progress() }.persist("boss_encounter_learning_v1")

    /**
     * Recognizes native boss families, including both Queen forms.
     */
    private fun target(id: Int): BossTarget? = when (id) {
        3340 -> BossTarget.MOLE
        50 -> BossTarget.KBD
        3200 -> BossTarget.CHAOS_ELEMENTAL
        1158, 1160, 3835, 3836 -> BossTarget.KALPHITE_QUEEN
        else -> null
    }

    /**
     * Returns one bounded personal record, rejecting malformed saved array lengths by rebuilding the record.
     */
    internal fun memory(bot: Bot, target: BossTarget): Memory {
        val progress = bot.bossExperience
        if (progress.memories.size != 4) progress.memories = Array(4) { Memory() }
        return progress.memories[target.ordinal]
    }

    /**
     * Records applied damage after health capping. Splashes and physical misses count as spent attack time.
     * Effect-only markers do not train offensive damage. Source delay is bounded to one through twenty ticks.
     */
    @JvmStatic fun record(damage: CombatDamage, attackDelay: Int) {
        if (damage.rawAmount < 0) return
        val delay = attackDelay.coerceIn(1, 20).toDouble()
        val attacker = damage.attacker
        val victim = damage.victim
        if (attacker.isRealBot() && victim is Npc) {
            val boss = target(victim.id) ?: return
            val memory = memory(attacker, boss)
            val weapon = attacker.equipment[Equipment.WEAPON]?.id ?: -1
            if (memory.weapon != weapon) {
                memory.weapon = weapon
                memory.attacks = 0
                memory.damage = 0.0
                memory.attackTicks = 0.0
            }
            if (memory.attacks >= 64) {
                memory.attacks /= 2
                memory.damage /= 2
                memory.attackTicks /= 2
            }
            memory.attacks++
            memory.damage += damage.rawAmount.coerceIn(0, 255)
            memory.attackTicks += delay
        } else if (attacker is Npc && victim.isRealBot()) {
            val boss = target(attacker.id) ?: return
            val memory = memory(victim, boss)
            if (memory.incomingAttacks >= 64) {
                memory.incomingAttacks /= 2
                memory.incomingDamage /= 2
                memory.incomingTicks /= 2
            }
            memory.incomingAttacks++
            memory.incomingDamage += damage.rawAmount.coerceIn(0, 255)
            memory.incomingTicks += delay
            memory.largestIncomingHit = maxOf(memory.largestIncomingHit, damage.rawAmount.coerceIn(0, 255))
        }
    }

    /**
     * Forecasts only one remaining life, with a reserve for another hit and time lost to eating.
     * An unsupported first-form Queen is never treated as an almost-completed two-form encounter.
     * Native incoming damage floors prevent earlier tanking by companions from implying future safety.
     */
    @JvmStatic fun canFinish(bot: Bot, target: BossTarget, npc: Npc): Boolean {
        if (this.target(npc.id) != target || !npc.isAlive || npc.health <= 0 || !bot.isAlive) return false
        if (target == BossTarget.KALPHITE_QUEEN && npc.id in setOf(1158, 3835)) return false
        val memory = memory(bot, target)
        if (memory.attacks < 3 || memory.weapon != (bot.equipment[Equipment.WEAPON]?.id ?: -1) ||
            !memory.damage.isFinite() || memory.damage <= 0 || !memory.attackTicks.isFinite() || memory.attackTicks <= 0)
            return false
        val maximum = maxOf(memory.largestIncomingHit, when (target) {
            BossTarget.MOLE -> 21
            BossTarget.KBD -> 25
            BossTarget.CHAOS_ELEMENTAL -> 28
            BossTarget.KALPHITE_QUEEN -> 31
        })
        val poison = bot.status.isPoisoned() && !bot.status.isAntiPoison()
        val reserve = maxOf(maximum + if (poison) 10 else 4, bot.hitpoints.staticLevel / 5)
        if (bot.health <= reserve || poison && bot.health <= maximum * 2) return false
        val nativeDelay = if (target == BossTarget.KBD) 5.0 else 4.0
        val observedRate = if (memory.incomingTicks.isFinite() && memory.incomingTicks > 0 &&
            memory.incomingDamage.isFinite()) memory.incomingDamage / memory.incomingTicks else 0.0
        val incomingRate = maxOf(maximum / (2 * nativeDelay), observedRate) + if (poison) 0.5 else 0.0
        val confidence = bot.personality.confidence.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.5
        val intelligence = bot.personality.intelligence.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.5
        val margin = 1.1 + (1.0 - confidence) * 0.5 + intelligence * 0.25
        val ticks = ceil(npc.health / (memory.damage / memory.attackTicks) * margin)
        if (!ticks.isFinite() || ticks > 2000) return false
        var availableHealth = bot.health.toDouble() - reserve
        if (availableHealth >= incomingRate * ticks) return true
        val foodPolicy = BossFood.policy(target, true)
        val food = bot.inventory.filterNotNull().mapNotNull { item ->
            BossFood.portion(item.id)?.takeIf { it.biteHealing >= foodPolicy.minimumBiteHealing }?.let {
                Triple(it, item.amount.coerceIn(0, 28), maxOf(4, it.delay))
            }
        }.sortedByDescending { it.first.biteHealing }
        var physicalItems = 0
        var eatingTicks = 0
        for ((portion, count, delay) in food) repeat(minOf(count, 28 - physicalItems)) {
            physicalItems++
            repeat(portion.remainingBites.coerceIn(0, 4)) {
                eatingTicks += delay
                availableHealth += minOf(portion.biteHealing, bot.hitpoints.staticLevel)
                // Each remaining bite costs its own food action and delayed outgoing attack.
                if (availableHealth >= incomingRate * (ticks + eatingTicks)) return true
            }
        }
        return false
    }
}
