package world.player.skill.prayer

import api.attr.Attr
import api.predef.*
import io.luna.game.action.ThrottledAction
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player
import java.util.*

/**
 * Throttles bone burying.
 */
val Player.lastBury by Attr.timeSource()

/**
 * The bone burying animation.
 */
val buryAnimation = Animation(827)

/**
 * Buries [bone] for [plr] and awards XP.
 */
fun bury(plr: Player, bone: Bone) {
    plr.submitAction(object : ThrottledAction<Player>(plr, plr.lastBury, 3) {
        override fun execute() {
            mob.animation(buryAnimation)

            mob.prayer.addExperience(bone.exp)
            mob.inventory.remove(bone.boneItem)

            mob.sendMessage("You dig a hole in the ground.")
            mob.sendMessage("You bury the bones.")
        }
    })
}

// Initialize bone burying event listeners here.
ItemDefinition.ALL
    .stream()
    .filter(Objects::nonNull)
    .forEach {
        val bone = Bone.ID_TO_BONE[it.id]
        if (bone != null) {
            item1(it.id) {
                bury(plr, bone)
            }
        }
    }
