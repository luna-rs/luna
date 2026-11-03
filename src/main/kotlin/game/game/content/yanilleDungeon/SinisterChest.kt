package game.content.yanilleDungeon

import api.predef.*
import api.predef.ext.*
import engine.combat.status.hooks.PoisonStatusEffect
import game.obj.entrances.Entrances
import game.player.Messages
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject

/**
 * The sinister chest in the Yanille Agility Dungeon's skeleton room. A sinister key, from Salarin the twisted, opens
 * it once for a fixed set of nine unidentified herbs, and its gas poisons anyone without antipoison.
 *
 * @author TheLining
 */
object SinisterChest {

    /**
     * The chest, closed and open.
     */
    const val CLOSED = 377
    const val OPEN = 378

    /**
     * The sinister key.
     */
    const val KEY = 993

    /**
     * HUMAN_OPENCHEST.
     */
    const val OPEN_ANIMATION = 536

    /**
     * Ticks from unlocking the chest to the herbs, and how long it then stays open.
     */
    const val OPEN_TICKS = 4

    /**
     * The poison's severity: 6 damage at first.
     */
    const val POISON = 30

    /**
     * The nine unidentified herbs: two harralander, three ranarr, and an irit, avantoe, kwuarm and torstol.
     */
    val HERBS = listOf(205, 205, 207, 207, 207, 209, 211, 213, 219)

    /**
     * Opens [chest] if [plr] carries a key and has room for the herbs; the key's own slot holds one of them.
     */
    fun open(plr: Player, chest: GameObject) {
        when {
            plr.isLocked -> Unit
            KEY !in plr.inventory -> plr.sendMessage("The chest is locked.")
            plr.inventory.computeRemainingSize() < HERBS.size - 1 -> plr.sendMessage(Messages.inventoryFull())
            else -> plr.submitAction(OpenSinisterChestAction(plr, chest))
        }
    }
}

/**
 * Unlocks the sinister chest with a key, then a few ticks later opens it: the key is used up, the gas comes out and
 * the herbs go into the pack.
 */
class OpenSinisterChestAction(plr: Player, private val chest: GameObject) : LockedAction(plr) {

    private var ticks = 0

    override fun onLock() {
        mob.sendMessage("you unlock the chest with your key...")
        mob.animation(Animation(SinisterChest.OPEN_ANIMATION))
    }

    override fun run(): Boolean {
        if (++ticks < SinisterChest.OPEN_TICKS) return false
        if (!mob.inventory.remove(Item(SinisterChest.KEY))) return true
        Entrances.replace(chest, SinisterChest.OPEN, SinisterChest.OPEN_TICKS)
        mob.sendMessage("A foul gas seeps from the chest")
        mob.status.add(PoisonStatusEffect(mob, SinisterChest.POISON))
        SinisterChest.HERBS.forEach { mob.inventory.add(Item(it)) }
        mob.sendMessage("You find a lot of herbs in the chest...")
        return true
    }
}
