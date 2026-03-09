package engine.combat

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.NpcClickEvent.AttackNpcEvent
import io.luna.game.event.impl.PlayerClickEvent.PlayerFirstClickEvent
import io.luna.game.event.impl.UseSpellEvent.MagicOnNpcEvent
import io.luna.game.event.impl.UseSpellEvent.MagicOnPlayerEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType

/**
 * Dynamically determines the interaction range when attacking.
 */
fun getRange(plr: Player): InteractionPolicy {
    val range = plr.combat.weapon.range
    return if (range < 2) {
        InteractionPolicy(InteractionType.SIZE, 1)
    } else {
        InteractionPolicy(InteractionType.LINE_OF_SIGHT, range)
    }
}

// "Attack" context menu option on players.
on(PlayerFirstClickEvent::class, EventPriority.HIGH, interaction = { getRange(it) }) {
    if (plr.contextMenu.contains(OPTION_ATTACK) && targetPlr.hitpoints.level > 0) {
        plr.combat.attack(targetPlr)
    }
}

// Use magic spell on player.
on(MagicOnPlayerEvent::class, EventPriority.HIGH, InteractionPolicy.STANDARD_LINE_OF_SIGHT) {
    if (plr.contextMenu.contains(OPTION_ATTACK) && targetPlr.hitpoints.level > 0) {
        // start combat
    }
}

// "Attack" context menu option on npcs.
on(AttackNpcEvent::class, EventPriority.HIGH, interaction = { getRange(it) }) {
    val def = targetNpc.definition
    if (def.combatLevel > 0 && def.actions.contains("Attack")) {
        plr.combat.attack(targetNpc)
    }
}

// Use magic spell on NPC.
on(MagicOnNpcEvent::class, EventPriority.HIGH, InteractionPolicy.STANDARD_LINE_OF_SIGHT) {
    val def = targetNpc.definition
    if (def.combatLevel > 0 && def.actions.contains("Attack")) {
        // start combat
    }
}