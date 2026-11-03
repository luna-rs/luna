package api.bot.action

import api.bot.Suspendable.waitFor
import api.bot.script.BotScript.ActionSafety
import api.predef.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.content.darkness.DarkArea
import game.skill.firemaking.Firemaking
import game.skill.firemaking.lightSource.LightSource
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration.Companion.seconds

/**
 * Selects, collects, lights and extinguishes owned light sources through normal player actions.
 * Sealed-light callers retain their cave safety policy, while safe ordinary areas can also use candles and torches.
 * No source is assembled or purchased. Lit mining helmets count while worn and need not be removed.
 *
 * @author lare96
 */
object BotLightSources {
    /**
     * Chooses already lit stock before a brighter unlit source that the bot can actually light.
     */
    fun owned(bot: Bot, sealed: Boolean = false): LightSource? = LightSource.entries.filter {
        it.brightness > 0 && (!sealed || !it.naked) &&
            (bot.itemTracker.contains(it.lit) || bot.firemaking.level >= it.level &&
                bot.itemTracker.contains(it.unlit) && bot.itemTracker.contains(Firemaking.TINDERBOX))
    }.sortedWith(compareByDescending<LightSource> { bot.itemTracker.contains(it.lit) }
        .thenByDescending { it.brightness }).firstOrNull()

    /**
     * Verifies physical illumination, with an optional requirement for an enclosed flame.
     */
    fun carried(bot: Bot, sealed: Boolean = false): Boolean = LightSource.entries.any {
        it.brightness > 0 && (!sealed || !it.naked) &&
            (bot.inventory.contains(it.lit) || bot.equipment[Equipment.HEAD]?.id == it.lit)
    }

    /**
     * Collects an owned source and its tinderbox when necessary from an already open bank.
     */
    suspend fun collect(bot: Bot, sealed: Boolean = false): Boolean {
        if (carried(bot, sealed)) return true
        val source = owned(bot, sealed) ?: return false
        val items = if (bot.itemTracker.contains(source.lit)) listOf(source.lit)
            else listOf(source.unlit, Firemaking.TINDERBOX)
        for (id in items) {
            if (!ActionSafety.NON_COMBAT.allows(bot)) return false
            if (!bot.inventory.contains(id) && (!bot.actionHandler.banking.withdraw(Item(id)) ||
                    !bot.inventory.contains(id))) return false
        }
        return carried(bot, sealed) || bot.firemaking.level >= source.level &&
            bot.inventory.contains(source.unlit) && bot.inventory.contains(Firemaking.TINDERBOX)
    }

    /**
     * Rejects naked flames in the swamp cave and goblin-mine footprints, including before native gas is implemented.
     */
    fun safeToLight(bot: Bot, source: LightSource): Boolean = !source.naked ||
        DarkArea.forPosition(bot.position) !in setOf(DarkArea.LUMBRIDGE_SWAMP_CAVES, DarkArea.CAVE_GOBLIN_MINE)

    /**
     * Lights one carried source with an owned tinderbox, verifying the native item transformation.
     */
    suspend fun light(bot: Bot, source: LightSource): Boolean {
        if (!bot.inventory.contains(source.unlit)) return bot.inventory.contains(source.lit)
        if (!ActionSafety.NON_COMBAT.allows(bot) || !safeToLight(bot, source) ||
            bot.firemaking.level < source.level || !bot.inventory.contains(source.unlit) ||
            !bot.inventory.contains(Firemaking.TINDERBOX)) return false
        val before = bot.inventory.computeAmountForId(source.unlit)
        if (!bot.actionHandler.inventory.useItem(Firemaking.TINDERBOX).onItem(source.unlit)) return false
        return bot.inventory.computeAmountForId(source.unlit) < before || waitFor(3.seconds) {
            bot.inventory.computeAmountForId(source.unlit) < before
        }
    }

    /**
     * Restores usable illumination after banking without allowing an unsafe open-flame substitute.
     */
    suspend fun ensureLit(bot: Bot, sealed: Boolean = false): Boolean {
        if (carried(bot, sealed)) return true
        val source = LightSource.entries.filter {
            it.brightness > 0 && (!sealed || !it.naked) && safeToLight(bot, it) &&
                bot.firemaking.level >= it.level && bot.inventory.contains(it.unlit)
        }.maxByOrNull { it.brightness } ?: return false
        return light(bot, source) && carried(bot, sealed)
    }

    /**
     * Extinguishes one carried light through its real cache option, only outside native dark areas.
     * Required worn equipment is never removed and a failed interaction cannot report a conversion.
     */
    suspend fun extinguish(bot: Bot, source: LightSource): Boolean {
        if (!ActionSafety.NON_COMBAT.allows(bot) || DarkArea.forPosition(bot.position) != null ||
            !bot.inventory.contains(source.lit)) return false
        val option = itemDef(source.lit).inventoryActions.indexOfFirst { it.equals("Extinguish", true) } + 1
        if (option !in 1..3) return false
        val before = bot.inventory.computeAmountForId(source.lit)
        if (!bot.actionHandler.inventory.clickItem(option, source.lit)) return false
        return bot.inventory.computeAmountForId(source.lit) < before || waitFor(3.seconds) {
            bot.inventory.computeAmountForId(source.lit) < before
        }
    }
}
