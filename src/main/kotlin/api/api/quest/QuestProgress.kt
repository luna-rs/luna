package api.quest

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.PersistentVarp

/**
 * Stores a quest's stage in its existing player varp and awards quest points on completion.
 * Call mutations on the game thread so progress checks and rewards run in the same game action.
 *
 * @author lare96
 */
class QuestProgress(private val stageVarp: PersistentVarp,
                    private val completionStage: Int,
                    val questPoints: Int) {

    init {
        require(stageVarp != PersistentVarp.QUEST_POINTS)
        require(completionStage > 0)
        require(questPoints >= 0)
    }

    /**
     * Reads the saved stage, with zero representing a quest that has not started.
     */
    fun stage(player: Player): Int = player.varpManager.getValue(stageVarp).coerceIn(0, completionStage)

    /**
     * Records forward progress and returns whether the stage changed. The final stage is awarded by [complete].
     */
    fun advance(player: Player, nextStage: Int): Boolean {
        if (nextStage !in 1 until completionStage || nextStage <= stage(player)) return false
        player.varpManager.setAndSendValue(stageVarp, nextStage)
        return true
    }

    /**
     * Records completion and adds quest points once, from the specified prior stage.
     * The caller can present completion and grant quest-specific rewards when this returns true.
     */
    fun complete(player: Player, expectedStage: Int): Boolean {
        if (expectedStage !in 0 until completionStage || stage(player) != expectedStage) return false
        val varps = player.varpManager
        val total = Math.addExact(varps.getValue(PersistentVarp.QUEST_POINTS), questPoints)
        varps.setValue(stageVarp, completionStage)
        varps.setValue(PersistentVarp.QUEST_POINTS, total)
        varps.sendValue(stageVarp)
        varps.sendValue(PersistentVarp.QUEST_POINTS)
        return true
    }
}
