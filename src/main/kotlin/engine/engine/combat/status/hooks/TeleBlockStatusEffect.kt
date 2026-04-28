package engine.combat.status.hooks

import engine.combat.status.BasicStatusEffect
import engine.combat.status.StatusEffectType
import io.luna.game.model.mob.Player
import kotlin.time.Duration

/**
 * Teleblock status effect.
 *
 * This marks a player as unable to teleport for the supplied [duration]. The effect is persistent, so remaining
 * duration can be saved and restored across logout.
 *
 * @param player The player affected by teleblock.
 * @param duration The amount of time teleblock should last.
 * @author lare96
 */
class TeleBlockStatusEffect(player: Player, duration: Duration) :
    BasicStatusEffect<Player>(player,
                              type = StatusEffectType.TELEBLOCK,
                              duration,
                              startMsg = "You have been Tele Blocked!",
                              endMsg = "Your Tele Block has expired.",
                              persistent = true) {

    /**
     * Creates a placeholder teleblock effect for loading saved status data.
     *
     * The real remaining duration is expected to be restored through [load].
     *
     * @param player The player this effect will be restored for.
     */
    constructor(player: Player) : this(player, Duration.ZERO)
}