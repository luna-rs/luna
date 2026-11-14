/*
 * Adapted from LostCityRS/Content scripts/areas/area_entrana,
 * revision 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
 */
package game.content.lostCity

import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject

/**
 * The cave monk's one-way dungeon warning and base-level Prayer drain from the pinned area_entrana scripts.
 * Admission remains independent of Lost City progress, as in the source.
 *
 * @author lare96
 */
object EntranaCaveMonk {
    const val NPC = 656
    const val LADDER = 2408
    const val DECLINE = "I don't think I'm strong enough to enter then."
    const val AGREE = "Well that is a risk I will have to take."
    val LADDER_POSITION = Position(2820, 3374)
    val LANDING = Position(2822, 9774)

    /**
     * Starts the cave monk conversation when the player uses the native one-way ladder.
     */
    fun ladder(player: Player, ladder: GameObject) {
        warning(player) { p -> ladder.id == LADDER && ladder.position == LADDER_POSITION &&
            ladder.state == EntityState.ACTIVE && ladder.isVisibleTo(p) &&
            p.world.collisionManager.reached(p, ladder, InteractionPolicy.STANDARD_SIZE) }
    }

    /**
     * The native cave monk offers the same warning and admission when spoken to directly.
     */
    fun talk(player: Player, monk: Npc) {
        warning(player) { p -> monk.id == NPC && monk.state == EntityState.ACTIVE &&
            monk.z == p.z && monk.position.isWithinDistance(p.position, 3) &&
            p.z == LADDER_POSITION.z && p.position.isWithinDistance(LADDER_POSITION, 8) }
    }

    private fun warning(player: Player, admission: (Player) -> Boolean) {
        if (!ready(player) || !admission(player)) return
        val origin = player.position
        player.newDialogue().npc(NPC, "Be careful going in there! You are unarmed, and",
            "there is much evil lurking below. It seems to block",
            "our contact with our gods, weakening our prayers.")
            .npc(NPC, "You won't be able to come back this way.",
                "This ladder only goes one way!")
            .npc(NPC, "The only exit is a portal which leads",
                "to the deepest Wilderness!")
            .options(DECLINE, { it.resetDialogues() }, AGREE, { enter(it, origin, admission) }).open()
    }

    /**
     * Rechecks a continued conversation before travelling or draining Prayer. Moving away invalidates the offer.
     */
    private fun enter(player: Player, origin: Position, admission: (Player) -> Boolean) {
        if (!ready(player) || player.position != origin || !admission(player)) return
        player.submitAction(object : LockedAction(player, false, 1) {
            override fun beforeLock(): Boolean = ready(mob) && mob.position == origin && admission(mob)

            override fun onLock() {
                mob.walking.clear()
                mob.sendMessage("You climb down the ladder.")
                mob.move(LANDING)
                val prayer = mob.skill(Skill.PRAYER)
                prayer.adjustLevel(-(prayer.staticLevel * 95 / 100))
            }

            override fun run(): Boolean = true
        })
    }

    private fun ready(player: Player): Boolean = player.state == EntityState.ACTIVE && player.isAlive &&
        !player.isLocked && player.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }
}
