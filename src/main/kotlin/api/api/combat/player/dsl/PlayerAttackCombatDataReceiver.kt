package api.combat.player.dsl

import api.combat.player.VoidCombatAttack
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.attack.CombatAttack

/**
 * Supplies attack-specific combat context and helper methods for player attack DSL callbacks.
 *
 * @param player The player performing the attack.
 * @param other The current combat target.
 * @author lare96
 */
class PlayerAttackCombatDataReceiver(player: Player, other: Mob) : PlayerCombatDataReceiver(player, other) {

    /**
     * Resolves the default combat attack the player would normally use against [other].
     *
     * @return The default [CombatAttack] for the player against the current target.
     */
    fun default(): CombatAttack<Player> = player.combat.getDefaultAttack(other)

    /**
     * Creates a no-op combat attack for the current player and target.
     *
     * This can be returned by combat hooks when the attack should be explicitly cancelled instead of continuing with
     * default combat handling.
     *
     * @return A [VoidCombatAttack] that performs no attack action and resets the current target.
     */
    fun nothing() = VoidCombatAttack(player, other)
}