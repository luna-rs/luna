@file:JvmName("BotTypes")

package api.bot

import io.luna.game.model.mob.bot.Bot
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Tests autonomous bot identity, excluding humans that have dormant or active bot-script facilities.
 * Null and ordinary players are false, even if another Player subtype overrides isBot to return true.
 * The contract preserves Bot smart casts at Kotlin call sites; Java calls BotTypes.isRealBot(value).
 *
 * @author lare96
 */
@OptIn(ExperimentalContracts::class)
fun Any?.isRealBot(): Boolean {
    contract { returns(true) implies (this@isRealBot is Bot) }
    return this is Bot && isBot
}
