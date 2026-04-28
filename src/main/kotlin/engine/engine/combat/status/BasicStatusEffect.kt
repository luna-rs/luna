package engine.combat.status

import com.google.gson.JsonObject
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import kotlin.time.Duration

/**
 * A basic [StatusEffect] implementation acting as a simple duration timer.
 *
 * This class handles the common status-effect flow:
 * - Run optional startup logic.
 * - Send an optional start message.
 * - Count down for a fixed duration.
 * - Run optional completion logic.
 * - Send an optional end message.
 * - Optionally save and load remaining duration for player persistence.
 *
 * Subclasses can override [onStart] and [onComplete] to add effect-specific behaviour without replacing the base
 * persistence and message flow.
 *
 * @param T The mob type this status effect applies to.
 * @param mob The mob affected by this status effect.
 * @param type The unique status effect type.
 * @param duration The duration of one effect cycle.
 * @param startMsg Optional message sent to players when the effect starts.
 * @param endMsg Optional message sent to players when the effect completes.
 * @param persistent Whether this effect should save remaining duration for players.
 * @param refreshable Whether a new effect of this type can replace an existing one.
 * @author lare96
 */
open class BasicStatusEffect<T : Mob>(mob: T,
                                      type: StatusEffectType,
                                      duration: Duration,
                                      protected var startMsg: String? = null,
                                      protected var endMsg: String? = null,
                                      protected val persistent: Boolean = false,
                                      refreshable: Boolean = false) : StatusEffect<T>(mob, duration, type, refreshable) {

    final override fun start(restored: Boolean) {
        onStart(restored)

        if (startMsg != null && !restored && mob is Player) {
            mob.sendMessage(startMsg)
        }
    }

    final override fun run(): Boolean {
        if (endMsg != null && mob is Player) {
            mob.sendMessage(endMsg)
        }
        return true
    }

    final override fun load(obj: JsonObject?) {
        if (persistent && obj != null && mob is Player && obj.has("remaining")) {
            countdown = obj.get("remaining").asInt
            onLoad(obj)
        }
    }

    final override fun save(): JsonObject? {
        if (persistent && mob is Player) {
            val obj = JsonObject()
            obj.addProperty("remaining", countdown)
            onSave(obj)
            return obj
        }

        return null
    }

    /**
     * Runs when this effect starts.
     *
     * @param restored Whether this effect was restored from saved player data.
     */
    open fun onStart(restored: Boolean) {

    }

    /**
     * Loads this object's persisted state from the given JSON object.
     *
     * Subclasses may override this to restore any custom data that was previously written by [onSave].
     *
     * @param obj The JSON object containing the saved state.
     */
    open fun onLoad(obj: JsonObject) {

    }

    /**
     * Saves this object's persistent state into the given JSON object.
     *
     * Subclasses may override this to write any custom data that should be restored later by [onLoad].
     *
     * @param obj The JSON object to write saved state into.
     */
    open fun onSave(obj: JsonObject) {

    }
}