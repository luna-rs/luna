/*
 * Adapted from LostCityRS/Content, commit 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
 */
package game.skill.woodcutting.jungle

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.skill.Skills
import game.skill.woodcutting.Woodcutting
import game.skill.woodcutting.cutTree.Axe
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.area.Area
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject

/**
 * Kharazi's tree and bush passages, including the source tool checks and evaporating quest bowls.
 * Success, depletion and product chances follow Lost City's reconstruction, whose script explicitly
 * marks the rates as estimates. The ordinary Ape Atoll jungle trees use normal woodcutting instead.
 *
 * @author lare96
 */
object KharaziJungle {

    val STUMPS = mapOf(2887 to 2891, 2889 to 2891, 2890 to 2891, 2892 to 2894, 2893 to 2894)
    val MACHETES = setOf(975, 6313, 6315, 6317)
    private val bowls = mapOf(723 to 721, 724 to 721, 725 to 722, 726 to 722)
    private val denseAreas = listOf(
        Area.of(2759, 2934, 2765, 2941), Area.of(2789, 2935, 2802, 2941),
        Area.of(2816, 2935, 2823, 2938), Area.of(2858, 2934, 2873, 2939),
        Area.of(2930, 2937, 2941, 2941))

    /**
     * Starts one weak chopping action after rechecking the interaction's live target and tools.
     */
    fun chop(player: Player, jungle: GameObject) {
        if (!ready(player) || jungle.id !in STUMPS || !usable(player, jungle)) return
        // TODO Once Legends' Quest exists, require carried Radimus notes or quest completion, as for its entrances.
        if (Axe.computeAxeType(player) == null) {
            player.sendMessage("You'll need an axe you have the Woodcutting level to use to get through this rough jungle.")
            offerEscape(player)
            return
        }
        if (!hasMachete(player)) {
            player.sendMessage("You need a machete to cut your way through this dense jungle bush.")
            offerEscape(player)
            return
        }
        evaporate(player)
        player.submitAction(ChopAction(player, jungle))
    }

    /**
     * Recognises every revision 377 machete in the inventory or weapon slot.
     */
    fun hasMachete(player: Player) = player.equipment[Equipment.WEAPON]?.id in MACHETES ||
        MACHETES.any { player.inventory.contains(it) }

    /**
     * Replaces each filled golden bowl in place, including when every inventory slot is occupied.
     */
    fun evaporate(player: Player) {
        var count = 0
        for ((full, empty) in bowls) count += player.inventory.replaceAll(full, empty)
        if (count > 0) {
            player.sendMessage("The heat in this jungle is terrific.")
            player.sendMessage("The water from your golden bowl evaporates.")
        }
    }

    /**
     * Gives the source two-tile movement, without moving a player already standing on the target.
     */
    fun destination(from: Position, jungle: Position): Position? =
        if (from == jungle || from.z != jungle.z) null else from.translate(2, Direction.between(from, jungle))

    /**
     * Source escape destinations for a player who has discarded a necessary tool within the dense jungle.
     */
    fun escapeDestination(position: Position): Position? {
        if (position.z != 0 || denseAreas.none { position in it }) return null
        return when {
            position in denseAreas[4] -> Position(2937, 2943)
            position in denseAreas[3] -> Position(2866, 2941)
            position in denseAreas[1] -> Position(2795, 2942)
            else -> Position(2820, 2941)
        }
    }

    private fun offerEscape(player: Player) {
        val from = player.position
        val destination = escapeDestination(from) ?: return
        player.newDialogue()
            .text("It looks like you're lost in the jungle!", "Would you like to try and scrabble out?")
            .options("Yes, I'll scrabble out of this jungle.", {
                if (ready(it) && it.position == from) {
                    it.move(destination)
                    it.damage(1)
                    it.sendMessage("You scrabble out of the jungle.")
                    it.sendMessage("You're scratched to pieces by the dense jungle trees,")
                    it.sendMessage("but at least you're alive and out of the jungle.")
                }
            }, "No, I think I can find my own way out.", { it.sendMessage("You decide to stay where you are.") })
            .open()
    }

    private fun ready(player: Player) = player.state == EntityState.ACTIVE && player.isAlive && !player.isLocked &&
        player.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

    private fun usable(player: Player, jungle: GameObject) = jungle.state == EntityState.ACTIVE &&
        jungle.isVisibleTo(player) && player.world.collisionManager.reached(player, jungle, InteractionPolicy.STANDARD_SIZE)

    /**
     * Retains one click's position and target identity while rolling the source's separate product and depletion.
     * Full inventories suppress only the product and its XP; they do not prevent a passage being cleared.
     *
     * @author lare96
     */
    internal class ChopAction(player: Player, private val jungle: GameObject,
                              private val succeeds: (Int) -> Boolean = { Skills.success(70, 233, minOf(it, 99)) },
                              private val product: () -> Boolean = { rand().nextInt(2) == 0 },
                              private val depletes: () -> Boolean = { rand().nextInt(4) < 3 }) :
        Action<Player>(player, ActionType.WEAK, false, 1) {

        private val from = player.position
        private val bush = jungle.id == 2892 || jungle.id == 2893
        private var ticks = 0
        private var nextAttempt = if (bush) 3 else 5

        override fun run(): Boolean {
            if (!ready(mob) || mob.position != from || !usable(mob, jungle)) return true
            val axe = Axe.computeAxeType(mob) ?: return true
            if (!hasMachete(mob)) return true
            if (++ticks % (if (bush) 2 else 4) == 1) {
                mob.animation(if (bush) Animation(910) else axe.animation)
                mob.sendMessage(if (bush) "You swing your machete at the jungle plant." else "You swing your axe at the tree.")
            }
            if (bush) mob.playSound(Sound.TREE_FALLEN)
            if (ticks < nextAttempt) return false
            nextAttempt += 4
            if (bush) mob.playSound(Sound.TREE_FALLEN)
            if (!succeeds(mob.woodcutting.level)) return false
            if (!mob.inventory.isFull && product() && mob.inventory.add(Item(1511))) {
                mob.sendMessage("You get some logs.")
                mob.woodcutting.addExperience(100.0)
            }
            if (!depletes()) return false
            val stumpId = STUMPS[jungle.id] ?: return true
            if (!world.removeObject(jungle)) return true
            val stump = GameObject.createDynamic(mob.context, stumpId, jungle.position,
                jungle.objectType, jungle.direction, jungle.view)
            if (!world.addObject(stump)) {
                world.addObject(GameObject.createDynamic(mob.context, jungle.id, jungle.position,
                    jungle.objectType, jungle.direction, jungle.view))
                return true
            }
            world.scheduleOnce(Woodcutting.scaleRespawn(4, world.players.size())) {
                if (world.removeObject(stump)) {
                    world.addObject(GameObject.createDynamic(jungle.context, jungle.id, jungle.position,
                        jungle.objectType, jungle.direction, jungle.view))
                }
            }
            val destination = destination(from, jungle.position)
            val atJungle = destination != null && world.objects.findAll(destination).anyMatch { it.id in STUMPS }
            if (destination == null || (world.collisionManager.isBlocked(destination, false) && !atJungle)) {
                mob.sendMessage("This way is blocked off, no chance to get through here!")
            } else {
                mob.sendMessage(if (bush) "You hack your way through the jungle bush." else "You hack your way through the tree.")
                mob.sendMessage("You move deeper into the jungle.")
                mob.move(destination)
            }
            return true
        }

        override fun onFinished() {
            mob.animation(Animation.CANCEL)
        }
    }
}
