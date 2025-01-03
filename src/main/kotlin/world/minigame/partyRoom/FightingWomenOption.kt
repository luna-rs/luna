package world.minigame.partyRoom

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Hit
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.task.Task

/**
 * Handles the lever option for the "Fighting Women" event.
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
                    winner.forceChat(VICTORY_MESSAGES.random())
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
    val WOMAN_ID = 1258

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
    val TAUNT_ANIMATIONS = listOf(864, 859, 856).map { Animation(it) }

    /**
     * The attack animations.
     */
    val ATTACK_ANIMATIONS = listOf(423, 422).map { Animation(it) }


    override fun execute(plr: Player) {
        // TODO Replace with real fight to the death once combat done
        // TODO Let them cast spells and have randomized weapons
        val women = listOf(
                world.addNpc(Npc(ctx, WOMAN_ID, POSITION_1)),
                world.addNpc(Npc(ctx, WOMAN_ID, POSITION_2))
        )
        women.forEach { it.setInteractable(false) }
        val woman1 = women[0]
        val woman2 = women[1]
        woman1.interact(woman2)
        woman2.interact(woman1)
        woman1.hitpoints.level = 100
        woman2.hitpoints.level = 100
        var otherWoman: Npc
        var lastWoman = if (rand().nextBoolean()) {
            otherWoman = woman2
            woman1
        } else {
            otherWoman = woman1
            woman2
        }
        val hitSupplier = { if (rand().nextInt(3) == 0) Hit(0, Hit.HitType.BLOCKED) else Hit(rand(1, 15), Hit.HitType.NORMAL) }
        world.schedule(4) { task ->
            if (task.executionCounter == 0) {
                women.forEach {
                    it.animation(TAUNT_ANIMATIONS.random())
                    it.forceChat(BEFORE_MESSAGES.random())
                }
            } else if (!woman1.isAlive || !woman2.isAlive) {
                women.filter { it.isAlive }.forEach { world.schedule(WinningWomanTask(it)) }
                PartyRoom.resetLeverOption()
                task.cancel()
            } else {
                lastWoman.animation(ATTACK_ANIMATIONS.random())
                otherWoman.animation(Animation(404))
                otherWoman.damage(hitSupplier())
                otherWoman.interact(lastWoman)
                lastWoman.interact(otherWoman)
                if (rand(3) == 0) {
                    if (rand().nextBoolean()) {
                        lastWoman.forceChat(DURING_MESSAGES.random())
                    } else {
                        otherWoman.forceChat(DURING_MESSAGES.random())
                    }
                }

                val newLastWoman = otherWoman
                val newOtherWoman = lastWoman
                lastWoman = newLastWoman
                otherWoman = newOtherWoman
            }
        }
    }
}