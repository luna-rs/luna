package api.combat.player.dsl

import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.state.PlayerCombatContext

/**
 * Base receiver used by the player combat DSL.
 *
 * Receivers expose the player being processed, the opposing mob involved in the combat event, and the player's combat
 * context. DSL filters and listeners can extend this class to access shared combat data without repeatedly passing the
 * same values through every callback.
 *
 * @property player The player whose combat event is being processed.
 * @property other The opposing mob involved in the combat event.
 * @author lare96
 */
open class PlayerCombatDataReceiver(val player: Player, val other: Mob) {

    /**
     * The player's combat context.
     *
     * This is cached from [player] for convenient access inside combat DSL listeners and filters.
     */
    val combat: PlayerCombatContext = player.combat
}