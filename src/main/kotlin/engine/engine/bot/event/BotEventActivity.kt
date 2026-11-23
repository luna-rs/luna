package engine.bot.event

import api.bot.script.BotScript
import io.luna.LunaContext
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot

/**
 * An activity that can admit a temporary roster into its existing game.
 *
 * @author lare96
 */
internal class BotEventActivity(
    val name: String,
    val defaultBots: Int = 50,
    val defaultMinutes: Int,
    val spawn: (index: Int, count: Int) -> Position,
    val prepare: (Bot, BotEventParticipant) -> Unit,
    val play: (Bot, BotEventParticipant) -> BotScript?,
    val inMatch: (Bot) -> Boolean = { false },
    val leave: (Bot) -> Unit = {},
    val maxBots: Int = 100,
    val configure: (Bot.Builder, LunaContext) -> Unit = { _, _ -> }
)

/**
 * The assigned starting position, roster slot, and admission window of a participant.
 *
 * @author lare96
 */
internal class BotEventParticipant(
    val index: Int,
    val count: Int,
    val spawn: Position,
    val canStart: () -> Boolean
)

/**
 * Replaces event supplies and equipment when the participant is eligible for a fresh kit.
 */
internal fun Bot.applyEventKit(
    levels: Map<Int, Int> = emptyMap(),
    gear: Map<Int, Item> = emptyMap(),
    supplies: List<Item> = emptyList(),
    keepGear: Set<Int> = emptySet()
) {
    inventory.clear()
    for (slot in 0 until equipment.capacity()) {
        if (slot !in keepGear) equipment.set(slot, null)
    }
    levels.forEach { (id, level) ->
        skills.getSkill(id).apply {
            staticLevel = level
            this.level = level
        }
    }
    gear.forEach { (slot, item) -> equipment.set(slot, item) }
    check(supplies.all { inventory.add(it) }) { "Event supplies exceed the inventory capacity." }
}

/**
 * Rechecks the event deadline at native entry and launch boundaries.
 */
internal fun Player.canJoinEventMatch(): Boolean = !isEventBot || asBot().canJoinEventMatch()
