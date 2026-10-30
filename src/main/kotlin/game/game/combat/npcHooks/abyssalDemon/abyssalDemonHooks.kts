package game.combat.npcHooks.abyssalDemon

import api.combat.npc.NpcCombatHandler.combat

/**
 * Registers native Abyssal demon hit reactions. Their ordinary melee attack remains profile-driven, while teleport
 * reactions exclude them from terrain-only safespot proofs.
 *
 * @author lare96
 */
combat(1615) {
    disruptsPursuit = true
    afterDamage { damage -> AbyssalDemons.afterHit(this, damage) }
}
