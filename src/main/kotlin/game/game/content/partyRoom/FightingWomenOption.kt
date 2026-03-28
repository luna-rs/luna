package game.content.partyRoom

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.task.Task
import io.luna.util.RandomUtils

/**
 * Handles the lever option for the "Fighting Women" event.
 *
 * @author lare96
 */
object FightingWomenOption : PartyRoomOption(50_000, "Fighting Women") {

    /**
     * A task scheduled when someone wins.
     */
    class WinningWomanTask(private val winner: Npc) : Task(3) {
        override fun execute() {
            // Gloat after winning.
            when (executionCounter) {
                0 -> winner.face(Direction.NORTH)
                1 -> {
                    winner.speak(VICTORY_MESSAGES.random())
                    winner.animation(Animation(862))
                }

                2, 3 -> winner.animation(Animation(862))
                else -> {
                    world.removeNpc(winner)
                    cancel()
                }
            }
        }
    }

    /**
     * The woman ID.
     */
    val WOMAN_ID = 15

    /**
     * The first position of the woman.
     */
    val POSITION_1 = Position(2737, 3468)

    /**
     * The second position of the woman.
     */
    val POSITION_2 = Position(2738, 3468)

    /**
     * The messages spoken before fighting.
     */
    val BEFORE_MESSAGES = listOf(
        "Prepare to lose!",
        "You suck!",
        "ARRGGHHHHHH!",
        "AHHHHHHHH!",
        "GRAHHHHHHH!",
        "HAHAHAHA good luck!",
        "You're about to lose this!"
    )

    /**
     * The messages spoken during fighting.
     */
    val DURING_MESSAGES = listOf(
        "ARRGGHHHHHH!",
        "AHHHHHHHH!",
        "GRAHHHHHHH!"
    )

    /**
     * The messages spoken after fighting.
     */
    val VICTORY_MESSAGES = listOf(
        "Told you!",
        "I killed her!",
        "Hah! I won!",
        "She was no match for me!",
        "Aww.. I was just getting started..."
    )

    /**
     * The taunting animations.
     */
    private val TAUNT_ANIMATIONS = listOf(864, 859, 856).map { Animation(it) }

    override fun execute(plr: Player) {
        val women = listOf(
            world.addNpc(Npc(ctx, WOMAN_ID, POSITION_1)),
            world.addNpc(Npc(ctx, WOMAN_ID, POSITION_2))
        )
        val woman1 = women[0]
        val woman2 = women[1]
        woman1.combat.lastCombatWith = woman2
        woman2.combat.lastCombatWith = woman1
        woman1.interact(woman2)
        woman2.interact(woman1)
        world.schedule(4) { task ->
            if (task.executionCounter == 0) {
                women.forEach {
                    it.attack.level = RandomUtils.inclusive(50, 99)
                    it.strength.level = RandomUtils.inclusive(50, 99)
                    it.defence.level = RandomUtils.inclusive(1, 50)
                    it.hitpoints.level = RandomUtils.inclusive(125, 175)
                    it.maxHit = RandomUtils.inclusive(10, 20)
                    it.animation(TAUNT_ANIMATIONS.random())
                    it.speak(BEFORE_MESSAGES.random())
                }
            } else if (task.executionCounter == 1) {
                women.forEach {
                    it.animation(TAUNT_ANIMATIONS.random())
                    it.speak(BEFORE_MESSAGES.random())
                }
            } else if (task.executionCounter == 2) {
                women.forEach {
                    it.animation(TAUNT_ANIMATIONS.random())
                    it.speak(BEFORE_MESSAGES.random())
                }
            } else if (task.executionCounter == 3) {
                woman1.combat.attack(woman2)
                woman2.combat.attack(woman1)
            } else if (!woman1.isAlive || !woman2.isAlive) {
                women.filter { it.isAlive }.forEach { world.schedule(WinningWomanTask(it)) }
                PartyRoom.resetLeverOption()
                task.cancel()
            } else if (RandomUtils.inclusive(3) == 0) {
                for (npc in women) {
                    if (RandomUtils.nextBoolean()) {
                        npc.speak(DURING_MESSAGES.random())
                    }
                }
            }
        }
    }
}