package game.content.abyss

import api.predef.*
import api.predef.ext.*
import game.content.abyss.Abyss.ENTRIES
import game.player.Animations
import game.player.Sound
import game.skill.magic.Magic.findLandingTile
import game.skill.magic.Magic.teleport
import game.skill.magic.teleportSpells.TeleportAction
import game.skill.magic.teleportSpells.TeleportSequence
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic

/**
 * The Mage of Zamorak's teleport into the Abyss. He casts it on the player like Tele Other.
 *
 * @param mage The Mage of Zamorak casting the teleport.
 * @author TheLining
 */
class AbyssTeleport(private val mage: Npc) : TeleportSequence {

    companion object {

        /**
         * The words the Mage of Zamorak says as he casts the teleport.
         */
        const val INCANTATION = "Veniens! Sallakar! Rinnesset!"

        /**
         * Has the Mage of Zamorak at [mage] teleport [plr] into the Abyss.
         */
        fun cast(plr: Player, mage: Npc) {
            val layout = rand(ENTRIES.indices)
            plr.teleport(findLandingTile(ENTRIES[layout], 5), AbyssTeleport(mage),
                         onLand = { Abyss.arrive(plr, layout) })
        }
    }

    override fun step(action: TeleportAction): Boolean {
        val plr = action.mob
        return when (action.executions) {
            0 -> {
                mage.interact(plr)
                mage.speak(INCANTATION)
                mage.animation(Animations.CAST_TELEOTHER)
                mage.graphic(Graphic(343))
                plr.playSound(Sound.TELE_OTHER_CAST)
                true
            }

            2 -> {
                plr.animation(Animations.RECEIVE_TELEOTHER)
                plr.graphic(Graphic(342, 92))
                plr.playSound(Sound.TELEPORT_ALL)
                true
            }

            5 -> {
                action.land()
                plr.animation(Animation.CANCEL)
                mage.interact(null)
                true
            }

            6 -> false
            else -> true
        }
    }
}
