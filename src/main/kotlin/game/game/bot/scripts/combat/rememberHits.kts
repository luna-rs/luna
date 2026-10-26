package game.bot.scripts.combat

import api.bot.action.eating.MonsterMemory.Companion.monsterMemory
import api.predef.*
import io.luna.game.event.impl.CombatDamageReceivedEvent
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot

// Bots remember the hits they take, so they learn how hard monsters hit and when their hitpoints dropped.
on(CombatDamageReceivedEvent::class) {
    val bot = plr as? Bot ?: return@on
    bot.monsterMemory.remember((damage.attacker as? Npc)?.id, damage.rawAmount, world.currentTick)
}
