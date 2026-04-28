package engine.combat.status

import api.predef.*
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
 * @param warnMsg The warning message sent shortly before expiration.
 * @param endMsg The message sent when the effect expires.
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

    constructor(player: Player, type: StatusEffectType) : this(player, type, Duration.ZERO, Potion.ZAMORAK_BREW)

    override fun process() {
        if (countdown == 50) {
            mob.sendMessage("Your ${potion.formattedName} is going to expire soon.")
        }
    }

    override fun onLoad(obj: JsonObject) {
        potion = Potion.valueOf(obj.get("potion").asString)
        endMsg = "Your ${potion.formattedName} has expired."
    }

    override fun onSave(obj: JsonObject) {
        obj.addProperty("potion", potion.name)
    }
}