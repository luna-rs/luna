package game.bot.scripts.skills

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import game.skill.prayer.Bone
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.mob.bot.Bot

/**
 * Tracks loose and noted bones and protects part of each bulk purchase for future use.
 *
 * @author lare96
 */
internal object PrayerBoneStock {
    private var Bot.prayerBoneReserves by Attr.string().persist("bot_prayer_bone_reserves")

    fun reserves(bot: Bot): Map<Int, Int> = bot.prayerBoneReserves.split(';').mapNotNull { entry ->
        val (id, amount) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        val bone = id.toIntOrNull()?.takeIf { it in Bone.ID_TO_BONE } ?: return@mapNotNull null
        val count = amount.toIntOrNull()?.takeIf { it in 1..50 } ?: return@mapNotNull null
        bone to count
    }.toMap()

    fun reserve(bot: Bot, id: Int): Int = if (id in Bone.ID_TO_BONE) reserves(bot)[id] ?: 0 else 0

    fun retainPurchase(bot: Bot, id: Int, quantity: Int) {
        if (quantity < 100) return
        val reserves = reserves(bot).toMutableMap()
        reserves[id] = maxOf(reserves[id] ?: 0, minOf(50, quantity / 10))
        bot.prayerBoneReserves = reserves.entries.joinToString(";") { "${it.key}=${it.value}" }
    }

    fun owned(bot: Bot, id: Int): Int {
        val noted = ItemDefinition.ALL.get(id).map { it.notedId.orElse(-1) }.orElse(-1)
        val loose = bot.inventory.computeNonDynamicAmountForId(id).toLong() +
            bot.bank.computeNonDynamicAmountForId(id)
        val notes = if (noted >= 0 && noted != id) bot.inventory.computeNonDynamicAmountForId(noted) else 0
        return (loose + notes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun hasNotes(bot: Bot): Boolean = bot.inventory.any { item ->
        item != null && !item.isDynamic && item.itemDef.unnotedId.orElse(-1) in Bone.ID_TO_BONE
    }
}
