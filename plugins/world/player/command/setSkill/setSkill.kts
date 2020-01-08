package world.player.command.setSkill

import api.predef.*
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.mob.inter.AbstractInterfaceSet
import world.player.command.cmd

/**
 * A mapping of buttons to skill identifiers.
 */
val buttonToSkill = mapOf(
        2812 to SKILL_ATTACK,
        2816 to SKILL_DEFENCE,
        2813 to SKILL_STRENGTH,
        2817 to SKILL_HITPOINTS,
        2814 to SKILL_RANGED,
        2818 to SKILL_PRAYER,
        2815 to SKILL_MAGIC,
        2827 to SKILL_COOKING,
        2829 to SKILL_WOODCUTTING,
        2830 to SKILL_FLETCHING,
        2826 to SKILL_FISHING,
        2828 to SKILL_FIREMAKING,
        2822 to SKILL_CRAFTING,
        2825 to SKILL_SMITHING,
        2824 to SKILL_MINING,
        2820 to SKILL_HERBLORE,
        2819 to SKILL_AGILITY,
        2821 to SKILL_THIEVING,
        12034 to SKILL_SLAYER,
        13914 to SKILL_FARMING,
        2823 to SKILL_RUNECRAFTING
)

/**
 * Opens the input interface for choosing what level to set to, or closes the interface.
 */
fun buttonClick(id: Int, interfaces: AbstractInterfaceSet) {
    when (id) {
        2831 -> interfaces.close()
        else -> {
            val skill = buttonToSkill[id]
            if (skill != null) {
                interfaces.open(SetLevelInput(skill))
            }
        }
    }
}

/**
 * A command that sets skill levels.
 */
cmd("set_skill", RIGHTS_DEV) { plr.interfaces.open(SetLevelInterface()) }

/**
 * Listens for button clicks on the [SetLevelInterface].
 */
on(ButtonClickEvent::class)
    .filter { plr.rights >= RIGHTS_DEV && plr.interfaces.isOpen(SetLevelInterface::class) }
    .then { buttonClick(id, plr.interfaces) }
