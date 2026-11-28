package game.content.druidicRitual

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.ext.schedule
import io.luna.game.model.EntityState
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.NpcAggressionProfile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatRestriction
import io.luna.game.model.`object`.GameObject

/**
 * One of the suits beside the Cauldron of Thunder stirs when its door is opened during the ritual.
 * Passage is handled independently by the ordinary door action.
 *
 * @author lare96
 */
class CauldronArmour private constructor(val owner: Player, spawn: Position) :
    Npc(owner.context, SUIT_OF_ARMOUR, spawn), CombatRestriction {

    companion object {
        const val SUIT_OF_ARMOUR = 453
        private const val RADIUS = 10
        private const val LIFETIME_TICKS = 150
        private val doors = mapOf(2143 to Position(2889, 9831), 2144 to Position(2889, 9830))
        private var Player.disturbedCauldronArmour by Attr.boolean()

        internal fun rattle(player: Player, door: GameObject) {
            if (door.position != doors[door.id] || player.x >= door.x || player.disturbedCauldronArmour ||
                !inPassage(player)) return

            // The statues flank the doorway at (2887, 9832) and (2887, 9829).
            val spawn = Position(2888, if (door.id == 2143) 9832 else 9829)
            val armour = CauldronArmour(player, spawn)
            if (!player.world.npcs.add(armour)) return
            player.disturbedCauldronArmour = true
            player.sendMessage("A suit of armour springs to life!")
            armour.combat.attack(player)
            var age = 0
            player.world.schedule(1) { task ->
                if (!inPassage(player) || ++age >= LIFETIME_TICKS) {
                    if (armour.state == EntityState.ACTIVE && armour.isAlive) player.world.npcs.remove(armour)
                    player.disturbedCauldronArmour = false
                    task.cancel()
                }
            }
        }

        private fun inPassage(player: Player): Boolean =
            player.state == EntityState.ACTIVE && player.isAlive &&
                DruidicRitual.stage(player) == DruidicRitual.SPOKE_TO_SANFEW &&
                player.z == 0 && player.position.isWithinDistance(DruidicRitual.cauldronPosition, RADIUS)
    }

    init {
        respawnTicks = -1
    }

    override fun getAggressionProfile(): NpcAggressionProfile? = null

    override fun isViewableFrom(other: Locatable): Boolean = other === owner && super.isViewableFrom(other)

    override fun canFight(opponent: Mob): Boolean =
        opponent === owner && state == EntityState.ACTIVE && inPassage(owner)
}
