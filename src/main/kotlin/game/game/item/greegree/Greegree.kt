package game.item.greegree

import io.luna.game.model.Position
import io.luna.game.model.area.Area
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.PlayerModelAnimation
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag
import io.luna.game.model.mob.controller.PlayerController

/**
 * The monkey greegrees and the monkeys they turn their holder into.
 *
 * @param id The greegree's item id.
 * @param npc The monkey the holder becomes.
 * @param run The monkey's running animation, or -1 if it can't run.
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
     * Whether the monkey can run. The zombie monkeys only walk.
     */
    val canRun = run != -1

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
         * The greegree [plr] holds, or `null`. It reads the slot without the plugin predefs, since the engine calls it
         * through [disguises].
         */
        fun held(plr: Player): Greegree? = plr.equipment[Equipment.WEAPON]?.let { ITEMS[it.id] }

        /**
         * Whether [plr] looks like the monkey of the greegree they hold.
         */
        @JvmStatic
        fun disguises(plr: Player): Boolean {
            val greegree = held(plr) ?: return false
            return plr.transformId == greegree.npc
        }

        /**
         * Turns [plr] back into a human.
         */
        fun reset(plr: Player) {
            plr.resetTransform()
            if (plr.controllers.primary is GreegreeController) {
                plr.controllers.unregister()
            }
        }
    }

    /**
     * Turns [plr] into this greegree's monkey. The [GreegreeController] takes over only from the default controller, so
     * an activity's own controller stays in charge.
     */
    fun transform(plr: Player) {
        plr.transform(npc)
        plr.model = model
        plr.flags.flag(UpdateFlag.APPEARANCE)
        if (!canRun) {
            plr.isRunning = false
        }
        if (plr.controllers.primary.javaClass == PlayerController::class.java) {
            plr.controllers.register(GreegreeController(plr))
        }
    }
}
