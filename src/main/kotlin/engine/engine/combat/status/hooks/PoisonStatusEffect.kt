package engine.combat.status.hooks

import api.combat.npc.NpcCombatHandler
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.combat.status.StatusEffect
import engine.combat.status.StatusEffectType
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Hit.HitType
import kotlin.math.floor
import kotlin.math.max

/**
 * Periodic poison damage status effect.
 *
 * Poison runs every `30` ticks. Players take damage using the current severity before it decreases; NPCs decrease
 * severity before calculating damage. This preserves the player's final one-damage tick and the separate NPC cadence.
 * The effect ends when poison severity reaches zero or when the mob has active antipoison.
 * NPC combat registrations can prevent lethal poison for creatures that require a finishing item, even before
 * an ordinary hit prepares them for execution. Capped zero-damage ticks do not emit a poison hitsplat.
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

    /**
     * Raises this effect's remaining severity without restarting its damage countdown.
     *
     * Equal or weaker poison is ignored so repeated hits cannot weaken poison or postpone its next damage tick.
     *
     * @param incoming The new poison effect whose severity is being considered.
     * @return Whether the existing severity was increased.
     */
    fun strengthenFrom(incoming: PoisonStatusEffect): Boolean {
        if (incoming.severity <= severity) {
            return false
        }
        severity = incoming.severity
        return true
    }

    override fun start(restored: Boolean) {
        if (!restored && mob is Player) {
            mob.sendMessage("You have been poisoned!")
        }
    }

    override fun run(): Boolean {
        if (severity <= 0 || mob.status.isAntiPoison()) {
            return true
        }
        val damageSeverity = if (mob is Player) severity-- else --severity
        if (damageSeverity > 0) {
            val damage = max(1, floor((damageSeverity + 4.0) / 5.0).toInt())
            val appliedDamage = if (mob is Npc) NpcCombatHandler.capPoisonDamage(mob, damage) else damage
            if (appliedDamage > 0) {
                mob.damage(appliedDamage, HitType.POISON)
            }
        }
        return severity <= 0
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
