package engine.combat.status

import api.predef.ext.*
import com.google.gson.JsonObject
import game.player.item.consume.potion.Potion
import io.luna.game.model.mob.Player
import kotlin.time.Duration

/**
 * Base class for timed potion-based player status effects.
 *
 * Potion effects are persistent and refreshable by default. They save remaining duration through [BasicStatusEffect]
 * and can be replaced by a newer effect when the new effect has an equal or longer remaining countdown.
 *
 * @param player The player affected by the potion status.
 * @param type The status effect type.
 * @param duration The potion effect duration.
 * @param potion The potion whose name is used in warning and expiration messages.
 * @author lare96
 */
open class PotionStatusEffect(player: Player,
                              type: StatusEffectType,
                              duration: Duration,
                              private var potion: Potion) :
    BasicStatusEffect<Player>(player,
                              type,
                              duration,
                              startMsg = null,
                              endMsg = "Your ${potion.formattedName} has expired.",
                              persistent = true,
                              refreshable = true) {

    /**
     * Remaining ticks at which to warn, rounded down to at most 25% of the original duration.
     * Persisted separately so logging in does not recalculate the threshold from the remaining time.
     */
    private var warningTicks = duration.toTicks() / 4

    /**
     * Creates a placeholder effect for loading saved potion data.
     *
     * @param player The player whose effect is being restored.
     * @param type The potion status effect type.
     */
    constructor(player: Player, type: StatusEffectType) : this(player, type, Duration.ZERO, Potion.ZAMORAK_BREW)

    override fun process() {
        // StatusEffect processes this hook before decrementing the countdown.
        if (warningTicks > 0 && countdown - 1 == warningTicks) {
            mob.sendMessage("Your ${potion.formattedName} is going to expire soon.")
        }
    }

    override fun onLoad(obj: JsonObject) {
        potion = Potion.valueOf(obj.get("potion").asString)
        // Older saves did not record the original duration; retain their 50-tick warning.
        warningTicks = if (obj.has("warning_ticks")) obj.get("warning_ticks").asInt else 50
        endMsg = "Your ${potion.formattedName} has expired."
    }

    override fun onSave(obj: JsonObject) {
        obj.addProperty("potion", potion.name)
        obj.addProperty("warning_ticks", warningTicks)
    }
}
