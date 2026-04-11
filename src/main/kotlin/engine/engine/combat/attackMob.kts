package engine.combat

import api.predef.*
import io.luna.game.event.*
import io.luna.game.event.impl.NpcClickEvent.AttackNpcEvent
import io.luna.game.event.impl.PlayerClickEvent.PlayerFirstClickEvent
import io.luna.game.event.impl.UseSpellEvent.MagicOnNpcEvent
import io.luna.game.event.impl.UseSpellEvent.MagicOnPlayerEvent
import io.luna.game.model.*
import io.luna.game.model.def.*
import io.luna.game.model.mob.*
import io.luna.game.model.mob.interact.*

/**
 * Resolves the interaction policy to use when a player initiates combat against a target.
 *
 * The target must be a [Mob]. For valid combat targets, this method determines the player's next attack, stores it as
 * the player's first queued attack, and returns that attack's interaction policy.
 *
 * @param plr The player initiating combat.
 * @param target The entity being targeted for combat.
 * @return The interaction policy required to engage the target with the resolved first attack.
 * @throws IllegalStateException If `target` is not a [Mob].
 */
fun getInteraction(plr: Player, target: Entity): InteractionPolicy {
    if (target is Mob) {
        val nextAttack = plr.combat.getNextAttack(target)
        plr.combat.firstAttack = nextAttack
        return nextAttack.interactionPolicy
    } else {
        throw IllegalStateException("Combat target must always be a Mob.")
    }
}

// "Attack" context menu option on players.
on(PlayerFirstClickEvent::class, EventPriority.HIGH, interaction = { plr, target -> getInteraction(plr, target) }) {
    if (plr.combat.isAttackable && targetPlr.combat.isAttackable) {
        plr.combat.attack(targetPlr)
    }
}

// Use magic spell on player.
on(MagicOnPlayerEvent::class, EventPriority.HIGH, InteractionPolicy.STANDARD_LINE_OF_SIGHT) {
    if (plr.combat.isAttackable && targetPlr.combat.isAttackable) {
        plr.combat.magic.selectedSpell =
            CombatSpellDefinition.ALL[spellId].orElseThrow { IllegalArgumentException("Invalid spell ID $spellId") }
        plr.combat.firstAttack = plr.combat.getNextAttack(targetPlr)
        plr.combat.attack(targetPlr)
    }
}

// "Attack" context menu option on npcs.
on(AttackNpcEvent::class, EventPriority.HIGH, interaction = { plr, target -> getInteraction(plr, target) }) {
    if (targetNpc.combat.isAttackable) {
        plr.combat.attack(targetNpc)
    }
}

// Use magic spell on NPC.
on(MagicOnNpcEvent::class, EventPriority.HIGH, InteractionPolicy.STANDARD_LINE_OF_SIGHT) {
    if (targetNpc.combat.isAttackable) {
        plr.combat.magic.selectedSpell =
            CombatSpellDefinition.ALL[spellId].orElseThrow { IllegalArgumentException("Invalid spell ID $spellId") }
        plr.combat.firstAttack = plr.combat.getNextAttack(targetNpc)
        plr.combat.attack(targetNpc)
    }
}