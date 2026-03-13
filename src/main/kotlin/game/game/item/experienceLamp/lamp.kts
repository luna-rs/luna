package game.item.experienceLamp

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.mob.varp.*

/**
 * Rubbing the lamp.
 */
item1(2528) {
    plr.overlays.open(ExperienceLampInterface())
}

/**
 * Handling the buttons on the interface.
 */
for (skill in ExperienceLampInterface.InterfaceSkill.values()) {
    button(skill.button) {
        if (ExperienceLampInterface::class in plr.overlays) {
            plr.varpManager.setAndSendValue(PersistentVarp.XP_LAMP, skill.varpValue)
        }
    }
}

/**
 * Confirm button.
 */
button(2831) {
    if (ExperienceLampInterface::class in plr.overlays) {
        plr.overlays.closeWindows()
        plr.inventory.remove(2528)
        val skillId: Int = ExperienceLampInterface.InterfaceSkill.getSkillByVarp(plr.varpManager.getValue(PersistentVarp.XP_LAMP))
        val skill = plr.skills.getSkill(skillId)
        val experienceGain = skill.staticLevel * 10.toDouble()
        skill.addExperience(experienceGain)
        plr.sendMessage("You gained ${experienceGain.toInt()} experience in the ${skill.name} skill.")
    }
}