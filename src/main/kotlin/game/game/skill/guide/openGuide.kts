package game.skill.guide

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ButtonClickEvent

/**
 * Opens a skill's guide when it is clicked on the stats tab.
 */
for ((button, guide) in SkillGuides.BUTTONS) {
    button(button) { plr.overlays.open(SkillGuideInterface(guide)) }
}

/**
 * Switches category when one of the guide's tabs is clicked. Tabs past the guide's last category are ignored.
 */
on(ButtonClickEvent::class)
    .filter { SkillGuideInterface::class in plr.overlays }
    .then {
        val index = SkillGuideInterface.TAB_BUTTONS.indexOf(id)
        val inter = plr.overlays[SkillGuideInterface::class]
        if (inter != null && index != -1 && index < inter.guide.categories.size) {
            inter.show(plr, index)
        }
    }
