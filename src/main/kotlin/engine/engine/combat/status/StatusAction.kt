package engine.combat.status

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import engine.combat.status.StatusAction.Companion.statusEffectData
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * Ticking [Action] that owns and processes all active status effects for a mob.
 *
 * This action runs every game tick and checks each active [StatusEffect]. When an effect becomes ready, its [StatusEffect.run]
 * method is called. Effects that return `true` from [StatusEffect.run] are removed.
 *
 * Player status effects can save persistent data into [statusEffectData] and later be restored through [load].
 *
 * @param T The mob type this action belongs to.
 * @param mob The mob whose status effects are being processed.
 * @author lare96
 */
class StatusAction(mob: Mob) : Action<Mob>(mob, ActionType.SOFT, true, 1) {

    /**
     * Shared status-effect persistence data.
     */
    companion object {

        /**
         * Persisted status effect data for a player.
         *
         * The array contains one object per saved status effect. Each object stores the effect type and effect-specific
         * data returned by [StatusEffect.save].
         */
        var Player.statusEffectData by Attr.obj { JsonArray(0) }.persist("status_effects")
    }

    /**
     * Active status effects keyed by status effect type.
     */
    private val effects = EnumMap<StatusEffectType, StatusEffect<out Mob>>(StatusEffectType::class.java)

    /**
     * Adds a status effect to this mob.
     *
     * If an effect of the same type already exists, [onlyIfAbsent], [StatusEffect.refreshable], and remaining duration
     * decide whether the new effect can replace it.
     *
     * @param listener The status effect to add.
     * @param onlyIfAbsent Whether the effect should only be added when no effect of the same type exists.
     * @return `true` if the effect was added, otherwise `false`.
     */
    fun add(listener: StatusEffect<out Mob>, onlyIfAbsent: Boolean = false): Boolean {
        val current = effects[listener.type]

        if (current != null) {
            if (onlyIfAbsent) {
                return false
            } else if (!current.refreshable) {
                return false
            } else if (current.countdown > listener.countdown) {
                return false
            }
            current.complete()
        }

        effects[listener.type] = listener
        listener.start(false)
        return true
    }

    /**
     * Checks whether a status effect type is currently active.
     *
     * @param type The status effect type to check.
     * @return `true` if [type] is active.
     */
    operator fun contains(type: StatusEffectType): Boolean {
        return effects.containsKey(type)
    }

    /**
     * Removes an active status effect from this mob.
     *
     * This removes the effect from the active effect map and calls [StatusEffect.complete].
     *
     * @param type The status effect type to remove.
     */
    fun remove(type: StatusEffectType) {
        effects.remove(type)?.complete()
    }

    /**
     * Checks whether this mob has an active status effect of [type] that is also a subclass of [classType].
     *
     * @param type The status effect type to check.
     * @param classType The status effect class that the active effect must inherit from.
     * @return `true` if an effect of [type] exists and is a subclass of [classType].
     */
    fun has(type: StatusEffectType, classType: Class<out StatusEffect<out Mob>>): Boolean {
        val effect = effects[type] ?: return false
        return effect::class.isSubclassOf(classType.kotlin)
    }

    /**
     * Checks whether this mob has an active status effect of [type] that is also a subclass of [classType].
     *
     * @param type The status effect type to check.
     * @param classType The status effect class that the active effect must inherit from.
     * @return `true` if an effect of [type] exists and is a subclass of [classType].
     */
    fun has(type: StatusEffectType, classType: KClass<out StatusEffect<out Mob>>): Boolean {
        val effect = effects[type] ?: return false
        return effect::class.isSubclassOf(classType)
    }

    /**
     * Loads saved player status effects and starts this action.
     *
     * Only players can currently restore saved status effects. Each saved entry resolves a [StatusEffectType], creates a
     * placeholder listener through its load function, loads effect-specific data, and starts the listener as restored.
     */
    fun load() {
        if (mob is Player) {
            val data = mob.statusEffectData
            for (element in data) {
                val effectData = element.asJsonObject
                if (!effectData.has("type") || !effectData.has("data")) {
                    continue
                }
                val listener = StatusEffectType.valueOf(effectData.get("type").asString).loadFunction(mob)
                if (listener != null) {
                    listener.load(effectData.get("data").asJsonObject)
                    effects[listener.type] = listener
                    listener.start(true)
                }
            }
        }
        mob.submitAction(this)
    }

    /**
     * Saves all persistent status effects.
     *
     * Effects decide whether they should be persisted by returning non-null data from [StatusEffect.save].
     */
    fun save() {
        if (mob is Player) {
            val data = JsonArray()
            for ((type, listener) in effects) {
                val listenerData = listener.save()
                if (listenerData != null) {
                    val saveData = JsonObject()
                    saveData.addProperty("type", type.name)
                    saveData.add("data", listenerData)
                    data.add(saveData)
                }
            }
            mob.statusEffectData = data
        }
    }

    /**
     * Processes all active status effects.
     *
     * @return Always `false`, because the status action should keep running while attached to the mob.
     */
    override fun run(): Boolean {
        effects.values.removeIf {
            if (it.isReady() && it.run()) {
                it.complete()
                true
            } else {
                false
            }
        }
        return false
    }

    /**
     * @return `true` if this mob is currently poisoned.
     */
    fun isPoisoned(): Boolean {
        return StatusEffectType.POISONED in this
    }

    /**
     * @return `true` if this mob currently has anti-poison protection.
     */
    fun isAntiPoison(): Boolean {
        return StatusEffectType.ANTIPOISON in this
    }

    /**
     * @return `true` if this mob currently has anti-fire protection.
     */
    fun isAntiFire(): Boolean {
        return StatusEffectType.ANTIFIRE in this
    }

    /**
     * @return `true` if this mob is currently stunned.
     */
    fun isStunned(): Boolean {
        return StatusEffectType.STUNNED in this
    }

    /**
     * @return `true` if this mob is currently immobilized.
     */
    fun isImmobilized(): Boolean {
        return StatusEffectType.IMMOBILIZED in this
    }

    /**
     * @return `true` if this mob is currently Tele-Blocked.
     */
    fun isTeleBlocked(): Boolean {
        return StatusEffectType.TELEBLOCK in this
    }
}