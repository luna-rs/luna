package game.content.pestControl

import api.predef.ext.*
import game.content.pestControl.PestControl.commendations
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.Varp

/**
 * The cache's original combat-training interface. Each confirmed transaction consumes one point.
 *
 * @author lare96
 */
class PestControlTraining : StandardInterface(18691) {
    companion object {
        val SKILLS = listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED,
            Skill.MAGIC, Skill.HITPOINTS, Skill.PRAYER)

        fun experience(level: Int, skill: Int): Int {
            if (level !in 25..99 || skill !in SKILLS) return 0
            return level * level / if (skill == Skill.PRAYER) 12 else 6
        }

        fun show(player: Player) {
            if (!PestControl.atOutpost(player.position)) return
            PestControl.claimCoins(player)
            player.overlays.open(PestControlTraining())
            player.sendVarp(Varp(261, 0))
            player.sendText("Points: ${player.commendations}", 18783)
            player.sendText("Choose a combat skill", 18782)
        }
    }

    private var selected: Int? = null

    fun select(player: Player, index: Int) {
        val skill = SKILLS.getOrNull(index) ?: return
        selected = skill
        player.sendVarp(Varp(261, 1))
        player.sendText("Train ${player.skill(skill).name}: ${experience(player.skill(skill).staticLevel, skill)} xp", 18782)
    }

    fun confirm(player: Player): Boolean {
        val skill = selected ?: return false
        if (player.overlays.getOverlay(PestControlTraining::class.java) !== this ||
            !PestControl.atOutpost(player.position) || player.commendations <= 0) return false
        val amount = experience(player.skill(skill).staticLevel, skill)
        if (amount <= 0) {
            player.sendMessage("You need at least level 25 in that skill to receive training.")
            return false
        }
        selected = null
        player.sendVarp(Varp(261, 0))
        player.commendations--
        player.skill(skill).addExperience(amount.toDouble())
        player.sendText("Points: ${player.commendations}", 18783)
        player.sendText("Choose a combat skill", 18782)
        return true
    }
}
