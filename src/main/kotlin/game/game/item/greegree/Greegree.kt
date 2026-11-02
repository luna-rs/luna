package game.item.greegree

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.area.Area
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.PlayerModelAnimation
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag

/**
 * The monkey greegrees and the monkeys they turn their holder into.
 *
 * @param id The greegree's item id.
 * @param npc The monkey the holder becomes.
 * @param run The monkey's running animation, or -1 to run with its walk.
 * @author TheLining
 */
enum class Greegree(val id: Int, val npc: Int, private val run: Int) {
    SMALL_NINJA_MONKEY(4024, 1480, 1381),
    MEDIUM_NINJA_MONKEY(4025, 1481, 1381),
    GORILLA(4026, 1482, 1400),
    BEARDED_GORILLA(4027, 1483, 1400),
    ANCIENT_MONKEY(4028, 1484, 1400),
    SMALL_ZOMBIE_MONKEY(4029, 1485, -1),
    LARGE_ZOMBIE_MONKEY(4030, 1486, -1),
    KARAMJAN_MONKEY(4031, 1487, 1419);

    /**
     * The monkey's standing and walking animations. Its turns are left unset (-1), so the client turns it with its
     * walk, as it does the monkey NPC.
     */
    val model: PlayerModelAnimation by lazy {
        val def = NpcDefinition.ALL.retrieve(npc)
        PlayerModelAnimation.Builder()
            .setStandingId(def.standAnimationId)
            .setStandingTurnId(-1)
            .setWalkingId(def.walkAnimationId)
            .setTurning180DegreesId(-1)
            .setTurning90DegreesCwId(-1)
            .setTurning90DegreesCcwId(-1)
            .setRunningId(run)
            .build()
    }

    companion object {

        /**
         * The greegrees by item id.
         */
        val ITEMS = values().associateBy { it.id }

        /**
         * Where a greegree works: Ape Atoll and the dungeons under it, and the monkey cage in Ardougne Zoo. Zooknock
         * can't sustain its magic any further away.
         */
        private val AREAS = listOf(
            Area.of(2688, 2688, 2815, 2815, -1),
            Area.of(2688, 9088, 2815, 9215, -1),
            Area.of(2593, 3270, 2610, 3285, -1))

        /**
         * Whether a greegree works at [position].
         */
        fun worksAt(position: Position) = AREAS.any { it.contains(position) }

        /**
         * The greegree [plr] holds, or `null`.
         */
        fun held(plr: Player): Greegree? = plr.equipment.weapon?.let { ITEMS[it.id] }
    }

    /**
     * Turns [plr] into this greegree's monkey.
     */
    fun transform(plr: Player) {
        plr.transform(npc)
        plr.model = model
        plr.flags.flag(UpdateFlag.APPEARANCE)
    }
}
