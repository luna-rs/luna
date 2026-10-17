package engine.combat.status.hooks

import engine.combat.status.BasicStatusEffect
import engine.combat.status.StatusEffectType
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.SkullIcon
import kotlin.time.Duration

/**
 * Skull status effect.
 *
 * This shows the white skull above the player for the supplied [duration]. A skulled player keeps no items on death.
 * The effect is persistent, so the remaining duration is saved and restored across logout. A new skull only replaces
 * this one if it lasts longer.
 *
 * @param player The skulled player.
 * @param duration How long the skull lasts.
 * @author TheLining
 */
class SkullStatusEffect(player: Player, duration: Duration) :
    BasicStatusEffect<Player>(player,
                              type = StatusEffectType.SKULLED,
                              duration,
                              persistent = true,
                              refreshable = true) {

    /**
     * Creates a placeholder skull effect for loading saved status data.
     *
     * The real remaining duration is expected to be restored through [load].
     *
     * @param player The player this effect will be restored for.
     */
    constructor(player: Player) : this(player, Duration.ZERO)

    override fun onStart(restored: Boolean) {
        mob.skullIcon = SkullIcon.WHITE
    }

    override fun complete() {
        mob.skullIcon = SkullIcon.NONE
    }
}
