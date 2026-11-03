package api.bot.webwalk.walk.crossing

import io.luna.game.model.mob.bot.Bot

/**
 * Gives a native travel interaction sole ownership of its validated Continue packets.
 * The normal reflex resumes on success, failure and coroutine cancellation.
 *
 * @author lare96
 */
internal object NativeDialogueControl {

    /**
     * Restores the caller's previous reflex policy after the scoped interaction completes.
     */
    suspend fun <T> own(bot: Bot, action: suspend () -> T): T {
        val reflex = bot.reflex
        val previous = reflex.isDisableDialogueReflex
        reflex.isDisableDialogueReflex = true
        return try {
            action()
        } finally {
            reflex.isDisableDialogueReflex = previous
        }
    }
}
