package game.skill.magic.teleportSpells

/**
 * The tick-by-tick steps of a teleport, from the cast to the landing. Spells use a [TeleportStyle], while items with
 * their own animations supply their own sequence.
 *
 * @author TheLining
 */
fun interface TeleportSequence {

    /**
     * Runs the step for the current [TeleportAction.executions]. The step that moves the player must call
     * [TeleportAction.land].
     *
     * @param action The teleport action being processed.
     * @return `true` if the teleport continues on the next tick, `false` once it has finished.
     */
    fun step(action: TeleportAction): Boolean
}
