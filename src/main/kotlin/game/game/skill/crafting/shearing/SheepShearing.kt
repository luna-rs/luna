package game.skill.crafting.shearing

import api.predef.ext.scheduleOnce
import api.predef.rand
import game.player.Sound
import game.skill.crafting.textileCrafting.Textile
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Direction
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.interact.InteractionPolicy

/**
 * Shears ordinary sheep with the corresponding revision 377 shorn forms.
 *
 * The escape chance and fleece regrowth follow Lost City Content's shear_sheep.rs2.
 *
 * @author lare96
 */
object SheepShearing {
    const val SHEARS = 1735
    private const val REGROWTH_TICKS = 100
    private val WOOL = Textile.BALL_OF_WOOL.rawItem
    private val shornForms = mapOf(43 to 42, 1763 to 1762, 1765 to 1764, 3310 to 3311)
    internal val npcIds = shornForms.keys + shornForms.values

    /**
     * Checks whether a live sheep has wool available.
     */
    internal fun canShear(sheep: Npc): Boolean = sheep.isAlive && sheep.id in shornForms

    /**
     * Starts a shear attempt when the player uses carried shears within reach of a sheep.
     */
    internal fun shear(player: Player, sheep: Npc) {
        if (!canReach(player, sheep) || SHEARS !in player.inventory ||
            player.actions.first(ShearAction::class.java) != null) return
        val woolly = sheep.id
        val shorn = shornForms[woolly] ?: run {
            if (woolly in shornForms.values) player.sendMessage("This sheep has already been shorn.")
            return
        }
        if (!player.inventory.hasSpaceFor(WOOL)) {
            player.sendMessage("You don't have room for any wool.")
            return
        }

        player.animation(Animation(893))
        sheep.navigator.cancel()
        sheep.walking.clear()
        if (rand(0.25)) {
            sheep.navigator.step(Direction.between(player.position, sheep.position), false)
            player.sendMessage("The sheep gets away from you.")
            return
        }
        sheep.face(player.position)
        player.playSound(Sound.SHEAR_SHEEP)
        player.submitAction(ShearAction(player, sheep, woolly, shorn))
    }

    private fun canReach(player: Player, sheep: Npc): Boolean =
        player.isAlive && sheep.isAlive && player.world === sheep.world &&
            player.world.collisionManager.reached(player, sheep, InteractionPolicy.STANDARD_SIZE)

    /**
     * Rechecks the original sheep, reach and inventory before awarding exactly one fleece.
     *
     * @author lare96
     */
    internal class ShearAction(
        player: Player,
        private val sheep: Npc,
        private val woolly: Int,
        private val shorn: Int
    ) : Action<Player>(player, ActionType.WEAK, false, 2) {
        private val life = sheep.spawnCount

        override fun run(): Boolean {
            if (sheep.spawnCount != life || !canReach(mob, sheep) || SHEARS !in mob.inventory) return true
            if (sheep.id != woolly) {
                if (sheep.id == shorn) mob.sendMessage("This sheep has already been shorn.")
                return true
            }
            if (!mob.inventory.add(WOOL)) {
                mob.sendMessage("You don't have room for any wool.")
                return true
            }

            sheep.transform(shorn)
            world.scheduleOnce(REGROWTH_TICKS) {
                if (sheep.isAlive && sheep.spawnCount == life && sheep.id == shorn) sheep.transform(woolly)
            }
            val message = "You shear some wool from the sheep."
            mob.sendMessage(message)
            if (!mob.isBot) mob.newDialogue().give(WOOL, message, false).open()
            return true
        }
    }
}
