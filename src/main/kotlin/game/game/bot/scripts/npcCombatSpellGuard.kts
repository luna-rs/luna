package game.bot.scripts

import api.combat.player.PlayerCombatHandler.playerStopAttack
import io.luna.game.model.mob.bot.Bot

// A manual cast clears its selected spell before the script's next cycle. Retaliation must wait for re-selection.
playerStopAttack { attack ->
    val bot = this as? Bot
    val script = bot?.scriptStack?.findActive(NpcCombatScript::class.java)?.orElse(null)
    script != null && !script.acceptsCombatAttack(attack)
}
