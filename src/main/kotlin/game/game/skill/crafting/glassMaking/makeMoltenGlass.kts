package game.skill.crafting.glassMaking

import api.predef.*
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.streams.toList

/**
 * Retrieve all objects named "Furnace" with the "Smelt" option.
 */
val furnaces = GameObjectDefinition.ALL.stream().filter { it.name.equals("Furnace") }
    .filter { it.actions.contains("Smelt") }.map { it.id }.toList()

/**
 * Opens the molten glass interface.
 */
fun openMoltenGlassInterface(plr: Player) {
    plr.overlays.open(object : MakeItemDialogue(1775) {
        override fun make(player: Player, id: Int, index: Int, forAmount: Int) {
            plr.submitAction(MakeMoltenGlassActionItem(player, forAmount))
        }
    })
}

// Use bucket of sand or soda ash on furnace.
furnaces.forEach {
    useItem(1783).onObject(it) { openMoltenGlassInterface(plr) }
    useItem(1781).onObject(it) { openMoltenGlassInterface(plr) }
}
