package world.player.item.consumable.potion

import com.google.gson.JsonObject
import io.luna.game.action.CountdownTimer
import io.luna.game.model.mob.Player
import world.player.item.consumable.potion.PotionEffect.Companion.potionEffects
import world.player.item.consume.potion.Potion

/**
 * A [CountdownTimer] implementation that manages timers for [Potion] effects.
 *
 * @author lare96
 */
class PotionCountdownTimer(val plr: Player, var potion: Potion, val effect: PotionEffect, start: Long)
    : CountdownTimer(plr, start) {

    companion object {

        /**
         * Loads a [PotionCountdownTimer] for [plr] represented by [json].
         */
        fun loadJson(plr: Player, json: JsonObject) {
            val potion = Potion.valueOf(json.get("potion").asString)
            val effect = PotionEffect.valueOf(json.get("effect").asString)
            val start = json.get("start").asLong
            val remaining = json.get("remaining").asLong

            val timer = PotionCountdownTimer(plr, potion, effect, start)
            timer.remaining = remaining
            plr.submitAction(timer)
        }
    }

    /**
     * Determines if the player has been notified of expiry.
     */
    private var notified = false

    override fun onSubmit() {
        val timerAction = plr.potionEffects.putIfAbsent(effect, this)
        if (timerAction != null) {
            // We already have an active potion timer with this effect.
            if (timerAction.remaining < start) {
                timerAction.remaining = start
            }
            if (timerAction.start < start) {
                // Only change potion if total duration is greater.
                timerAction.start = start
                timerAction.potion = potion
            }
            timerAction.notified = false
            interrupt()
        }
    }

    override fun onCountdown() {
        if (!notified) {
            val percent = (remaining.toDouble() / start.toDouble())
            if (percent <= 0.10) {
                plr.sendMessage("Your ${potion.formattedName} is about to expire.")
                notified = true
            }
        }
    }

    override fun onComplete() {
        plr.sendMessage("Your ${potion.formattedName} has expired.")
        plr.potionEffects.remove(effect)
    }

    /**
     * Converts this timer into a serializable [JsonObject].
     */
    fun saveJson(): JsonObject {
        val json = JsonObject()
        json.addProperty("potion", potion.name)
        json.addProperty("effect", effect.name)
        json.addProperty("start", start)
        json.addProperty("remaining", remaining)
        return json
    }
}