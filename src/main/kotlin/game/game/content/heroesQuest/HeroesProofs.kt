package game.content.heroesQuest

import api.predef.ext.addItem
import game.content.heroesQuest.HeroesQuest as Quest
import game.content.questRewards.QuestRewards.owns
import game.skill.fishing.catchFish.Tool
import game.skill.herblore.makePotion.FinishedPotion
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * Gerrant's hint and the firebird's quest feather. The fishing rod recipe and ordinary eel fishing use their
 * normal skill handlers.
 *
 * @author lare96
 */
object HeroesProofs {
    const val ASK_ABOUT_EELS = "How can I catch a lava eel?"
    private val oil = FinishedPotion.BLAMISH_OIL

    fun askAboutEels(player: Player, gerrant: Npc) {
        player.newDialogue().npc(gerrant.id, "Lava will ruin an ordinary rod. Rub it with blamish oil to protect it.",
            "Mix this slime with harralander and water to make the oil.")
            .then {
                if (Quest.isInProgress(it) && Quest.nearby(it, gerrant) &&
                    !owns(it, oil.secondary) && !owns(it, oil.id) && !owns(it, Tool.LAVA_EEL_FISHING_ROD.id)) {
                    val message = if (it.inventory.add(oil.secondary)) "Gerrant gives you a jar of blamish snail slime."
                                  else "Make room for the jar first."
                    it.sendMessage(message)
                }
            }.open()
    }

    fun dropFeather(player: Player, bird: Npc) {
        if (player.isEventBot || !Quest.isInProgress(player) || owns(player, Quest.FIRE_FEATHER)) return
        player.world.addItem(GroundItem(player.context, Quest.FIRE_FEATHER, 1,
            bird.position, ChunkUpdatableView.localView(player)))
    }

    fun canTakeFeather(player: Player): Boolean {
        if (!Quest.isInProgress(player)) {
            player.sendMessage("You have no reason to take the feather.")
            return false
        }
        if (player.equipment[Equipment.HANDS]?.id == Quest.ICE_GLOVES) return true
        player.sendMessage("The feather is too hot to touch without ice gloves.")
        player.damage(player.health / 8 + 1)
        return false
    }
}
