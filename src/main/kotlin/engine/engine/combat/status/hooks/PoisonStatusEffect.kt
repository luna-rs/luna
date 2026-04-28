package engine.combat.status.hooks

import api.predef.ext.*
import com.google.gson.JsonObject
import engine.combat.status.StatusEffect
import engine.combat.status.StatusEffectType
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Hit.HitType
import kotlin.math.floor
import kotlin.math.max

/**
 * Periodic poison damage status effect.
 *
 * Poison runs every `30` ticks. Each cycle decreases the mob's combat poison severity and applies poison damage based on
 * the remaining severity. The effect ends when poison severity reaches zero or when the mob has active antipoison.
 *
 * @param mob The mob affected by poison.
 * @param severity The initial poison severity.
 * @author lare96
 */
class PoisonStatusEffect(mob: Mob, private var severity: Int) :
    StatusEffect<Mob>(mob,
                      duration = 30.ticks,
                      type = StatusEffectType.POISONED,
                      refreshable = true) {

    /**
     * Creates a placeholder poison effect for loading saved status data.
     *
     * @param mob The mob this effect will be restored for.
     */
    constructor(mob: Mob) : this(mob, 0)

    override fun start(restored: Boolean) {
        if (!restored && mob is Player) {
            mob.sendMessage("You have been poisoned!")
        }
    }

    override fun run(): Boolean {
        if (--severity > 0 && !mob.status.isAntiPoison()) {
            val damage = floor((severity + 4.0) / 5.0).toInt()
            mob.damage(max(1, damage), HitType.POISON)
            return false
        }

        return true
    }

    override fun load(obj: JsonObject?) {
        if (obj != null && mob is Player) {
            severity = obj.get("severity").asInt
        }
    }

    override fun save(): JsonObject? {
        if (severity > 0 && mob is Player) {
            val obj = JsonObject()
            obj.addProperty("severity", severity)
            return obj
        }

        return null
    }
}