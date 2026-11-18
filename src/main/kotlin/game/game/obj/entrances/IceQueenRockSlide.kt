package game.obj.entrances

import api.predef.mining
import game.player.Sound
import game.skill.agility.AgilityAction
import game.skill.agility.AgilityApproach
import game.skill.agility.AgilityStep
import game.skill.mining.Pickaxe
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectType

/**
 * The two lanes of the White Wolf Mountain rockslide. Both directions require current Mining and a usable pickaxe.
 * The Lost City interaction supplies the mining check and three-tile crossing. Each miner crosses under their own
 * movement lock, keeping the rockslide clipped for everyone else.
 *
 * @author lare96
 */
object IceQueenRockSlide {
    const val ID = 2634
    const val LEVEL = 50
    val POSITION = Position(2838, 3517)
    val POLICY = InteractionPolicy(InteractionType.DISTANCE, 2)
    val LANES = (3517..3518).map { y -> Position(2837, y) to Position(2840, y) }

    fun canTraverse(player: Player): Boolean = player.mining.level >= LEVEL && Pickaxe.computePickType(player) != null

    fun mine(player: Player, rock: GameObject) {
        if (!ready(player) || !validObject(player, rock) || player.z != POSITION.z) return
        val west = player.x < POSITION.x
        if (!west && player.x < POSITION.x + 2) return
        val (from, to) = LANES.map { (left, right) -> if (west) left to right else right to left }
            .minBy { (start, _) -> start.computeLongestDistance(player.position) }
        if (player.position.computeLongestDistance(from) > AgilityApproach.RADIUS) return
        if (player.position == from) cross(player, rock, from, to)
        else player.submitAction(AgilityApproach(player, from) { cross(player, rock, from, to) })
    }

    private fun cross(player: Player, rock: GameObject, from: Position, to: Position) {
        if (!ready(player) || player.position != from || !validObject(player, rock)) return
        val pickaxe = Pickaxe.computePickType(player)
        if (pickaxe == null) {
            Pickaxe.sendNoPickaxeMessage(player)
            return
        }
        if (player.mining.level < LEVEL) {
            player.sendMessage("You need a Mining level of $LEVEL to clear the rockslide.")
            return
        }
        player.submitAction(AgilityAction(player, listOf(
            AgilityStep(from, 2, animation = pickaxe.animation.id, jump = true,
                begin = { player.playSound(Sound.MINE_QUICK) }),
            AgilityStep(to, 2, animation = 819)
        ), null, valid = { player.isAlive && player.state == EntityState.ACTIVE && player.position == from &&
            validObject(player, rock) }))
    }

    private fun ready(player: Player): Boolean = player.isAlive && player.state == EntityState.ACTIVE &&
        !player.isLocked && !player.walking.isLocked && !player.status.isImmobilized()

    private fun validObject(player: Player, rock: GameObject): Boolean = rock.id == ID && rock.position == POSITION &&
        rock.objectType == ObjectType.DEFAULT && rock.state == EntityState.ACTIVE && !rock.isHidden &&
        rock.isVisibleTo(player) && player.world.objects.findAll(POSITION).anyMatch { it === rock }
}
