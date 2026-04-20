package api.combat.player

import api.combat.player.PlayerCombatHandler.playerAttack
import api.combat.player.PlayerCombatHandler.playerCombat
import api.combat.player.PlayerCombatHandler.playerDefence
import api.combat.player.PlayerCombatHandler.playerStopAttack
import api.combat.player.dsl.PlayerAttackCombatDataReceiver
import api.combat.player.dsl.PlayerAttackCombatFilter
import api.combat.player.dsl.PlayerDefenceCombatDataReceiver
import api.combat.player.dsl.PlayerDefenceCombatFilter
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamageAction

/**
 * Registers and executes player combat hooks.
 *
 * This handler exposes small DSL-style registration points for player attack selection, incoming defence processing,
 * per-tick combat callbacks, and conditional attack interruption.
 *
 * - [playerAttack] may override the normal combat attack chosen for a player.
 * - [playerDefence] may modify the pending damage action and replacement defence animation.
 * - [playerCombat] runs continuously while the player remains in combat.
 * - [playerStopAttack] may abort the current attack loop entirely.
 *
 * @author lare96
 */
object PlayerCombatHandler {

    /**
     * Registered attack-selection hooks for players.
     *
     * These hooks are consulted in registration order by [supplyAttack]. The first matching hook that returns a
     * non-null [CombatAttack] overrides normal attack selection.
     */
    val attackListeners = ArrayList<PlayerAttackCombatFilter>()

    /**
     * Registered defence hooks for players.
     *
     * These hooks are consulted in registration order by [consumeDefence]. The first matching hook handles the
     * defence event and stops further defence hook processing.
     */
    val defenceListeners = ArrayList<PlayerDefenceCombatFilter>()

    /**
     * Registered combat callbacks that run while a player is actively in combat.
     *
     * These are invoked by [consumeCombat], typically once per combat cycle.
     */
    val combatListeners = ArrayList<(Player).() -> Unit>()

    /**
     * Registered stop-condition hooks for player attacks.
     *
     * These hooks are checked by [testStopAttack]. Returning `true` from any hook immediately halts the current
     * attack loop.
     */
    val stopListeners = ArrayList<Player.(CombatAttack<*>) -> Boolean>()

    /**
     * Registers a player attack-selection hook.
     *
     * The supplied filter receives a [PlayerAttackCombatDataReceiver] containing the attacking player and victim.
     * When the returned [PlayerAttackCombatFilter] matches during [supplyAttack], it may provide a custom
     * [CombatAttack] to use instead of the default combat attack selection.
     *
     * @param filter The predicate used to determine whether this attack hook should apply.
     * @return The registered attack combat filter, allowing its attack behavior to be configured.
     */
    fun playerAttack(filter: PlayerAttackCombatDataReceiver.() -> Boolean): PlayerAttackCombatFilter {
        return PlayerAttackCombatFilter(attackListeners, filter)
    }

    /**
     * Registers a stop-condition hook for player attacks.
     *
     * The hook is evaluated during [testStopAttack]. Returning `true` signals that the current attack
     * should be stopped, which causes the attack loop to exit and the player's current target to be cleared.
     *
     * @param filter The stop-condition callback to register.
     */
    fun playerStopAttack(filter: Player.(CombatAttack<*>) -> Boolean) {
        stopListeners.add(filter)
    }

    /**
     * Registers a callback that runs repeatedly while a player is in combat.
     *
     * This is intended for lightweight per-combat-cycle logic only. The callback may run frequently, so expensive
     * work should be avoided here.
     *
     * @param action The combat callback to register.
     */
    fun playerCombat(action: (Player).() -> Unit) {
        combatListeners += action
    }

    /**
     * Registers a player defence hook.
     *
     * The supplied filter receives a [PlayerDefenceCombatDataReceiver] containing the defending player, the
     * opposing mob, and the pending [CombatDamageAction]. When matched during [consumeDefence], the hook may
     * modify the resulting damage and defence animation.
     *
     * @param filter The predicate used to determine whether this defence hook should apply.
     * @return The registered defence combat filter, allowing its defence behavior to be configured.
     */
    fun playerDefence(filter: PlayerDefenceCombatDataReceiver.() -> Boolean): PlayerDefenceCombatFilter {
        return PlayerDefenceCombatFilter(defenceListeners, filter)
    }

    /**
     * Attempts to supply a custom combat attack for a player.
     *
     * Attack hooks are evaluated in registration order. For each hook, a new [PlayerAttackCombatDataReceiver] is
     * created for the supplied attacker and victim.
     *
     * If a hook's filter matches and its attack supplier returns a non-null [CombatAttack], that attack is returned
     * immediately and normal attack selection is skipped.
     * 
     * If a hook matches but returns {@code null}, processing continues to the next hook. If no hook supplies a
     * custom attack, this method returns {@code null} so the caller can fall back to normal attack selection.
     *
     * @param attacker The attacking player.
     * @param victim The target mob being attacked.
     * @return The custom combat attack to use, or {@code null} if normal attack selection should continue.
     */
    fun supplyAttack(attacker: Player, victim: Mob): CombatAttack<Player>? {
        for (hook in attackListeners) {
            val receiver = PlayerAttackCombatDataReceiver(attacker, victim)
            if (hook.filter(receiver)) {
                // We matched on this hook, instead of null, do nothing.
                val attack = hook.attack(receiver)
                if (attack != null) {
                    return attack
                }
            }
        }
        // No hooks matched, return to normal attack selection.
        return null
    }

    /**
     * Applies the first matching defence hook to an incoming combat damage action.
     *
     * Defence hooks are evaluated in registration order. When a hook matches, it is allowed to mutate the receiver's 
     * damage and animation values. Those values are then copied back into the supplied [CombatDamageAction], the 
     * player's defence animation is played, and no further hooks are processed.
     *
     * @param player The defending player.
     * @param other The opposing mob responsible for the combat interaction.
     * @param action The incoming combat damage action to modify.
     */
    fun consumeDefence(player: Player, other: Mob, action: CombatDamageAction) {
        for (hook in defenceListeners) {
            val receiver = PlayerDefenceCombatDataReceiver(player, other, action)
            if (hook.filter(receiver)) {
                hook.defence(receiver)
                action.damage = receiver.damage
                player.animation(Animation(receiver.animationId))
                return
            }
        }
    }

    /**
     * Executes all registered per-combat callbacks for a player.
     *
     * Callbacks are run in registration order.
     *
     * @param player The player currently in combat.
     */
    fun consumeCombat(player: Player) {
        for (hook in combatListeners) {
            hook(player)
        }
    }

    /**
     * Tests whether the current player attack should be stopped.
     *
     * Stop hooks are evaluated in registration order. The first hook that returns `true` causes this method
     * to return `true` immediately.
     *
     * @param player The player whose attack is being checked.
     * @param attack The current combat attack being processed.
     * @return `true` if attack processing should stop, otherwise `false`.
     */
    fun testStopAttack(player: Player, attack: CombatAttack<*>): Boolean {
        for (hook in stopListeners) {
            if (hook(player, attack)) {
                return true
            }
        }
        return false
    }
}