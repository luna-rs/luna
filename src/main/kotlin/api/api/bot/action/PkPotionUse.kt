package api.bot.action

import api.attr.Attr
import engine.combat.prayer.CombatPrayer
import game.player.item.consume.potion.Potion
import io.luna.game.model.mob.bot.Bot

/**
 * Short-lived observations from native Wilderness fights for the next PK bank preparation.
 *
 * @author lare96
 */
internal object PkPotionUse {
    private const val MEMORY_MILLIS = 10 * 60 * 1000L
    private const val EXPECTED_FIGHT_TICKS = 120
    private val memory = Attr.obj { Observations() }

    internal val supportPotions by lazy {
        listOf(Potion.ANTIPOISON_POTION, Potion.SUPER_ANTIPOISON, Potion.ANTIDOTE_PLUS,
            Potion.ANTIDOTE_PLUS_PLUS, Potion.PRAYER_POTION, Potion.SUPER_RESTORE)
    }

    private class Observations {
        var prayers = emptySet<CombatPrayer>()
        var prayersUntil = 0L
        var poisonUntil = 0L
    }

    fun observe(bot: Bot, now: Long = System.currentTimeMillis()) {
        val prayers = bot.combat.prayers.active.filterTo(linkedSetOf()) { it.drain > 0 }
        val poisoned = bot.status.isPoisoned()
        if (prayers.isEmpty() && !poisoned) return
        val seen = bot.attributes()[memory.attr]
        val expires = now + MEMORY_MILLIS
        if (prayers.isNotEmpty()) {
            seen.prayers = prayers
            seen.prayersUntil = expires
        }
        if (poisoned) seen.poisonUntil = expires
    }

    fun needs(bot: Bot, prayerBonus: Int, maximumCoins: Long, now: Long = System.currentTimeMillis()):
            List<BotPotionTrip.Need> {
        val seen = bot.attributes()[memory.attr]
        val prayers = if (seen.prayersUntil > now) seen.prayers else emptySet()
        val poison = seen.poisonUntil > now
        if (!poison && prayers.isEmpty()) return emptyList()
        return BotPotionSupport.combatNeeds(bot, EXPECTED_FIGHT_TICKS, poison, dragonfire = false,
            prayers = prayers, maximumCoins = maximumCoins, prayerBonus = prayerBonus, restoreStats = false)
    }
}
