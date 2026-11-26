package game.content.ectofuntus

import api.predef.ext.*
import game.player.Sound
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Fills one bucket at a time at a slime pool until the chosen quantity is reached or the action stops.
 *
 * @author lare96
 */
internal class FillSlimeAction(
    player: Player,
    private val pool: GameObject,
    private val selectedSlot: Int,
    private var remaining: Int
) : Action<Player>(player, ActionType.WEAK, false, Ectofuntus.FILL_TICKS) {
    private val origin = player.position

    override fun onSubmit() = mob.walking.clear()

    override fun run(): Boolean {
        if (mob.isLocked || mob.position != origin || !Ectofuntus.reachable(mob, pool)) return true
        val inventory = mob.inventory
        val slot = if (inventory.computeIdForIndex(selectedSlot) == Ectofuntus.BUCKET) selectedSlot
                   else inventory.computeIndexForId(Ectofuntus.BUCKET)
        if (slot == -1) return true

        inventory[slot] = Item(Ectofuntus.SLIME)
        mob.playSound(Sound.FILL_ECTOPLASM)
        mob.sendMessage("You fill the bucket with slime.")
        return --remaining == 0
    }
}
