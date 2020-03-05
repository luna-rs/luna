package world.player.advanceLevel

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.inter.DialogueInterface

/**
 * A [DialogueInterface] that opens when a level is advanced.
 */
class LevelUpInterface(val skill: Int,
                       val newLevel: Int,
                       val data: LevelUpData) : DialogueInterface(data.inter) {

    override fun onOpen(player: Player) {
        val skillName = Skill.getName(skill)
        val lvlUpMessage = "Congratulations, you just advanced ${addArticle(skillName)} level!"

        player.sendMessage(lvlUpMessage)
        player.sendText(lvlUpMessage, data.firstLine)
        player.sendText("Your $skillName level is now $newLevel.", data.secondLine)
    }
}