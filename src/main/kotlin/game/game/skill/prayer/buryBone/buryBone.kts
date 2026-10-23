package game.skill.prayer.buryBone

import api.predef.*
import game.skill.prayer.Bone

/**
 * Registers the existing first inventory option for every supported bone type.
 * The player action owns cooldown validation, consumption, effects, and Prayer experience.
 *
 * @author lare96
 */
Bone.ID_TO_BONE.forEach { (id, bone) ->
    item1(id) { plr.submitAction(BuryBoneAction(plr, bone)) }
}
