package world.player.advanceLevel

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.inter.DialogueInterface
import io.luna.net.msg.out.WidgetAnimationMessageWriter
import io.luna.net.msg.out.WidgetTextMessageWriter

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
        player.queue(WidgetTextMessageWriter(lvlUpMessage, data.firstLine))
        player.queue(WidgetTextMessageWriter("Your $skillName level is now $newLevel.", data.secondLine))
        if(skill == SKILL_FIREMAKING) {
            // Animates the flame in the chatbox.
            player.queue(WidgetAnimationMessageWriter(4286, 475))
        }
    }
}