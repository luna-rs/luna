package world.player.skill.magic

import api.predef.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

interface SpellRequirement {
    fun canCast(plr: Player): Boolean
}

class ItemRequirement(val id: Int, val amount: Int = 1) : SpellRequirement {
    override fun canCast(plr: Player): Boolean {
        val item = Item(id, amount)
        if (!plr.inventory.contains(item)) {
            plr.sendMessage("You do not have enough ${item.itemDef.name}s to cast this spell.")
            return false
        }
        return true
    }
}

class RuneRequirement(val rune: Rune, val amount: Int) : SpellRequirement {
    override fun canCast(plr: Player): Boolean {
        // todo combination runes, staves
        val item = Item(rune.id, amount)
        if (!plr.inventory.contains(item)) {
            plr.sendMessage("You do not have enough ${item.itemDef.name}s to cast this spell.")
            return false
        }
        return true
    }
}