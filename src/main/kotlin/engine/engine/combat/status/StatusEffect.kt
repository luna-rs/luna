package engine.combat.status

import api.predef.ext.*
import com.google.gson.JsonObject
import io.luna.game.model.mob.Mob
import kotlin.time.Duration

/**
 * Base class for a timed combat status effect.
 *
 * A status effect owns one countdown timer and one [StatusEffectType]. [StatusAction] ticks the effect every game tick
 * through [isReady]. When [isReady] returns `true`, [run] is called to apply the periodic or completion behaviour.
 *
 * For one-shot effects, [run] should return `true` so the effect is removed. For repeating effects, [run] can return
 * `false` so the countdown restarts and the effect continues.
 *
 * @param T The mob type affected by this effect.
 * @property mob The mob affected by this effect.
 * @param duration The duration between each [run] execution.
 * @property type The unique type of this status effect.
 * @property refreshable Whether an existing effect of this type can be replaced by a new one.
 * @author lare96
 */
abstract class StatusEffect<T : Mob>(val mob: T,
                                     private val duration: Duration,
                                     val type: StatusEffectType,
                                     val refreshable: Boolean) {

    /**
     * Remaining ticks before this effect is ready to run.
     */
    var countdown = duration.toTicks()
        protected set

    /**
     * Starts this status effect.
     *
     * @param restored Whether this effect was restored from saved player data instead of newly applied.
     */
    abstract fun start(restored: Boolean)

    /**
     * Runs the effect once its countdown expires.
     *
     * @return `true` to remove the effect, or `false` to keep it active and restart the countdown.
     */
    abstract fun run(): Boolean

    /**
     * Processes one game tick and checks whether this effect is ready to run.
     *
     * [process] runs before countdown is decremented. When the countdown reaches zero, it is reset to the original
     * duration and this method returns `true`.
     *
     * @return `true` if [run] should be called this tick, otherwise `false`.
     */
    fun isReady(): Boolean {
        process()

        if (--countdown > 0) {
            return false
        }

        countdown = duration.toTicks()
        return true
    }

    /**
     * Runs when this status effect is replaced is finishes normally.
     */
    open fun complete() {

    }

    /**
     * Runs every game tick before countdown is decremented.
     */
    open fun process() {

    }

    /**
     * Loads status-specific data.
     *
     * @param obj Saved status data, or `null` if no saved data exists.
     */
    open fun load(obj: JsonObject?) {

    }

    /**
     * Saves status-specific data.
     *
     * @return Serialized status data, or `null` if this effect should not be persisted.
     */
    open fun save(): JsonObject? {
        return null
    }
}