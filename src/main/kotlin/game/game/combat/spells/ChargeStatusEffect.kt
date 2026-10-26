package game.combat.spells

import api.predef.ext.ticks
import com.google.gson.JsonObject
import engine.combat.status.StatusEffect
import engine.combat.status.StatusEffectType
import game.player.Sound
import io.luna.game.model.mob.Player

/**
 * The god spell Charge timer: 350 reference timer executions two ticks apart, or 700 game ticks.
 * Remaining time survives logout and pauses during modal interfaces and action locks. Recasting refreshes the
 * timer without a spurious expiry message or sound; expiry is emitted only by the natural timer completion.
 *
 * @author lare96
 */
class ChargeStatusEffect(player: Player) : StatusEffect<Player>(player, 700.ticks, StatusEffectType.CHARGED, true) {

    override fun start(restored: Boolean) {
        if (!restored) mob.sendMessage("You feel charged with magic power.")
    }

    override fun process() {
        if (mob.overlays.hasWindow() || mob.isActionLocked) countdown++
    }

    override fun run(): Boolean {
        mob.sendMessage("Your magical charge fades away.")
        mob.playSound(Sound.CHARGE_GONE)
        return true
    }

    override fun save(): JsonObject = JsonObject().apply { addProperty("remaining", countdown) }

    override fun load(obj: JsonObject?) {
        if (obj != null && obj.has("remaining")) countdown = obj.get("remaining").asInt.coerceIn(1, 700)
    }
}
